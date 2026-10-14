package com.huimeng.bookshop.core;
import android.test.AndroidTestCase;
import com.huimeng.bookshop.core.local.*;
import com.huimeng.bookshop.core.sync.*;
import org.json.*;
@SuppressWarnings("deprecation")
public class LocalSyncTest extends AndroidTestCase {
 private LocalDatabase db;private String file,space;
 @Override protected void setUp()throws Exception{super.setUp();file="sync-"+java.util.UUID.randomUUID()+".db";db=new LocalDatabase(getContext(),file);space=new LocalSpaceStore(db).ensureActiveSpace();}
 @Override protected void tearDown()throws Exception{db.close();getContext().deleteDatabase(file);super.tearDown();}
 public void testIncompleteSnapshotDoesNotDelete()throws Exception{db.saveConfirmed(space,"addresses","old",new JSONObject().put("id","old").put("detail","retained"),1);SnapshotImporter importer=new SnapshotImporter(db);importer.applyPage(space,"addresses",new JSONObject().put("snapshotId","s1").put("offset",0).put("complete",false).put("items",new JSONArray()));assertNotNull(db.entity(space,"addresses","old"));}
 public void testPendingEditSurvivesPull()throws Exception{db.saveConfirmed(space,"addresses","a",new JSONObject().put("id","a").put("detail","before"),1);db.mutate(space,"addresses","a","address.save",new JSONObject().put("id","a").put("detail","local"),false);new SnapshotImporter(db).applyPage(space,"addresses",new JSONObject().put("snapshotId","s1").put("offset",0).put("complete",true).put("items",new JSONArray().put(new JSONObject().put("id","a").put("detail","server").put("version",2))));assertEquals("local",db.entity(space,"addresses","a").getString("detail"));assertEquals(1,db.pending(space).getJSONObject(0).getLong("baseVersion"));}
 public void testAcknowledgementPreservesNewerEdit()throws Exception{String first=db.mutate(space,"addresses","a","address.save",new JSONObject().put("detail","first"),false);db.mutate(space,"addresses","a","address.save",new JSONObject().put("detail","second"),false);db.acknowledge(db.pending(space).getJSONObject(0),new JSONObject().put("operationId",first).put("entityId","ADDR1").put("version",1).put("data",new JSONObject().put("id","ADDR1")));assertEquals("second",db.entity(space,"addresses","a").getString("detail"));assertEquals(1,db.pending(space).length());assertEquals(1,db.pending(space).getJSONObject(0).getLong("baseVersion"));}
 public void testRetrySchedule(){assertTrue(SyncCoordinator.retryDelay(1,0)>=2000);assertTrue(SyncCoordinator.retryDelay(5,0)>=60000);assertTrue(SyncCoordinator.retryDelay(20,0)<=300000);assertEquals(45000,SyncCoordinator.retryDelay(1,45000));}
 public void testCanonicalHashVector()throws Exception{assertEquals("{\"a\":\"绘梦\",\"b\":[1,true]}",SyncTransport.canonical(new JSONObject().put("b",new JSONArray().put(1).put(true)).put("a","绘梦")));}
}
