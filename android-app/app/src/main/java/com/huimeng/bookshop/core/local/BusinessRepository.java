package com.huimeng.bookshop.core.local;
import org.json.*;
import java.util.UUID;
abstract class BusinessRepository {
    protected final LocalDatabase db;protected final String space;
    BusinessRepository(LocalDatabase db,String space){this.db=db;this.space=space;}
    protected JSONObject data(Object value)throws JSONException{return new JSONObject().put("data",value==null?JSONObject.NULL:value);}
    protected JSONObject require(String resource,String id){JSONObject value=db.entity(space,resource,id);if(value==null)throw new IllegalArgumentException("本地尚无此内容，请联网同步后重试");return value;}
    protected String id(){return "local-"+UUID.randomUUID();}
    protected String text(JSONObject body,String key,int min,int max){String value=body.optString(key).trim();if(value.length()<min||value.length()>max)throw new IllegalArgumentException("请填写有效的"+key);return value;}
    protected JSONObject profile(){JSONObject p=db.entity(space,"profile","self");if(p!=null)return p;return LocalDatabase.object("{\"id\":\""+space+"\",\"name\":\"本地用户\",\"username\":\"local_reader\",\"roles\":[\"USER\"]}");}
    protected JSONObject save(String resource,String id,String action,JSONObject projection,JSONObject payload)throws JSONException{
        boolean sample=projection.optBoolean("sample");projection.put("id",id).put("syncState",sample?"LOCAL_SAMPLE":"PENDING");
        String operation=db.mutate(space,resource,id,action,projection,payload,sample);
        return new JSONObject().put("id",id).put("ok",true).put("pending",true).put("operationId",operation).put("message",sample?"已保存在本地示例空间":"已保存在本地，待联网同步");
    }
    protected JSONObject remove(String resource,String id,String action)throws JSONException{JSONObject old=require(resource,id);old.put("deleted",true);return save(resource,id,action,old,new JSONObject().put("id",id));}
    protected JSONArray filter(JSONArray rows,String key,String value){JSONArray result=new JSONArray();for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row!=null&&value.equals(row.optString(key)))result.put(row);}return result;}
}
