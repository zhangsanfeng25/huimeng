package com.huimeng.bookshop.core.local;
import org.json.*;
public final class OrderRepository extends BusinessRepository {
    public OrderRepository(LocalDatabase db,String space){super(db,space);}
    public JSONObject handle(int method,String path,JSONObject body)throws Exception{
        if(body==null)body=new JSONObject();String[] p=path.split("/");
        if(path.equals("checkout")&&method==1){
            return createCheckout(body);
        }
        if(p[0].equals("checkout")&&p.length>1){
            JSONObject checkout=require("checkouts",p[1]);
            if(method==0)return data(checkout);
            if(method==1&&p.length==3&&p[2].equals("pay"))return pay(p[1],checkout,body);
            return null;
        }
        if((path.startsWith("me/orders")||path.startsWith("merchant/orders"))){String resource=path.startsWith("merchant/")?"merchantOrders":"buyerOrders";if(method==0)return data(p.length>2?readOrder(resource,require(resource,p[2])):readOrders(resource));JSONObject order=require(resource,p[2]);String action=p.length>3?p[3]:"";if(!java.util.Arrays.asList("cancel","confirm","ship","advance").contains(action))throw new IllegalArgumentException("不支持的订单操作");order.put("pendingAction",action);return save(resource,p[2],"order."+action,order,new JSONObject(body.toString()).put("orderId",p[2]));}
        if(path.equals("me/ratings")&&method==1){text(body,"content",1,1000);int stars=body.optInt("stars");if(stars<1||stars>5)throw new IllegalArgumentException("请选择 1–5 星评分");return save("ownRatings",id(),"rating.create",new JSONObject(body.toString()),body);}
        return null;
    }
    private JSONObject createCheckout(JSONObject body)throws JSONException{
        String addressId=body.getString("addressId");
        JSONObject address=require("addresses",addressId);
        JSONArray items=body.getJSONArray("items"),payloadItems=new JSONArray();
        if(items.length()==0)throw new IllegalArgumentException("请选择商品");
        java.util.Map<String,JSONArray> orders=new java.util.LinkedHashMap<>();
        long total=0;
        boolean sample=false,real=false;
        for(int i=0;i<items.length();i++){
            JSONObject item=items.getJSONObject(i),product=require("products",item.getString("productId"));
            int quantity=item.getInt("quantity");
            if(quantity<1||quantity>99||quantity>product.optInt("stock"))throw new IllegalArgumentException("商品库存不足");
            sample|=product.optBoolean("sample");
            real|=!product.optBoolean("sample");
            long cents=product.optLong("priceCents"),subtotal=cents*quantity;
            total+=subtotal;
            payloadItems.put(new JSONObject(item.toString()).put("expectedPriceCents",cents));
            String shop=product.optString("shop","绘梦书店");
            String merchant=product.optString("merchantId",product.optString("merchant_id",shop));
            orders.computeIfAbsent(merchant,key->new JSONArray()).put(new JSONObject()
                .put("id",id()).put("productId",product.getString("id")).put("shop",shop)
                .put("productName",product.optString("name")).put("coverUrl",product.optString("coverUrl"))
                .put("unitPriceCents",cents).put("quantity",quantity).put("subtotalCents",subtotal));
        }
        if(sample&&real)throw new IllegalArgumentException("本地示例与网络商品请分开下单");
        String group=id(),created=java.time.Instant.now().toString();
        JSONObject payload=new JSONObject(body.toString()).put("items",payloadItems).put("expectedTotalCents",total);
        JSONObject checkout=new JSONObject().put("id",group).put("status","PENDING")
            .put("totalCents",total).put("sample",sample).put("pendingAction","create");
        save("checkouts",group,"checkout.create",checkout,payload);
        JSONArray orderIds=new JSONArray();
        for(JSONArray details:orders.values()){
            JSONObject first=details.getJSONObject(0);
            long subtotal=0;
            for(int i=0;i<details.length();i++)subtotal+=details.getJSONObject(i).getLong("subtotalCents");
            String order=id();
            JSONObject row=new JSONObject().put("id",order).put("checkout_group_id",group)
                .put("checkout_order_index",orderIds.length()).put("total_cents",subtotal).put("status","PENDING_PAYMENT")
                .put("confirmedState","LOCAL").put("pendingAction","create").put("sample",sample)
                .put("productName",first.getString("productName")).put("shop",first.getString("shop"))
                .put("items",details).put("itemCount",details.length()).put("address_snapshot_json",address.toString())
                .put("events",new JSONArray()).put("created_at",created);
            db.saveConfirmed(space,"buyerOrders",order,row,0);
            orderIds.put(order);
        }
        return new JSONObject().put("checkoutGroupId",group).put("totalCents",total).put("orderIds",orderIds).put("pending",true);
    }

    private JSONObject pay(String group,JSONObject checkout,JSONObject body)throws JSONException{
        String status=checkout.optString("status");
        if(status.equals("PAID"))return data(checkout);
        if(!status.equals("PENDING"))throw new IllegalArgumentException("结算单已关闭");
        String method=body.optString("method","DEMO_BALANCE");
        if(!java.util.Set.of("DEMO_BALANCE","DEMO_ALIPAY","DEMO_WECHAT").contains(method))
            throw new IllegalArgumentException("请选择有效的付款方式");
        checkout.put("status","PAID");
        if(checkout.optString("pendingAction").equals("pay")){
            db.updateProjection(space,"checkouts",group,checkout);
        }else{
            JSONObject payload=new JSONObject().put("method",method).put("groupId",group)
                .put("expectedTotalCents",checkout.optLong("totalCents"));
            checkout.put("pendingAction","pay");
            save("checkouts",group,"checkout.pay",checkout,payload);
        }
        return data(checkout);
    }

    private JSONArray readOrders(String resource)throws JSONException{
        JSONArray rows=db.list(space,resource);
        for(int i=0;i<rows.length();i++)readOrder(resource,rows.getJSONObject(i));
        return rows;
    }

    private JSONObject readOrder(String resource,JSONObject order)throws JSONException{
        if(!resource.equals("buyerOrders"))return order;
        if(order.optString("status").equals("PENDING_PAYMENT")){
            JSONObject checkout=db.entity(space,"checkouts",order.optString("checkout_group_id"));
            if(checkout!=null&&checkout.optString("status").equals("PAID"))
                order.put("status","PAID").put("logistics_status","PREPARING").remove("pendingAction");
        }
        JSONObject sale=new ServiceRepository(db,space).latestSale(order.optString("id"));
        if(sale!=null&&(sale.has("pendingAction")||sale.optBoolean("sample")))
            order.put("after_sale_state",sale.optString("status")).put("after_sale_id",sale.optString("id"));
        return order;
    }
}
