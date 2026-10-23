package com.huimeng.bookshop.core.local;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.UUID;

/** Local business values and their outbox commit together. Never drop user data on upgrade. */
public final class LocalDatabase extends SQLiteOpenHelper {
    private static LocalDatabase instance;
    public static synchronized LocalDatabase get(Context context){if(instance==null)instance=new LocalDatabase(context.getApplicationContext(),"huimeng-local.db");return instance;}
    public LocalDatabase(Context context,String name){super(context,name,null,1);setWriteAheadLoggingEnabled(true);}
    @Override public void onConfigure(SQLiteDatabase db){db.setForeignKeyConstraintsEnabled(true);}
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE local_spaces(id TEXT PRIMARY KEY,origin TEXT NOT NULL DEFAULT '',user_id TEXT NOT NULL DEFAULT '',role TEXT NOT NULL DEFAULT 'USER',active INTEGER NOT NULL DEFAULT 0,created_at INTEGER NOT NULL)");
        db.execSQL("CREATE UNIQUE INDEX active_space ON local_spaces(active) WHERE active=1");
        db.execSQL("CREATE UNIQUE INDEX bound_space ON local_spaces(origin,user_id) WHERE user_id<>''");
        db.execSQL("CREATE TABLE local_entities(space_id TEXT NOT NULL REFERENCES local_spaces(id),resource TEXT NOT NULL,id TEXT NOT NULL,remote_id TEXT,data TEXT NOT NULL,confirmed TEXT,version INTEGER NOT NULL DEFAULT 0,revision INTEGER NOT NULL DEFAULT 0,deleted INTEGER NOT NULL DEFAULT 0,sample INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(space_id,resource,id))");
        db.execSQL("CREATE INDEX entity_remote ON local_entities(space_id,resource,remote_id)");
        db.execSQL("CREATE TABLE sync_operations(operation_id TEXT PRIMARY KEY,space_id TEXT NOT NULL REFERENCES local_spaces(id),resource TEXT NOT NULL,entity_id TEXT NOT NULL,action TEXT NOT NULL,payload TEXT NOT NULL,payload_hash TEXT NOT NULL DEFAULT '',base_version INTEGER NOT NULL DEFAULT 0,dependencies TEXT NOT NULL DEFAULT '[]',state TEXT NOT NULL DEFAULT 'PENDING' CHECK(state IN ('PENDING','IN_FLIGHT','WAITING_AUTH','BLOCKED','CONFLICT','ACKED')),attempts INTEGER NOT NULL DEFAULT 0,next_attempt_at INTEGER NOT NULL DEFAULT 0,error TEXT NOT NULL DEFAULT '',created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX outbox_ready ON sync_operations(space_id,state,next_attempt_at,created_at)");
        db.execSQL("CREATE TABLE local_media(space_id TEXT NOT NULL REFERENCES local_spaces(id),id TEXT NOT NULL,path TEXT NOT NULL,sha256 TEXT NOT NULL,url TEXT NOT NULL DEFAULT '',purpose TEXT NOT NULL,state TEXT NOT NULL DEFAULT 'PENDING',PRIMARY KEY(space_id,id))");
        db.execSQL("CREATE TABLE sync_results(operation_id TEXT PRIMARY KEY REFERENCES sync_operations(operation_id),result TEXT NOT NULL,confirmed_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE local_metadata(key TEXT PRIMARY KEY,value TEXT NOT NULL)");
        db.execSQL("CREATE TABLE snapshot_pages(space_id TEXT NOT NULL,resource TEXT NOT NULL,snapshot_id TEXT NOT NULL,offset INTEGER NOT NULL,data TEXT NOT NULL,PRIMARY KEY(space_id,resource,snapshot_id,offset))");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){throw new IllegalStateException("本地数据版本暂不支持，原数据已保留");}
    public static JSONObject object(String value){try{return new JSONObject(value);}catch(Exception error){throw new IllegalStateException("本地数据损坏，已保留原数据",error);}}
    public JSONObject entity(String space,String resource,String id){try(Cursor c=getReadableDatabase().rawQuery("SELECT data,version,remote_id,deleted,sample FROM local_entities WHERE space_id=? AND resource=? AND (id=? OR remote_id=?)",new String[]{space,resource,id,id})){if(!c.moveToFirst()||c.getInt(3)==1)return null;JSONObject value=object(c.getString(0));try{value.put("version",c.getLong(1));value.put("sample",c.getInt(4)==1);if(!c.isNull(2))value.put("remoteId",c.getString(2));}catch(Exception e){throw new IllegalStateException(e);}return value;}}
    public JSONArray list(String space,String resource){JSONArray result=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT data FROM local_entities WHERE space_id=? AND resource=? AND deleted=0 ORDER BY rowid DESC",new String[]{space,resource})){while(c.moveToNext())result.put(object(c.getString(0)));}return result;}
    public void saveConfirmed(String space,String resource,String id,JSONObject value,long version){
        SQLiteDatabase db=getWritableDatabase();String json=value.toString();ContentValues row=new ContentValues();row.put("space_id",space);row.put("resource",resource);row.put("id",id);row.put("remote_id",id);row.put("data",json);row.put("confirmed",json);row.put("version",version);row.put("sample",value.optBoolean("sample")?1:0);
        db.insertWithOnConflict("local_entities",null,row,SQLiteDatabase.CONFLICT_IGNORE);
        // Preserve the local projection if any unacknowledged writes exist.
        db.execSQL("UPDATE local_entities SET confirmed=?,version=?,data=CASE WHEN EXISTS(SELECT 1 FROM sync_operations o WHERE o.space_id=local_entities.space_id AND o.resource=local_entities.resource AND o.entity_id=local_entities.id AND o.state<>'ACKED') THEN data ELSE ? END WHERE space_id=? AND resource=? AND id=?",new Object[]{json,version,json,space,resource,id});
    }
    public String mutate(String space,String resource,String id,String action,JSONObject value,boolean sample){return mutate(space,resource,id,action,value,value,sample);}
    public String mutate(String space,String resource,String id,String action,JSONObject projection,JSONObject payload,boolean sample){
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
            JSONObject previous=entity(space,resource,id);long version=previous==null?0:previous.optLong("version");
            ContentValues row=new ContentValues();row.put("space_id",space);row.put("resource",resource);row.put("id",id);row.put("data",projection.toString());row.put("sample",sample?1:0);row.put("deleted",projection.optBoolean("deleted")?1:0);
            int changed=db.update("local_entities",row,"space_id=? AND resource=? AND id=?",new String[]{space,resource,id});if(changed==0)db.insertOrThrow("local_entities",null,row);
            db.execSQL("UPDATE local_entities SET revision=revision+1 WHERE space_id=? AND resource=? AND id=?",new Object[]{space,resource,id});
            String operationId=UUID.randomUUID().toString();
            if(!sample){ContentValues operation=new ContentValues();operation.put("operation_id",operationId);operation.put("space_id",space);operation.put("resource",resource);operation.put("entity_id",id);operation.put("action",action);operation.put("payload",payload.toString());operation.put("base_version",version);operation.put("created_at",System.currentTimeMillis());db.insertOrThrow("sync_operations",null,operation);}
            db.setTransactionSuccessful();return operationId;
        }finally{db.endTransaction();}
    }
    public JSONArray pending(String space){JSONArray rows=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT operation_id,resource,entity_id,action,payload,base_version,state,attempts,next_attempt_at,error FROM sync_operations WHERE space_id=? AND state<>'ACKED' ORDER BY created_at,rowid",new String[]{space})){while(c.moveToNext())try{rows.put(new JSONObject().put("operationId",c.getString(0)).put("spaceId",space).put("resource",c.getString(1)).put("entityId",c.getString(2)).put("action",c.getString(3)).put("payload",object(c.getString(4))).put("baseVersion",c.getLong(5)).put("state",c.getString(6)).put("attempts",c.getInt(7)).put("nextAttemptAt",c.getLong(8)).put("error",c.getString(9)));}catch(Exception e){throw new IllegalStateException(e);}}return rows;}
    public void acknowledge(JSONObject op,JSONObject result)throws org.json.JSONException{
        String operation=op.getString("operationId"),space=op.getString("spaceId"),resource=op.getString("resource"),id=op.getString("entityId");
        if(!operation.equals(result.optString("operationId")))throw new IllegalArgumentException("服务器回执不匹配");
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
            ContentValues receipt=new ContentValues();receipt.put("operation_id",operation);receipt.put("result",result.toString());receipt.put("confirmed_at",System.currentTimeMillis());db.insertWithOnConflict("sync_results",null,receipt,SQLiteDatabase.CONFLICT_IGNORE);
            db.execSQL("UPDATE sync_operations SET state='ACKED',error='' WHERE operation_id=? AND space_id=?",new Object[]{operation,space});
            String remote=result.optString("entityId",id);long version=result.optLong("version");
            db.execSQL("UPDATE local_entities SET remote_id=?,version=? WHERE space_id=? AND resource=? AND id=?",new Object[]{remote,version,space,resource,id});
            db.execSQL("UPDATE sync_operations SET base_version=? WHERE space_id=? AND resource=? AND entity_id=? AND state='PENDING' AND payload_hash='' AND base_version=?",new Object[]{version,space,resource,id,op.optLong("baseVersion")});
            JSONObject current=entity(space,resource,id);if(current!=null){boolean more=false;try(Cursor c=db.rawQuery("SELECT 1 FROM sync_operations WHERE space_id=? AND resource=? AND entity_id=? AND state<>'ACKED' LIMIT 1",new String[]{space,resource,id})){more=c.moveToFirst();}if(!more){current.put("syncState","CONFIRMED");current.remove("pendingAction");String json=current.toString();ContentValues row=new ContentValues();row.put("data",json);row.put("confirmed",json);db.update("local_entities",row,"space_id=? AND resource=? AND id=?",new String[]{space,resource,id});}}
            if(op.optString("action").equals("checkout.create")){
                JSONArray orderIds=result.optJSONObject("data")==null?null:result.optJSONObject("data").optJSONArray("orderIds");
                JSONArray localOrders=list(space,"buyerOrders");for(int i=0;i<localOrders.length();i++){JSONObject order=localOrders.getJSONObject(i);if(!id.equals(order.optString("checkout_group_id")))continue;
                    if(orderIds!=null&&orderIds.length()==1){db.execSQL("UPDATE local_entities SET remote_id=?,version=1 WHERE space_id=? AND resource='buyerOrders' AND id=?",new Object[]{orderIds.getString(0),space,order.getString("id")});db.execSQL("UPDATE sync_operations SET base_version=1 WHERE space_id=? AND resource='buyerOrders' AND entity_id=? AND state='PENDING' AND payload_hash='' AND base_version=0",new Object[]{space,order.getString("id")});}
                    else db.execSQL("UPDATE local_entities SET deleted=1 WHERE space_id=? AND resource='buyerOrders' AND id=?",new Object[]{space,order.getString("id")});
                }
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public String metadata(String key){try(Cursor c=getReadableDatabase().rawQuery("SELECT value FROM local_metadata WHERE key=?",new String[]{key})){return c.moveToFirst()?c.getString(0):"";}}
    public void importLegacyCart(String key,String space,JSONArray legacy){
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
            if(!metadata("cart-migrated:"+key).isEmpty()){db.setTransactionSuccessful();return;}
            java.util.Set<String> seen=new java.util.HashSet<>();
            for(int i=0;i<legacy.length();i++){JSONObject row=legacy.optJSONObject(i);if(row==null)continue;String id=row.optString("id");if(id.isEmpty()||!seen.add(id))continue;try{row.put("quantity",Math.max(1,Math.min(99,row.optInt("quantity",1))));saveConfirmed(space,"cart",id,row,0);}catch(org.json.JSONException e){throw new IllegalStateException(e);}}
            metadata("cart-migrated:"+key,"1");db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public void metadata(String key,String value){ContentValues row=new ContentValues();row.put("key",key);row.put("value",value);getWritableDatabase().insertWithOnConflict("local_metadata",null,row,SQLiteDatabase.CONFLICT_REPLACE);}
}
