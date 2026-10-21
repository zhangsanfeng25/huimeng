package com.huimeng.bookshop.core;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.util.UUID;

/** Stable keys and persisted retry identities, including after process recreation. */
public final class ServiceDrafts {
    private final SharedPreferences store;private final String key;
    public ServiceDrafts(SharedPreferences store,String key){this.store=store;this.key=key;}
    public static String key(String scope,String kind,String record,String order){return scope+"|"+kind+"|"+(record==null||record.isEmpty()?"new:"+(order==null?"":order):record);}
    public JSONObject prepare(String content,String context)throws org.json.JSONException{
        String signature=content+"\u0000"+context;String previous=store.getString(key+"|pending","");
        if(!previous.isEmpty()){JSONObject row=new JSONObject(previous);if(signature.equals(row.optString("signature")))return row;}
        JSONObject row=new JSONObject().put("signature",signature).put("content",content).put("clientId",UUID.randomUUID().toString());store.edit().putString(key+"|pending",row.toString()).commit();return row;
    }
    public void acknowledge(String clientId){String previous=store.getString(key+"|pending","");try{if(clientId.equals(new JSONObject(previous).optString("clientId")))store.edit().remove(key+"|pending").apply();}catch(Exception ignored){}}
    public static boolean canClear(String current,String sent){return current.trim().equals(sent);}
}
