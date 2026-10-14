package com.huimeng.bookshop.core.sync;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.huimeng.bookshop.core.local.LocalDatabase;
import org.json.*;
import java.util.HashSet;
import java.util.Set;
public final class SnapshotImporter {
 private final LocalDatabase store;
 public SnapshotImporter(LocalDatabase store){this.store=store;}
 public void applyPage(String space,String resource,JSONObject page)throws Exception{
  SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();try{
   String snapshot=page.getString("snapshotId");int offset=page.optInt("offset");JSONArray items=page.getJSONArray("items");
   if(offset==0)db.delete("snapshot_pages","space_id=? AND resource=?",new String[]{space,resource});
   ContentValues row=new ContentValues();row.put("space_id",space);row.put("resource",resource);row.put("snapshot_id",snapshot);row.put("offset",offset);row.put("data",items.toString());db.insertWithOnConflict("snapshot_pages",null,row,SQLiteDatabase.CONFLICT_REPLACE);
   if(page.optBoolean("complete")){
    Set<String> present=new HashSet<>();int expected=0;
    try(Cursor cursor=db.rawQuery("SELECT offset,data FROM snapshot_pages WHERE space_id=? AND resource=? AND snapshot_id=? ORDER BY offset",new String[]{space,resource,snapshot})){
     while(cursor.moveToNext()){if(cursor.getInt(0)!=expected)throw new IllegalStateException("快照分页不完整，已保留原数据");JSONArray values=new JSONArray(cursor.getString(1));expected+=values.length();
      for(int i=0;i<values.length();i++){JSONObject value=values.getJSONObject(i);String remote=value.getString("id"),id=remote;
       if(resource.equals("profile")||resource.equals("merchantApplication"))id="self";
       else try(Cursor alias=db.rawQuery("SELECT id FROM local_entities WHERE space_id=? AND resource=? AND remote_id=?",new String[]{space,resource,remote})){if(alias.moveToFirst())id=alias.getString(0);}
       present.add(id);value.put("remoteId",remote).put("syncState","CONFIRMED");if(!resource.equals("profile")&&!resource.equals("merchantApplication"))value.put("id",id);
       if(resource.equals("posts")){if(value.has("images")&&!value.has("images_json"))value.put("images_json",value.opt("images"));}
       store.saveConfirmed(space,resource,id,value,value.optLong("version"));
       db.execSQL("UPDATE local_entities SET remote_id=?,deleted=CASE WHEN EXISTS(SELECT 1 FROM sync_operations o WHERE o.space_id=local_entities.space_id AND o.resource=local_entities.resource AND o.entity_id=local_entities.id AND o.state<>'ACKED') THEN deleted ELSE 0 END WHERE space_id=? AND resource=? AND id=?",new Object[]{remote,space,resource,id});
      }
     }
    }
    try(Cursor existing=db.rawQuery("SELECT id FROM local_entities e WHERE space_id=? AND resource=? AND sample=0 AND NOT EXISTS(SELECT 1 FROM sync_operations o WHERE o.space_id=e.space_id AND o.resource=e.resource AND o.entity_id=e.id AND o.state<>'ACKED')",new String[]{space,resource})){while(existing.moveToNext()){String id=existing.getString(0);if(!present.contains(id))db.execSQL("UPDATE local_entities SET deleted=1 WHERE space_id=? AND resource=? AND id=?",new Object[]{space,resource,id});}}
    db.delete("snapshot_pages","space_id=? AND resource=?",new String[]{space,resource});
   }
   db.setTransactionSuccessful();
  }finally{db.endTransaction();}
 }
}
