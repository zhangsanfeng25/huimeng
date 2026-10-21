package com.huimeng.bookshop.core;
import android.test.AndroidTestCase;
import com.huimeng.bookshop.core.local.*;
import com.huimeng.bookshop.model.BookDetails;
import org.json.*;
public class BookDataTest extends AndroidTestCase {
    private LocalDatabase db;private String file,space;
    @Override protected void setUp()throws Exception{super.setUp();file="book-data-"+java.util.UUID.randomUUID()+".db";db=new LocalDatabase(getContext(),file);space=new LocalSpaceStore(db).ensureActiveSpace();}
    @Override protected void tearDown()throws Exception{db.close();getContext().deleteDatabase(file);super.tearDown();}
    private JSONObject product(String id,String name)throws Exception{return new JSONObject().put("id",id).put("name",name).put("summary",name+"，适合亲子共读的温暖绘本。").put("stock",20).put("priceCents",2980).put("status","ONLINE").put("categoryId","C01").put("ageRange","3–6岁");}
    public void testUpgradeIsIncrementalAndKeepsPendingWrites()throws Exception{
        db.saveConfirmed(space,"products","P0001",product("P0001","晚安，小熊"),7);
        JSONObject custom=product("P0002","森林里的邮递员").put("summary","人工维护的简介");db.saveConfirmed(space,"products","P0002",custom,8);
        JSONObject pending=product("P0003","星空百科");db.saveConfirmed(space,"products","P0003",pending,9);db.mutate(space,"merchantProducts","P0003","product.save",pending,false);
        db.saveConfirmed(space,"addresses","keep",new JSONObject().put("detail","原地址"),1);
        assertEquals(2,LocalBookUpgrade.apply(getContext(),db,space));
        JSONObject book=db.entity(space,"products","P0001");assertEquals("林禾",book.getString("authorName"));assertTrue(book.getString("summary").length()>=150);assertEquals(7,book.getInt("version"));assertEquals(2980,book.getInt("priceCents"));
        assertEquals("人工维护的简介",db.entity(space,"products","P0002").getString("summary"));assertFalse(db.entity(space,"products","P0003").has("authorName"));assertEquals(1,db.pending(space).length());assertEquals("原地址",db.entity(space,"addresses","keep").getString("detail"));assertEquals(0,LocalBookUpgrade.apply(getContext(),db,space));
    }
    public void testNewInstallHasTwentyOfflineBooksAndSameRecommendations()throws Exception{
        LocalBootstrap.prepare(getContext(),db,space);JSONArray books=db.list(space,"products");assertEquals(20,books.length());
        for(int i=0;i<books.length();i++){BookDetails details=new BookDetails(books.getJSONObject(i));assertFalse(details.text("authorName").isEmpty());assertTrue(details.chapters().length()>=5);}
        JSONObject current=new CatalogRepository(db,space).handle(0,"catalog/products/P0001",null).getJSONObject("data");JSONArray similar=current.getJSONArray("recommendations");assertEquals(4,similar.length());java.util.Set<String> seen=new java.util.HashSet<>();for(int i=0;i<similar.length();i++){JSONObject row=similar.getJSONObject(i);assertFalse("P0001".equals(row.getString("id")));assertTrue(seen.add(row.getString("id")));assertTrue(row.getInt("stock")>0);}
        assertEquals("P0005",similar.getJSONObject(0).getString("id"));
    }
    public void testMerchantOmittedFieldsPreservedAndExplicitClearWorks()throws Exception{
        MerchantRepository merchant=new MerchantRepository(db,space);JSONObject body=new JSONObject().put("name","虚构绘本").put("summary","简介").put("categoryId","C01").put("ageRange","3–6岁").put("stock",5).put("priceCents",2000).put("authorName","原作者").put("tableOfContents",new JSONArray().put("开篇"));
        String id=merchant.handle(1,"merchant/products",body).getString("productId");body.remove("authorName");body.remove("tableOfContents");merchant.handle(2,"merchant/products/"+id,body);assertEquals("原作者",db.entity(space,"merchantProducts",id).getString("authorName"));body.put("authorName","").put("tableOfContents",new JSONArray());merchant.handle(2,"merchant/products/"+id,body);assertEquals("",db.entity(space,"merchantProducts",id).getString("authorName"));assertEquals(0,db.entity(space,"merchantProducts",id).getJSONArray("tableOfContents").length());
        try{BookDetails.validate(new JSONObject().put("publicationDate","2025-02-30"));fail("Invalid date must fail before outbox write");}catch(IllegalArgumentException expected){}
    }
}
