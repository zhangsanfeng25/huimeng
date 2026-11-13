package com.huimeng.bookshop.ui;
import org.json.JSONObject;
final class AfterSaleState {
    static boolean active(JSONObject order){String state=order.optString("after_sale_state");return !state.isEmpty()&&!java.util.Arrays.asList("COMPLETED","REJECTED","CANCELLED").contains(state);}
    static boolean full(JSONObject order){return order.optLong("total_cents")>0&&order.optLong("refunded_cents")>=order.optLong("total_cents");}
    static boolean blocked(JSONObject order){return active(order)||full(order);}
    static String refundLabel(JSONObject order){return order.optLong("refunded_cents")>0?(full(order)?"已全额退款":"部分退款"):"";}
    static String describe(JSONObject order,String normal){String refund=refundLabel(order);if(active(order))return "售后处理中"+(refund.isEmpty()?"":" · "+refund);return refund.isEmpty()?normal:full(order)?refund:normal+" · "+refund;}
    static String detailStatus(JSONObject order,String normal){
        String state=order.isNull("after_sale_state")?"":order.optString("after_sale_state");
        if(full(order))return "已全额退款"+("COMPLETED".equals(state)?" · 售后完成":"");
        if(active(order))return "售后处理中\n"+ServiceActivity.label(state);
        if("COMPLETED".equals(state)){
            String refund=refundLabel(order);
            return normal+"\n"+(refund.isEmpty()?"":refund+" · ")+"售后完成";
        }
        return state.isEmpty()?normal:normal+"\n售后："+ServiceActivity.label(state);
    }
}
