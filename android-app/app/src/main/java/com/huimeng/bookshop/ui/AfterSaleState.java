package com.huimeng.bookshop.ui;
import org.json.JSONObject;
final class AfterSaleState {
    static boolean active(JSONObject order){String state=order.optString("after_sale_state");return !state.isEmpty()&&!java.util.Arrays.asList("COMPLETED","REJECTED","CANCELLED").contains(state);}
    static boolean full(JSONObject order){return order.optLong("total_cents")>0&&order.optLong("refunded_cents")>=order.optLong("total_cents");}
    static boolean blocked(JSONObject order){return active(order)||full(order);}
    static String refundLabel(JSONObject order){return order.optLong("refunded_cents")>0?(full(order)?"已全额退款":"部分退款"):"";}
    static String describe(JSONObject order,String normal){String refund=refundLabel(order);if(active(order))return "售后处理中"+(refund.isEmpty()?"":" · "+refund);return refund.isEmpty()?normal:full(order)?refund:normal+" · "+refund;}
}
