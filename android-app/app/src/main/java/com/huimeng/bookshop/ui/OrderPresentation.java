package com.huimeng.bookshop.ui;

import org.json.JSONObject;
import java.time.Instant;

final class OrderPresentation {
    static String platformId(JSONObject order){
        String remote=order.isNull("remoteId")?"":order.optString("remoteId");
        if(!remote.isEmpty()&&!remote.startsWith("local-"))return remote;
        String id=order.optString("id");return id.startsWith("local-")?"":id;
    }
    static String number(JSONObject order){
        String id=platformId(order);
        return "订单号："+displayNumber(id.isEmpty()?order.optString("id"):id,"DD");
    }
    static String displayNumber(String id,String prefix){
        if(id.matches("[A-Za-z]{1,4}-?[0-9]{5,18}"))return id;
        String hex=id.replaceFirst("^local-","").replace("-","");
        if(hex.matches("[a-fA-F0-9]{12,}"))
            return String.format(java.util.Locale.ROOT,"%s-%015d",prefix,Long.parseLong(hex.substring(0,12),16));
        return String.format(java.util.Locale.ROOT,"%s-%010d",prefix,Integer.toUnsignedLong(id.hashCode()));
    }
    static String receipt(JSONObject order,Instant now){
        String logistics=order.optString("logistics_status",order.optString("status"));
        if(!"OUT_FOR_DELIVERY".equals(logistics)||AfterSaleState.full(order))return "";
        if(AfterSaleState.active(order))return "售后处理中，已暂停自动确认收货";
        try{
            Instant deadline=Instant.parse(order.getString("shipped_at")).plusSeconds(30L*86400);
            long seconds=deadline.getEpochSecond()-now.getEpochSecond();
            if(seconds<=0)return "等待自动确认收货";
            return "商品将在"+Math.max(1,(seconds+86399)/86400)+"天后自动确认收货（自发货起30天）";
        }catch(Exception ignored){return "商品将在发货满30天后自动确认收货";}
    }
}
