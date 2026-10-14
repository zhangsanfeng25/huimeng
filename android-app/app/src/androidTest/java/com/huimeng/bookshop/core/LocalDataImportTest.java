package com.huimeng.bookshop.core;
import android.test.AndroidTestCase;
import com.huimeng.bookshop.core.local.*;
import org.json.*;
@SuppressWarnings("deprecation")
public class LocalDataImportTest extends AndroidTestCase {
 private LocalDatabase db; private String file,space;
 @Override protected void setUp()throws Exception{super.setUp();file="import-"+java.util.UUID.randomUUID()+".db";db=new LocalDatabase(getContext(),file);space=new LocalSpaceStore(db).ensureActiveSpace();}
 @Override protected void tearDown()throws Exception{db.close();getContext().deleteDatabase(file);super.tearDown();}
 private JSONObject snapshot()throws Exception{return new JSONObject().put("format",1).put("userId","u1").put("resources",new JSONObject().put("products",new JSONArray().put(new JSONObject().put("id","p1").put("name","原有商品").put("version",1))).put("notifications",new JSONArray().put(new JSONObject().put("id","n1").put("title","原有消息"))));}
 public void testImportPersistsOriginalCatalogAndNotices()throws Exception{new LocalDataImporter(db).apply(space,snapshot(),"u1");db.close();assertEquals("原有商品",db.entity(space,"products","p1").getString("name"));assertEquals(1,db.list(space,"notifications").length());assertEquals(0,db.pending(space).length());}
 public void testImportCannotOverwritePendingEdit()throws Exception{db.mutate(space,"products","p1","product.save",new JSONObject().put("name","未上传修改"),false);new LocalDataImporter(db).apply(space,snapshot(),"u1");assertEquals("未上传修改",db.entity(space,"products","p1").getString("name"));assertEquals(1,db.pending(space).length());}
 public void testDifferentAccountIsRejected()throws Exception{try{new LocalDataImporter(db).apply(space,snapshot(),"other");fail("Do not import another account");}catch(IllegalArgumentException expected){}assertEquals(0,db.list(space,"products").length());}
 public void testActualCatalogRetiresOnlyUnusedSample()throws Exception{SampleCatalog.seed(db,space);new LocalDataImporter(db).apply(space,snapshot(),"u1");assertNull(db.entity(space,"products","sample-book"));assertEquals(1,db.list(space,"products").length());}
}
