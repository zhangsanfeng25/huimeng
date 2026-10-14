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
    public String forAccount(String origin,String user){try(Cursor c=store.getReadableDatabase().rawQuery("SELECT id FROM local_spaces WHERE origin=? AND user_id=?",new String[]{origin,user})){if(c.moveToFirst())return c.getString(0);}return create(origin,user);}
    public void select(String id){SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();try{db.execSQL("UPDATE local_spaces SET active=0 WHERE active=1");ContentValues v=new ContentValues();v.put("active",1);if(db.update("local_spaces",v,"id=?",new String[]{id})!=1)throw new IllegalArgumentException("本地空间不存在");db.setTransactionSuccessful();}finally{db.endTransaction();}}
    public JSONObject info(String id){try(Cursor c=store.getReadableDatabase().rawQuery("SELECT origin,user_id,role FROM local_spaces WHERE id=?",new String[]{id})){if(!c.moveToFirst())throw new IllegalArgumentException("本地空间不存在");try{return new JSONObject().put("id",id).put("origin",c.getString(0)).put("userId",c.getString(1)).put("role",c.getString(2));}catch(Exception e){throw new IllegalStateException(e);}}}
    public String bind(String id,String origin,String user){if(user.isEmpty()||origin.isEmpty())throw new IllegalArgumentException("请先验证网络账号");JSONObject old=info(id);if(!old.optString("userId").isEmpty()&&(!user.equals(old.optString("userId"))||!origin.equals(old.optString("origin"))))throw new IllegalStateException("本地数据已属于其他账号，请切换空间");ContentValues v=new ContentValues();v.put("origin",origin);v.put("user_id",user);store.getWritableDatabase().update("local_spaces",v,"id=?",new String[]{id});return id;}
    public void role(String id,String role){if(!"USER".equals(role)&&!"MERCHANT".equals(role))throw new IllegalArgumentException("未知角色");ContentValues v=new ContentValues();v.put("role",role);store.getWritableDatabase().update("local_spaces",v,"id=?",new String[]{id});}
}
