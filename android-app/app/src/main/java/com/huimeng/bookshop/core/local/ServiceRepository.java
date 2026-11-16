package com.huimeng.bookshop.core.local;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Buyer feedback and after-sale records, including writes awaiting upload. */
public final class ServiceRepository extends BusinessRepository {
    public ServiceRepository(LocalDatabase db, String space) {
        super(db, space);
    }

    public String networkPath(String path) {
        String[] parts = path.split("/");
        if (parts.length < 3) return path;
        String resource = parts[1].equals("order-after-sale") ? "buyerOrders" : parts[1];
        JSONObject record = db.entity(space, resource, parts[2]);
        String remote = record == null ? parts[2] : record.optString("remoteId", parts[2]);
        return remote.startsWith("local-") ? "" : "service/" + parts[1] + "/" + remote;
    }

    public JSONObject read(String path, JSONObject network) throws Exception {
        String[] parts = path.split("/");
        String resource = parts[1];
        if (resource.equals("order-after-sale")) return orderContext(parts[2], network);
        if (parts.length == 3) {
            if (network != null && network.optJSONObject("data") != null)
                remember(resource, network.getJSONObject("data"), network.optBoolean("cached"));
            return data(require(resource, parts[2]));
        }
        JSONArray remote = network == null ? null : network.optJSONArray("data");
        for (int i = 0; remote != null && i < remote.length(); i++)
            remember(resource, remote.getJSONObject(i), network.optBoolean("cached"));
        JSONArray records = db.list(space, resource);
        java.util.List<JSONObject> sorted = new java.util.ArrayList<>();
        for (int i = 0; i < records.length(); i++) sorted.add(records.getJSONObject(i));
        sorted.sort(java.util.Comparator.comparing((JSONObject row) -> row.optString("updated_at")).reversed());
        return data(new JSONArray(sorted));
    }

    private void remember(String resource, JSONObject remote, boolean cached) throws JSONException {
        String key = remote.getString("id");
        JSONObject current = db.entity(space, resource, key);
        String clientId = remote.optString("client_id");
        if (current == null && !clientId.isEmpty())
            current = db.entity(space, resource, "local-" + clientId);
        if (cached && current != null) return;
        String id = current == null ? key : current.getString("id");
        JSONObject merged = current == null ? new JSONObject() : new JSONObject(current.toString());
        java.util.Iterator<String> fields = remote.keys();
        while (fields.hasNext()) {
            String field = fields.next();
            merged.put(field, remote.get(field));
        }
        merged.put("id", id);
        db.saveConfirmed(space, resource, id, merged, 0);
    }

    private JSONObject orderContext(String id, JSONObject network) throws Exception {
        JSONObject result = network == null ? null : network.optJSONObject("data");
        JSONObject localSale = latestSale(id);
        if (result != null) {
            db.metadata(orderContextKey(id), result.toString());
            JSONObject sale = result.optJSONObject("sale");
            if (sale != null) remember("after-sales", sale, network.optBoolean("cached"));
            if (localSale != null && (sale == null || localSale.has("pendingAction")))
                result.put("sale", localSale);
            return data(result);
        }
        JSONObject order = db.entity(space, "buyerOrders", id);
        String cached = db.metadata(orderContextKey(id));
        if (order == null && !cached.isEmpty()) {
            JSONObject saved = new JSONObject(cached);
            if (localSale != null) saved.put("sale", localSale);
            return data(saved);
        }
        order = new OrderRepository(db, space).handle(0, "me/orders/" + id, null).getJSONObject("data");
        return data(new JSONObject().put("order", order).put("sale", localSale == null ? JSONObject.NULL : localSale)
            .put("items", refundableItems(order, id)));
    }

    private String orderContextKey(String id) {
        return "service-order:" + space + ":" + id;
    }

    JSONObject latestSale(String id) throws JSONException {
        JSONObject order = db.entity(space, "buyerOrders", id);
        String local = order == null ? id : order.optString("id", id);
        String remote = order == null ? id : order.optString("remoteId", id);
        JSONArray records = db.list(space, "after-sales");
        JSONObject latest = null;
        for (int i = 0; i < records.length(); i++) {
            JSONObject row = records.getJSONObject(i);
            String associated = row.optString("order_id");
            if ((associated.equals(id) || associated.equals(local) || associated.equals(remote))
                && (latest == null || row.optString("created_at").compareTo(latest.optString("created_at")) > 0))
                latest = row;
        }
        return latest;
    }

