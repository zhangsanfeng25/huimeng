package com.huimeng.bookshop.model;

import org.json.JSONObject;

public record Product(String id,String name,String summary,String category,String ageRange,
                      String shop,int priceCents,String coverUrl,int stock,boolean arEnabled,String audioUrl){
    public static Product from(JSONObject item){
        return new Product(item.optString("id"),item.optString("name","绘本"),item.optString("summary"),
                item.optString("category"),item.optString("ageRange"),item.optString("shop"),
                item.optInt("priceCents"),item.optString("coverUrl"),"ONLINE".equals(item.optString("status","ONLINE"))?item.optInt("stock"):0,
                item.optBoolean("arEnabled"),item.optString("audioUrl"));
    }
    public String price(){return String.format(java.util.Locale.CHINA,"¥%.2f",priceCents/100.0);}
}
