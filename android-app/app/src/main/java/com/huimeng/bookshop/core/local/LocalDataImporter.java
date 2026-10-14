package com.huimeng.bookshop.core.local;

import android.database.sqlite.SQLiteDatabase;
import com.huimeng.bookshop.core.sync.SnapshotImporter;
import org.json.*;

/** Imports scoped snapshots without replacing pending local edits or copying credentials. */
public final class LocalDataImporter {
    private final LocalDatabase db;
    public LocalDataImporter(LocalDatabase db){this.db=db;}
    public void apply(String space,JSONObject snapshot,String expectedUser)throws Exception{
        if(snapshot.optInt("format")!=1||!expectedUser.equals(snapshot.optString("userId")))throw new IllegalArgumentException("迁移文件不属于当前账号");
        JSONObject resources=snapshot.getJSONObject("resources");
        SQLiteDatabase sql=db.getWritableDatabase();sql.beginTransaction();try{
            java.util.Iterator<String> names=resources.keys();SnapshotImporter importer=new SnapshotImporter(db);
            while(names.hasNext()){String resource=names.next();
                if(!java.util.Arrays.asList("categories","products","profile","addresses","cart","favorites","posts","comments","likes","notifications","merchantApplication","merchantProducts","checkouts","buyerOrders","merchantOrders","productRatings","ownRatings").contains(resource))throw new IllegalArgumentException("未知迁移资源");
                JSONArray rows=resources.getJSONArray(resource);
                importer.applyPage(space,resource,new JSONObject().put("snapshotId","import-"+java.util.UUID.randomUUID()).put("offset",0).put("complete",true).put("items",rows));
            }
            JSONArray products=resources.optJSONArray("products");
            if(products!=null&&products.length()>0)sql.execSQL("UPDATE local_entities SET deleted=1 WHERE space_id=? AND resource IN ('products','categories') AND sample=1 AND NOT EXISTS(SELECT 1 FROM sync_operations o WHERE o.space_id=local_entities.space_id AND o.resource=local_entities.resource AND o.entity_id=local_entities.id AND o.state<>'ACKED')",new Object[]{space});
            db.metadata("data-import:"+space,snapshot.optString("revision","manual"));sql.setTransactionSuccessful();
        }finally{sql.endTransaction();}
    }
}
