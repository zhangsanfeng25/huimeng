package com.huimeng.bookshop.ui;

import org.json.JSONArray;
import org.json.JSONObject;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Show the confirmed current state and recorded actions, never inferred future steps. */
final class OrderProgress {
    private record Step(String title,String note,String time,int sequence){}
    static String format(JSONObject order){
        boolean local="LOCAL".equals(order.optString("confirmedState"));
        String current=local?"待联网确认":current(order);
        if(local)return "当前："+current+"\n\n订单意向已保存在本机，尚未提交至平台。";
        List<Step> steps=new ArrayList<>();append(steps,order.optJSONArray("events"),false);append(steps,order.optJSONArray("serviceEvents"),true);
        steps.sort(Comparator.comparingLong((Step s)->epoch(s.time())).thenComparingInt(Step::sequence).reversed());
        StringBuilder text=new StringBuilder("当前："+current);
        for(Step step:steps){text.append("\n\n● ").append(step.title());if(!step.note().isEmpty())text.append("\n   ").append(step.note());if(!step.time().isEmpty())text.append("\n   ").append(date(step.time()));}
        if(steps.isEmpty())text.append("\n\n暂无已记录的操作。");return text.toString();
    }
    private static void append(List<Step> rows,JSONArray events,boolean service){
        if(events==null)return;for(int i=0;i<events.length();i++){JSONObject e=events.optJSONObject(i);if(e==null)continue;String title=service?serviceTitle(e):orderTitle(e);if(title.isEmpty())continue;
            String note=e.isNull("note")?"":e.optString("note").trim();if(note.equals(title)||note.matches("[A-Z_]+"))note="";
            rows.add(new Step(title,note,e.optString("createdAt"),rows.size()));}
    }
    private static String current(JSONObject order){String sale=order.isNull("after_sale_state")?"":order.optString("after_sale_state");if(AfterSaleState.full(order))return "已全额退款";
        if(AfterSaleState.active(order))return ServiceActivity.label(sale);
        return AfterSaleState.describe(order,switch(order.optString("logistics_status",order.optString("status"))){case "PENDING_PAYMENT","WAITING_PAYMENT"->"待付款";case "PAID","PREPARING"->"准备出库";case "READY_TO_SHIP"->"待发货";case "SHIPPED"->"已发货";case "IN_TRANSIT"->"正在运输";case "OUT_FOR_DELIVERY"->"正在派送";case "COMPLETED"->"订单已完成";case "CANCELLED","EXPIRED"->"订单已关闭";default->"状态待确认";});
    }
    private static String orderTitle(JSONObject e){return switch(e.optString("toStatus")){
        case "PENDING_PAYMENT","WAITING_PAYMENT"->"订单已提交，待付款";case "PAID","PREPARING"->"付款已完成，商家准备出库";
        case "READY_TO_SHIP"->"出库准备已完成，待发货";case "SHIPPED"->"商家已发货";case "IN_TRANSIT"->"订单正在运输";case "OUT_FOR_DELIVERY"->"订单正在派送";
        case "COMPLETED"->"已确认收货，订单完成";case "CANCELLED"->"订单已取消";case "EXPIRED"->"订单超时未付款，已关闭";default->"";};}
    private static String serviceTitle(JSONObject e){return switch(e.optString("action")){
        case "CREATE"->"售后申请已提交，待商家审核";case "merchant-review"->"商家已初审，待平台审核";
        case "approve"->"RETURN_REFUND".equals(e.optString("type"))?"平台审核已通过，待买家退货":"平台审核已通过，待退款";
        case "reject"->"平台已驳回售后申请";case "return"->"退货已寄出，退货运输中";case "receive"->"商家已确认收到退货，待退款";case "refund"->"退款已完成";case "cancel"->"售后申请已撤销";default->"";};}
    private static long epoch(String value){try{return Instant.parse(value).toEpochMilli();}catch(Exception ignored){return 0;}}
    private static String date(String value){try{return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.parse(value));}catch(Exception ignored){String text=value.replace('T',' ').replace("Z","");return text.length()>16?text.substring(0,16):text;}}
}
