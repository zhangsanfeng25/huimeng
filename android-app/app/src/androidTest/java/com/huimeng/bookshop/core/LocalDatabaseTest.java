package com.huimeng.bookshop.core;
import android.test.AndroidTestCase;
import com.huimeng.bookshop.core.local.LocalDatabase;
import com.huimeng.bookshop.core.local.LocalSpaceStore;
import org.json.JSONObject;
@SuppressWarnings("deprecation")
public class LocalDatabaseTest extends AndroidTestCase {
    private LocalDatabase store; private String file;
    @Override protected void setUp() throws Exception {super.setUp();file="local-test-"+java.util.UUID.randomUUID()+".db";store=new LocalDatabase(getContext(),file);}
    @Override protected void tearDown() throws Exception {store.close();getContext().deleteDatabase(file);super.tearDown();}
    private String space(){return new LocalSpaceStore(store).ensureActiveSpace();}
    public void testMutationAndQueueAreAtomic() throws Exception {
        String space=space();store.getWritableDatabase().execSQL("CREATE TRIGGER fail_outbox BEFORE INSERT ON sync_operations BEGIN SELECT RAISE(ABORT,'outbox unavailable'); END");
        try{store.mutate(space,"addresses","a1","address.save",new JSONObject().put("recipient","小明"),false);fail("Must reject storage failure");}catch(android.database.SQLException expected){}
        assertNull(store.entity(space,"addresses","a1"));assertEquals(0,store.pending(space).length());
    }
    public void testReopenPreservesPending() throws Exception {
        String space=space();String op=store.mutate(space,"addresses","a1","address.save",new JSONObject().put("recipient","小明"),false);
        store.close();store=new LocalDatabase(getContext(),file);assertEquals(op,store.pending(space).getJSONObject(0).getString("operationId"));assertEquals("小明",store.entity(space,"addresses","a1").getString("recipient"));
    }
    public void testSpaceAndOriginIsolation() throws Exception {
        LocalSpaceStore spaces=new LocalSpaceStore(store);String a=spaces.ensureActiveSpace();spaces.bind(a,"http://a/api/v1/","u1");store.mutate(a,"addresses","a1","address.save",new JSONObject().put("recipient","private"),false);
        String b=spaces.forAccount("http://a/api/v1/","u2");assertFalse(a.equals(b));assertNull(store.entity(b,"addresses","a1"));assertFalse(a.equals(spaces.forAccount("http://b/api/v1/","u1")));
        try{spaces.bind(a,"http://a/api/v1/","u2");fail("Cannot rebind owned data");}catch(IllegalStateException expected){}
    }
    public void testSampleNeverEnqueues() throws Exception {String space=space();store.saveConfirmed(space,"products","sample-1",new JSONObject().put("sample",true),0);store.mutate(space,"orders","sample-order","checkout.create",new JSONObject().put("sample",true),true);assertEquals(0,store.pending(space).length());assertNotNull(store.entity(space,"orders","sample-order"));}
    public void testUpgradeFailureKeepsOldData() throws Exception {String space=space();store.saveConfirmed(space,"addresses","old",new JSONObject().put("recipient","original"),0);try{store.onUpgrade(store.getWritableDatabase(),1,99);fail("Unknown upgrade must fail safely");}catch(IllegalStateException expected){}assertEquals("original",store.entity(space,"addresses","old").getString("recipient"));}
    public void testLegacyCartMigrationOnce()throws Exception{String space=space();org.json.JSONArray legacy=new org.json.JSONArray("[null,{}, {\"id\":\"old\",\"quantity\":3}]");store.importLegacyCart("test",space,legacy);store.mutate(space,"cart","old","cart.set",new JSONObject().put("id","old").put("quantity",5),false);store.importLegacyCart("test",space,legacy);assertEquals(5,store.entity(space,"cart","old").getInt("quantity"));assertEquals(1,store.list(space,"cart").length());}
    public void testBindingMergesIntoExistingAccountSpace()throws Exception{LocalSpaceStore spaces=new LocalSpaceStore(store);String local=spaces.ensureActiveSpace(),account=spaces.forAccount("http://service/","u1");store.mutate(local,"posts","draft","post.create",new JSONObject().put("title","preserve"),false);assertEquals(account,spaces.bind(local,"http://service/","u1"));assertNotNull(store.entity(account,"posts","draft"));assertEquals(1,store.pending(account).length());}
}