    private JSONArray refundableItems(JSONObject order, String id) throws JSONException {
        JSONArray items = order.optJSONArray("items"), sales = db.list(space, "after-sales"), rows = new JSONArray();
        for (int i = 0; items != null && i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            String key = item.getString("id");
            int quantity = item.optInt("quantity");
            long price = item.optLong("unit_price_cents", item.optLong("unitPriceCents"));
            long remaining = item.optLong("subtotal_cents", item.optLong("subtotalCents", price * quantity));
            int returnable = quantity;
            for (int j = 0; j < sales.length(); j++) {
                JSONObject sale = sales.getJSONObject(j);
                String saleOrder = sale.optString("order_id");
                if ((!id.equals(saleOrder) && !order.optString("id", id).equals(saleOrder) && !order.optString("remoteId").equals(saleOrder))
                    || Set.of("REJECTED", "CANCELLED").contains(sale.optString("status"))) continue;
                JSONArray applied = sale.optJSONArray("items");
                for (int k = 0; applied != null && k < applied.length(); k++) {
                    JSONObject line = applied.getJSONObject(k);
                    if (key.equals(line.optString("id"))) {
                        remaining -= line.optLong("amount_cents");
                        returnable -= line.optInt("restock_quantity");
                    }
                }
            }
            rows.put(new JSONObject().put("id", key)
                .put("product_id", item.optString("product_id", item.optString("productId")))
                .put("product_name", item.optString("product_name", item.optString("productName")))
                .put("unit_price_cents", price).put("refundable_cents", Math.max(0, remaining))
                .put("returnable_quantity", Math.max(0, returnable)));
        }
        return rows;
    }

    public JSONObject write(String path, JSONObject body) throws Exception {
        String[] parts = path.split("/");
        String resource = parts[1], stamp = Instant.now().toString();
        boolean feedback = resource.equals("feedback");
        String action = parts.length == 2 ? "create" : body.optString("action");
        String prefix = feedback ? "feedback." : "afterSale.";
        android.database.sqlite.SQLiteDatabase sql = db.getWritableDatabase();
        sql.beginTransaction();
        try {
            JSONObject record;
            if (action.equals("create")) {
                String clientId = text(body, "clientId", 1, 128);
                String id = "local-" + clientId;
                record = db.entity(space, resource, id);
                if (record == null) {
                    record = feedback ? feedback(body) : sale(body);
                    record.put("id", id).put("client_id", clientId).put("created_at", stamp);
                    saveRecord(resource, prefix + action, record, body, stamp);
                }
            } else {
                record = require(resource, parts[2]);
                if (feedback && action.equals("reply")) {
                    String content = text(body, "content", 1, 2000);
                    String clientId = text(body, "clientId", 1, 128);
                    JSONArray replies = record.optJSONArray("replies");
                    if (replies == null) replies = new JSONArray();
                    for (int i = 0; i < replies.length(); i++)
                        if (clientId.equals(replies.getJSONObject(i).optString("client_id"))) {
                            sql.setTransactionSuccessful();
                            return data(record);
                        }
                    replies.put(new JSONObject().put("content", content).put("client_id", clientId)
                        .put("sender", profile().optString("name")).put("sender_role", "USER").put("created_at", stamp));
                    record.put("replies", replies).put("status", "PROCESSING");
                } else if (!feedback && action.equals("cancel")
                    && Set.of("MERCHANT_REVIEW", "ADMIN_REVIEW", "WAITING_RETURN").contains(record.optString("status"))) {
                    record.put("status", "CANCELLED").put("can_apply_again", true);
                } else if (!feedback && action.equals("return") && record.optString("status").equals("WAITING_RETURN")) {
                    record.put("status", "RETURNING").put("carrier", text(body, "carrier", 1, 60))
                        .put("tracking_no", text(body, "trackingNo", 1, 100));
                } else {
                    throw new IllegalArgumentException("当前记录无法执行此操作");
                }
                saveRecord(resource, prefix + action, record, body, stamp);
            }
            sql.setTransactionSuccessful();
            return data(record);
        } finally {
            sql.endTransaction();
        }
    }

