package com.huimeng.bookshop.ui;

import org.json.JSONObject;
import java.util.Locale;

/** Shared predicates keep workbench counts consistent with the lists they open. */
final class MerchantWork {
    static final String[] PRODUCT_FILTERS={"","ONLINE","PENDING_REVIEW","ACTION","OFFLINE"};
    static final String[] PRODUCT_LABELS={"全部状态","在售","审核中 / 待同步","待修改 / 提交","已下架"};
    static final String[] ORDER_FILTERS={"","TO_SHIP","DELIVERY","COMPLETED","AFTER_SALE","CLOSED","WAITING_PAYMENT"};
    static final String[] ORDER_LABELS={"全部状态","待发货","配送中","已完成","售后 / 退款","已关闭","待付款"};
    static boolean saleNeedsAction(String status){return "MERCHANT_REVIEW".equals(status)||"RETURNING".equals(status);}
    static boolean productNeedsAction(JSONObject row){
        if(reviewPending(row))return false;
        return "DRAFT".equals(row.optString("status"))||"REJECTED".equals(row.optString("status"))||"REJECTED".equals(row.optString("reviewStatus"));
    }
    static boolean reviewPending(JSONObject row){return "PENDING_REVIEW".equals(row.optString("status"))||"submit".equals(row.optString("pendingAction"));}
    static boolean readyToShip(JSONObject row){
        String state=row.optString("logistics_status",row.optString("status"));
        return ("PREPARING".equals(state)||"READY_TO_SHIP".equals(state)||"PAID".equals(state))&&!AfterSaleState.blocked(row)&&!row.has("pendingAction");
    }
    static String productStatus(JSONObject row){
        if("submit".equals(row.optString("pendingAction")))return "送审待同步";
        if("REJECTED".equals(row.optString("reviewStatus")))return "已驳回 · 待修改";
        return switch(row.optString("status")){case "ONLINE"->"在售";case "DRAFT"->"草稿 · 待提交";case "PENDING_REVIEW"->"审核中";case "OFFLINE"->"已下架";case "PLATFORM_OFFLINE"->"平台下架";case "REJECTED"->"已驳回 · 待修改";default->"状态待确认";};
    }
    static String productAction(JSONObject row){return reviewPending(row)?"查看审核":productNeedsAction(row)?"继续编辑":"查看管理";}
    static boolean productMatches(JSONObject row,String query,String filter,String category){
        if(!category.isEmpty()&&!category.equals(row.optString("categoryId",row.optString("category_id"))))return false;
        if(!search(row,query,"id","remoteId","name","category","categoryName"))return false;
        return switch(filter){case "ACTION"->productNeedsAction(row);case "PENDING_REVIEW"->reviewPending(row);case "OFFLINE"->"OFFLINE".equals(row.optString("status"))||"PLATFORM_OFFLINE".equals(row.optString("status"));case ""->true;default->filter.equals(row.optString("status"));};
    }
    static boolean orderMatches(JSONObject row,String query,String filter){
        if(!search(row,query,"id","remoteId","buyer","productName"))return false;
        String state=row.optString("logistics_status",row.optString("status"));
        return switch(filter){case "TO_SHIP"->readyToShip(row);case "DELIVERY"->java.util.Arrays.asList("SHIPPED","IN_TRANSIT","OUT_FOR_DELIVERY").contains(state)&&!AfterSaleState.full(row);case "AFTER_SALE"->(!row.isNull("after_sale_state")&&!row.optString("after_sale_state").isEmpty())||row.optLong("refunded_cents")>0;case "CLOSED"->"CANCELLED".equals(state)||"EXPIRED".equals(state);case "WAITING_PAYMENT"->"WAITING_PAYMENT".equals(state)||"PENDING_PAYMENT".equals(state);case "COMPLETED"->"COMPLETED".equals(state);default->true;};
    }
    private static boolean search(JSONObject row,String query,String... fields){
        String keyword=query.trim().toLowerCase(Locale.ROOT);if(keyword.isEmpty())return true;
        for(String field:fields)if(row.optString(field).toLowerCase(Locale.ROOT).contains(keyword))return true;return false;
    }
}
