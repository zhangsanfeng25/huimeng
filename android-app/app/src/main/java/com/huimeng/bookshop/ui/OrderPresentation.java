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
        return id.isEmpty()?"订单号：本地待同步（长按复制本地编号）":"订单号："+id;
    }
    static String receipt(JSONObject order,Instant now){
        String logistics=order.optString("logistics_status",order.optString("status"));
        if(!"OUT_FOR_DELIVERY".equals(logistics)||AfterSaleState.full(order))return "";
        if(AfterSaleState.active(order))return "售后处理中，已暂停自动确认收货";
        try{
            Instant deadline=Instant.parse(order.getString("shipped_at")).plusSeconds(30L*86400);
            long seconds=deadline.getEpochSecond()-now.getEpochSecond();
            if(seconds<=0)return "已到自动确认收货时间，联网后以平台状态为准";
            return "商品将在"+Math.max(1,(seconds+86399)/86400)+"天后自动确认收货（自发货起30天）";
        }catch(Exception ignored){return "商品将在发货满30天后自动确认收货";}
    }
}