    private void saveRecord(String resource, String action, JSONObject record, JSONObject body, String stamp) throws JSONException {
        record.put("updated_at", stamp);
        if (!record.optBoolean("sample")) record.put("pendingAction", action);
        save(resource, record.getString("id"), action, record, body);
    }

    private JSONObject feedback(JSONObject body) throws JSONException {
        String type = body.optString("type");
        if (!Set.of("功能问题", "订单问题", "使用建议", "其他").contains(type))
            throw new IllegalArgumentException("请选择反馈类型");
        return new JSONObject().put("title", text(body, "title", 1, 100)).put("type", type)
            .put("content", text(body, "content", 1, 2000)).put("status", "OPEN").put("replies", new JSONArray());
    }

    private JSONObject sale(JSONObject payload) throws Exception {
        String id = payload.getString("orderId");
        JSONObject previous = latestSale(id);
        if (previous != null && !Set.of("REJECTED", "CANCELLED", "COMPLETED").contains(previous.optString("status")))
            throw new IllegalArgumentException("此订单已有进行中的售后");
        JSONObject orderView = orderContext(id, null).getJSONObject("data");
        JSONObject order = orderView.getJSONObject("order");
        String state = order.optString("status"), type = payload.optString("type");
        if (!Set.of("PAID", "SHIPPED", "COMPLETED").contains(state))
            throw new IllegalArgumentException("当前订单无法申请售后");
        if (!Set.of("REFUND_ONLY", "RETURN_REFUND").contains(type) || type.equals("RETURN_REFUND") && state.equals("PAID"))
            throw new IllegalArgumentException("请选择适合当前订单的售后类型");
        JSONArray available = orderView.getJSONArray("items"), selected = payload.getJSONArray("items"), items = new JSONArray();
        HashSet<String> seen = new HashSet<>();
        long total = 0;
        if (selected.length() < 1 || selected.length() > 100) throw new IllegalArgumentException("请选择售后商品");
        for (int i = 0; i < selected.length(); i++) {
            JSONObject item = selected.getJSONObject(i);
            String key = item.getString("orderItemId");
            JSONObject line = null;
            for (int k = 0; k < available.length(); k++) {
                JSONObject candidate = available.getJSONObject(k);
                if (key.equals(candidate.optString("id"))) line = candidate;
            }
            int quantity = item.optInt("quantity");
            long amount = item.optLong("amountCents");
            if (line == null) {
                String cache = db.metadata(orderContextKey(id));
                JSONArray remoteItems = cache.isEmpty() ? null : new JSONObject(cache).optJSONArray("items");
                for (int k = 0; remoteItems != null && k < remoteItems.length(); k++) {
                    JSONObject candidate = remoteItems.getJSONObject(k);
                    if (key.equals(candidate.optString("id"))) line = candidate;
                }
            }
            if (line == null || !seen.add(key) || quantity < 1 || quantity > line.optInt("returnable_quantity")
                || amount < 1 || amount > Math.min(line.optLong("refundable_cents"), quantity * line.optLong("unit_price_cents")))
                throw new IllegalArgumentException("请选择有效的商品、数量和退款金额");
            if (key.startsWith("local-")) item.put("productId", line.getString("product_id"));
            total += amount;
            int restock = type.equals("RETURN_REFUND") ? quantity : state.equals("PAID") && amount == line.optLong("refundable_cents")
                ? line.optInt("returnable_quantity") : state.equals("PAID") && amount == quantity * line.optLong("unit_price_cents") ? quantity : 0;
            items.put(line.put("quantity", quantity).put("amount_cents", amount).put("restock_quantity", restock));
        }
        return new JSONObject().put("order_id", id).put("type", type).put("reason", text(payload,"reason",1,200))
            .put("description", text(payload,"description",0,2000)).put("images", payload.optJSONArray("images"))
            .put("status", "MERCHANT_REVIEW").put("amount_cents", total).put("items", items)
            .put("sample", order.optBoolean("sample"));
    }
}
