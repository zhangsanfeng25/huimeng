package com.huimeng.bookshop.core.local;
import android.content.ContentValues;import android.database.Cursor;import android.database.sqlite.SQLiteDatabase;import org.json.*;
public final class LocalConflictResolver {
 private final LocalDatabase store;
 public LocalConflictResolver(LocalDatabase store){this.store=store;}
 private JSONObject operation(String space,String id)throws Exception{JSONArray rows=store.pending(space);for(int i=0;i<rows.length();i++)if(id.equals(rows.getJSONObject(i).optString("operationId")))return rows.getJSONObject(i);throw new IllegalArgumentException("此待处理记录已变化，请刷新");}
 public String retryWithLocal(String space,String id)throws Exception{
  JSONObject op=operation(space,id);if(!op.optString("state").equals("CONFLICT"))throw new IllegalArgumentException("请先取得明确冲突结果，再处理本地修改");
  String action=op.getString("action"),role=op.getString("submissionRole");if(!java.util.Arrays.asList("address.save","profile.save","profile.avatar","product.save","merchant.profile").contains(action))throw new IllegalArgumentException("请返回对应页面核对内容、价格或订单状态后重新操作");
  SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();try{String resource=op.getString("resource"),entity=op.getString("entityId");JSONObject local=store.entity(space,resource,entity);if(local==null)throw new IllegalArgumentException("本地内容不存在，请保留网络版本后重建");JSONObject payload=op.getJSONObject("payload");JSONArray pending=store.pending(space,role);for(int i=0;i<pending.length();i++){JSONObject newer=pending.getJSONObject(i);if(resource.equals(newer.optString("resource"))&&entity.equals(newer.optString("entityId"))&&action.equals(newer.optString("action")))payload=newer.getJSONObject("payload");}store.metadata("discarded:"+id,local.toString());db.execSQL("UPDATE sync_operations SET state='ACKED',error='已由用户重新确认的操作替代' WHERE space_id=? AND resource=? AND entity_id=? AND action=? AND submission_role=? AND state<>'ACKED'",new Object[]{space,resource,entity,action,role});String next=store.mutate(space,resource,entity,action,local,payload,false);db.setTransactionSuccessful();return next;}finally{db.endTransaction();}
 }
 public void keepServer(String space,String id)throws Exception{
  JSONObject op=operation(space,id);if(op.optString("state").equals("IN_FLIGHT"))throw new IllegalArgumentException("操作正在传输，请稍后处理");String resource=op.getString("resource"),entity=op.getString("entityId");SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();try{
   JSONObject local=store.entity(space,resource,entity);store.metadata("discarded:"+id,local==null?op.toString():local.toString());
   db.execSQL("UPDATE sync_operations SET state='ACKED',error='用户保留网络版本；不撤销可能已在服务器完成的操作' WHERE space_id=? AND resource=? AND entity_id=? AND submission_role=? AND state<>'ACKED'",new Object[]{space,resource,entity,op.getString("submissionRole")});
   try(Cursor c=db.rawQuery("SELECT confirmed FROM local_entities e WHERE space_id=? AND resource=? AND id=? AND NOT EXISTS(SELECT 1 FROM sync_operations o WHERE o.space_id=e.space_id AND o.resource=e.resource AND o.entity_id=e.id AND o.state<>'ACKED')",new String[]{space,resource,entity})){if(c.moveToFirst()){ContentValues value=new ContentValues();if(c.isNull(0))value.put("deleted",1);else{value.put("data",c.getString(0));value.put("deleted",0);}db.update("local_entities",value,"space_id=? AND resource=? AND id=?",new String[]{space,resource,entity});}}
   db.setTransactionSuccessful();
  }finally{db.endTransaction();}
 }
 public void retryBlocked(String space){store.getWritableDatabase().execSQL("UPDATE sync_operations SET state='PENDING',next_attempt_at=0 WHERE space_id=? AND state IN ('PENDING','BLOCKED')",new Object[]{space});store.getWritableDatabase().execSQL("UPDATE local_media SET state='PENDING' WHERE space_id=? AND state='BLOCKED'",new Object[]{space});}
}
