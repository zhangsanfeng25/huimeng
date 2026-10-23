package com.huimeng.bookshop.core.local;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.UUID;
import org.json.JSONObject;

public final class LocalSpaceStore {
    private final LocalDatabase store;
    public LocalSpaceStore(LocalDatabase store){this.store=store;}
    public String activeSpaceId(){try(Cursor c=store.getReadableDatabase().rawQuery("SELECT id FROM local_spaces WHERE active=1",null)){return c.moveToFirst()?c.getString(0):"";}}
    public synchronized String ensureActiveSpace(){String id=activeSpaceId();if(!id.isEmpty())return id;id=create("","");select(id);return id;}
    private String create(String origin,String user){String id=UUID.randomUUID().toString();ContentValues v=new ContentValues();v.put("id",id);v.put("origin",origin);v.put("user_id",user);v.put("created_at",System.currentTimeMillis());store.getWritableDatabase().insertOrThrow("local_spaces",null,v);return id;}
    public synchronized String guestSpace(){String id=store.metadata("guest-space:v1");if(id.isEmpty()){id=create("","");store.metadata("guest-space:v1",id);}return id;}
    public String forAccount(String origin,String user){try(Cursor c=store.getReadableDatabase().rawQuery("SELECT id FROM local_spaces WHERE origin=? AND user_id=?",new String[]{origin,user})){if(c.moveToFirst())return c.getString(0);}return create(origin,user);}
    public void select(String id){SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();try{db.execSQL("UPDATE local_spaces SET active=0 WHERE active=1");ContentValues v=new ContentValues();v.put("active",1);if(db.update("local_spaces",v,"id=?",new String[]{id})!=1)throw new IllegalArgumentException("本地空间不存在");db.setTransactionSuccessful();}finally{db.endTransaction();}}
    public JSONObject info(String id){try(Cursor c=store.getReadableDatabase().rawQuery("SELECT origin,user_id,role FROM local_spaces WHERE id=?",new String[]{id})){if(!c.moveToFirst())throw new IllegalArgumentException("本地空间不存在");try{return new JSONObject().put("id",id).put("origin",c.getString(0)).put("userId",c.getString(1)).put("role",c.getString(2));}catch(Exception e){throw new IllegalStateException(e);}}}
    public String bind(String id,String origin,String user){
        if(user.isEmpty()||origin.isEmpty())throw new IllegalArgumentException("请先验证网络账号");JSONObject old=info(id);if(!old.optString("userId").isEmpty()&&(!user.equals(old.optString("userId"))||!origin.equals(old.optString("origin"))))throw new IllegalStateException("本地数据已属于其他账号，请切换空间");
        String merged=store.metadata("merged:"+id);if(!merged.isEmpty()){JSONObject target=info(merged);if(!user.equals(target.optString("userId"))||!origin.equals(target.optString("origin")))throw new IllegalStateException("原本地内容已关联其他账号");return merged;}
        SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();try{
            String target="";try(Cursor c=db.rawQuery("SELECT id FROM local_spaces WHERE origin=? AND user_id=?",new String[]{origin,user})){if(c.moveToFirst())target=c.getString(0);}
            if(!target.isEmpty()&&!target.equals(id)){
                JSONObject profile=store.entity(id,"profile","self");if(profile!=null)store.metadata("merged-profile:"+id,profile.toString());
                db.execSQL("INSERT OR IGNORE INTO local_entities(space_id,resource,id,remote_id,data,confirmed,version,revision,deleted,sample) SELECT ?,resource,id,remote_id,data,confirmed,version,revision,deleted,sample FROM local_entities WHERE space_id=?",new Object[]{target,id});
                db.execSQL("UPDATE sync_operations SET state='CONFLICT',error='本地资料合并后需要确认采用哪个版本' WHERE space_id=? AND resource='profile' AND state<>'ACKED'",new Object[]{id});
                db.execSQL("UPDATE sync_operations SET space_id=? WHERE space_id=?",new Object[]{target,id});db.execSQL("UPDATE local_media SET space_id=? WHERE space_id=?",new Object[]{target,id});store.metadata("merged:"+id,target);db.setTransactionSuccessful();return target;
            }
            ContentValues v=new ContentValues();v.put("origin",origin);v.put("user_id",user);db.update("local_spaces",v,"id=?",new String[]{id});db.setTransactionSuccessful();return id;
        }finally{db.endTransaction();}
    }
    public void role(String id,String role){if(!"USER".equals(role)&&!"MERCHANT".equals(role))throw new IllegalArgumentException("未知角色");ContentValues v=new ContentValues();v.put("role",role);store.getWritableDatabase().update("local_spaces",v,"id=?",new String[]{id});}
}
