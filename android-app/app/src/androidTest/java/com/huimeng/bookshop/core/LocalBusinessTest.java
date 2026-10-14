package com.huimeng.bookshop.core;
import android.test.AndroidTestCase;
import com.huimeng.bookshop.core.local.*;
import org.json.*;
@SuppressWarnings("deprecation")
public class LocalBusinessTest extends AndroidTestCase {
    private LocalDatabase db;private String file,space;
    @Override protected void setUp()throws Exception{super.setUp();file="business-"+java.util.UUID.randomUUID()+".db";db=new LocalDatabase(getContext(),file);space=new LocalSpaceStore(db).ensureActiveSpace();SampleCatalog.seed(db,space);}
    @Override protected void tearDown()throws Exception{db.close();getContext().deleteDatabase(file);super.tearDown();}
    public void testOfflineBuyerWritesSurviveReopen()throws Exception{
        BuyerRepository buyer=new BuyerRepository(db,space);
        JSONObject result=buyer.handle(1,"me/addresses",new JSONObject().put("recipient","小明").put("phone","13800138000").put("region","测试地区").put("detail","本地地址").put("isDefault",true));
        String id=result.getString("id");db.close();db=new LocalDatabase(getContext(),file);assertEquals("本地地址",db.entity(space,"addresses",id).getString("detail"));assertEquals(1,db.pending(space).length());
    }
    public void testOfflinePostCommentDependencies()throws Exception{
        CommunityRepository community=new CommunityRepository(db,space);
        String post=community.handle(1,"community/posts",new JSONObject().put("title","我的本地帖子").put("content","离线发布")).getString("id");
        community.handle(1,"community/posts/"+post+"/comments",new JSONObject().put("content","本地评论"));
        assertEquals(2,db.pending(space).length());assertEquals(1,community.handle(0,"community/posts/"+post,null).getJSONObject("data").getJSONArray("comments").length());
    }
    public void testLocalMerchantWithoutServerRole()throws Exception{
        MerchantRepository merchant=new MerchantRepository(db,space);
        JSONObject p=new JSONObject().put("name","本地绘本").put("summary","简介").put("categoryId","sample-category").put("ageRange","3–6岁").put("priceCents",2000).put("stock",5);
        String id=merchant.handle(1,"merchant/products",p).getString("productId");assertEquals("DRAFT",db.entity(space,"merchantProducts",id).getString("status"));assertEquals(1,db.pending(space).length());
    }
    public void testSampleOrdersNeverUpload()throws Exception{
        BuyerRepository buyer=new BuyerRepository(db,space);String address=buyer.handle(1,"me/addresses",new JSONObject().put("recipient","小明").put("phone","13800138000").put("region","测试地区").put("detail","本地地址")).getString("id");
        int before=db.pending(space).length();OrderRepository orders=new OrderRepository(db,space);
        String group=orders.handle(1,"checkout",new JSONObject().put("addressId",address).put("items",new JSONArray().put(new JSONObject().put("productId","sample-book").put("quantity",1)))).getString("checkoutGroupId");
        JSONObject pending=orders.handle(1,"checkout/"+group+"/pay",new JSONObject().put("method","DEMO_BALANCE"));assertTrue(pending.getBoolean("pending"));assertEquals(before,db.pending(space).length());assertEquals("PENDING",orders.handle(0,"checkout/"+group,null).getJSONObject("data").getString("status"));
    }
    public void testLocalCatalogMatchesAgeAndPagination()throws Exception{
        CatalogRepository catalog=new CatalogRepository(db,space);JSONObject result=catalog.handle(0,"catalog/products?age=3-6&page=1&pageSize=12",null);assertEquals(1,result.getInt("total"));assertEquals(1,result.getJSONArray("data").length());assertEquals(0,catalog.handle(0,"catalog/products?category=nonexistent&page=1",null).getJSONArray("data").length());
    }
    public void testRepeatedLikeDoesNotInflateLocalCount()throws Exception{
        CommunityRepository community=new CommunityRepository(db,space);String id=community.handle(1,"community/posts",new JSONObject().put("title","本地帖子").put("content","内容")).getString("id");
        community.handle(1,"community/posts/"+id+"/like",null);community.handle(1,"community/posts/"+id+"/like",null);assertEquals(1,db.entity(space,"posts",id).getInt("likeCount"));
        community.handle(3,"community/posts/"+id+"/like",null);community.handle(3,"community/posts/"+id+"/like",null);assertEquals(0,db.entity(space,"posts",id).getInt("likeCount"));
    }
    public void testDeletingDefaultAddressSelectsRemaining()throws Exception{
        BuyerRepository buyer=new BuyerRepository(db,space);JSONObject body=new JSONObject().put("recipient","本地").put("phone","13800138000").put("region","地区").put("detail","详细地址");String first=buyer.handle(1,"me/addresses",body).getString("id");body.remove("isDefault");String second=buyer.handle(1,"me/addresses",body).getString("id");buyer.handle(3,"me/addresses/"+first,null);assertTrue(db.entity(space,"addresses",second).getBoolean("isDefault"));
    }
}
