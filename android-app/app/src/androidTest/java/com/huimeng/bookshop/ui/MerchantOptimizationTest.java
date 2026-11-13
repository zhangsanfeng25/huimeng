package com.huimeng.bookshop.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.SystemClock;
import android.widget.TextView;
import com.huimeng.bookshop.R;
import com.android.volley.Request;
import com.android.volley.NetworkResponse;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

/** Isolated UI transport: no business database or real merchant writes. */
public class MerchantOptimizationTest extends RefreshNavigationTest {
    private boolean merchantFixture;
    private volatile JSONObject saved; private volatile int stockUpdates;
    private volatile int submittedPrice=-1;
    private JSONObject draft;
    private boolean failSave,failSubmit;
    private int saleVersion; private volatile boolean extraNotice; private volatile boolean extraChat;
    private MainActivity merchant;
    private Activity opened;
    private com.android.volley.RequestQueue auditQueue,previousTransport;
    @Override protected String fixture(Request<?> request){
        if(!merchantFixture)return super.fixture(request);
        String url=request.getUrl();
        if(merchantFixture&&url.endsWith("merchant/products/MP2/stock")){try{JSONObject value=new JSONObject(new String(request.getBody(),StandardCharsets.UTF_8));if(value.getInt("expectedStock")!=10)throw new IllegalStateException("库存基准错误");draft.put("stock",value.getInt("stock"));stockUpdates++;return new JSONObject().put("ok",true).put("stock",value.getInt("stock")).toString();}catch(Exception e){throw new RuntimeException(e);}}
        try{
            if(url.endsWith("merchant/products")&&request.getMethod()==Request.Method.POST){saved=new JSONObject(new String(request.getBody(),StandardCharsets.UTF_8));draft=new JSONObject(saved.toString()).put("id","MP2").put("status","DRAFT");return "{\"ok\":true,\"productId\":\"MP2\"}";}
            if(url.endsWith("merchant/products"))return new JSONObject().put("data",new JSONArray()
                .put(product("MP1","星空百科","ONLINE","C1","适龄阅读"))
                .put(new JSONObject(draft.toString()).put("category","适龄阅读"))
                .put(product("MP3","森林邮递员","PENDING_REVIEW","C2","阅读主题"))
                .put(product("MP4","海洋绘本","OFFLINE","C2","阅读主题"))).toString();
            if(url.endsWith("merchant/orders"))return new JSONObject().put("data",new JSONArray()
                .put(order("MO1","PREPARING",0)).put(order("MO2","OUT_FOR_DELIVERY",0))
                .put(order("MO3","PREPARING",2980)).put(order("MO4","COMPLETED",0))).toString();
            if(url.endsWith("service/after-sales"))return new JSONObject().put("data",new JSONArray()
                .put(sale("AS1",saleVersion==0?"MERCHANT_REVIEW":"ADMIN_REVIEW")).put(sale("AS2","ADMIN_REVIEW"))
                .put(sale("AS3","RETURNING")).put(sale("AS4","COMPLETED"))).toString();
            if(extraChat&&url.endsWith("service/conversations"))return "{\"data\":[{\"id\":\"CV1\",\"buyer\":\"测试买家\",\"unread\":3}]}";
            if(url.endsWith("service/conversations"))return "{\"data\":[{\"id\":\"CV1\",\"buyer\":\"测试买家\",\"last_message\":\"请问什么时候发货\",\"unread\":2}]}";
            if(extraNotice&&url.endsWith("me/notifications"))return "{\"data\":[{\"id\":\"N1\",\"is_read\":0},{\"id\":\"N2\",\"is_read\":0}]}";
            if(url.endsWith("me/notifications"))return "{\"data\":[{\"id\":\"N1\",\"title\":\"新订单待发货\",\"content\":\"订单MO1待处理\",\"is_read\":0}]}";
            if(url.endsWith("catalog/categories"))return "{\"data\":[{\"id\":\"C1\",\"name\":\"适龄阅读\"}]}";
            if(url.endsWith("merchant/products/MP2/submit")){
                submittedPrice=draft.optInt("priceCents");draft.put("status","PENDING_REVIEW");return "{\"ok\":true}";
            }
            if(url.endsWith("merchant/products/MP2")){
                if(request.getMethod()==Request.Method.PUT){SystemClock.sleep(200);saved=new JSONObject(new String(request.getBody(),StandardCharsets.UTF_8));draft=new JSONObject(saved.toString()).put("id","MP2").put("status","DRAFT");return "{\"ok\":true,\"productId\":\"MP2\"}";}
                return new JSONObject().put("data",draft).toString();
            }
        }catch(Exception e){throw new RuntimeException(e);}
        return super.fixture(request);
    }
    private JSONObject product(String id,String name,String status,String category,String categoryName)throws Exception{return new JSONObject().put("id",id).put("name",name).put("status",status).put("categoryId",category).put("category",categoryName).put("priceCents",2980).put("stock",10);}
    private JSONObject order(String id,String logistics,int refund)throws Exception{return new JSONObject().put("id",id).put("productName","星空百科").put("buyer","测试买家").put("status",logistics.equals("PREPARING")?"PAID":"SHIPPED").put("logistics_status",logistics).put("total_cents",2980).put("refunded_cents",refund);}
    private JSONObject sale(String id,String state)throws Exception{return new JSONObject().put("id",id).put("reason",id+" 售后原因").put("order_id","MO1").put("status",state).put("amount_cents",1000);}
    private interface Condition{boolean ready();}
    private void await(Condition condition)throws Exception{long end=SystemClock.uptimeMillis()+6000;while(SystemClock.uptimeMillis()<end){boolean[] ready={false};getInstrumentation().runOnMainSync(()->ready[0]=condition.ready());if(ready[0])return;SystemClock.sleep(80);}fail("目标界面未在限定时间内完成");}
    private void capture(String name)throws Exception{getInstrumentation().waitForIdleSync();SystemClock.sleep(250);android.graphics.Bitmap bitmap=getInstrumentation().getUiAutomation().takeScreenshot();try(var stream=new java.io.FileOutputStream(new java.io.File(getInstrumentation().getTargetContext().getExternalFilesDir(null),name+".png"))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream);}finally{bitmap.recycle();}}
    private String text(Activity page,int id){return ((TextView)view(page,id)).getText().toString();}
    private android.view.View view(Activity page,int id){if(page==merchant){android.view.View root=merchant.getSupportFragmentManager().getPrimaryNavigationFragment().getView();if(root!=null){android.view.View found=root.findViewById(id);if(found!=null)return found;}}return page.findViewById(id);}
    private int rows(){return ((androidx.recyclerview.widget.RecyclerView)merchant.getSupportFragmentManager().getPrimaryNavigationFragment().requireView().findViewById(R.id.list)).getAdapter().getItemCount();}
    private void prepare()throws Exception{
        merchantFixture=true;draft=new JSONObject().put("id","MP2").put("name","原草稿").put("summary","原有简介").put("ageRange","3–6岁").put("categoryId","C1").put("stock",10).put("priceCents",2980).put("status","DRAFT");
        com.huimeng.bookshop.HuiMengApp app=(com.huimeng.bookshop.HuiMengApp)getInstrumentation().getTargetContext().getApplicationContext();
        java.lang.reflect.Field requests=com.huimeng.bookshop.HuiMengApp.class.getDeclaredField("requestQueue");requests.setAccessible(true);previousTransport=(com.android.volley.RequestQueue)requests.get(app);
        auditQueue=new com.android.volley.RequestQueue(new com.android.volley.toolbox.NoCache(),request->{
            boolean save=request.getMethod()==Request.Method.PUT&&request.getUrl().endsWith("merchant/products/MP2"),submit=request.getUrl().endsWith("merchant/products/MP2/submit");
            if((failSave&&save)||(failSubmit&&submit))throw new com.android.volley.ClientError(new NetworkResponse(409,(failSave?"{\"message\":\"模拟保存失败\"}":"{\"message\":\"模拟送审失败\"}").getBytes(StandardCharsets.UTF_8),false,0,Collections.emptyList()));
            return new NetworkResponse(200,fixture(request).getBytes(StandardCharsets.UTF_8),false,0,Collections.emptyList());
        });auditQueue.start();requests.set(app,auditQueue);
    }
    private void openMerchant()throws Exception{
        prepare();activity.getSharedPreferences("huimeng_session",0).edit().putString("role","MERCHANT").commit();
        merchant=(MainActivity)getInstrumentation().startActivitySync(new Intent(activity,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK));getInstrumentation().waitForIdleSync();
    }
    public void testMerchantMessageIsPrimaryNavigation()throws Exception{
        openMerchant();
        com.google.android.material.bottomnavigation.BottomNavigationView nav=(com.google.android.material.bottomnavigation.BottomNavigationView)view(merchant,R.id.bottomNav);
        assertEquals("消息",nav.getMenu().getItem(3).getTitle().toString());
        getInstrumentation().runOnMainSync(()->merchant.selectTab(4));
        assertTrue(merchant.getSupportFragmentManager().getPrimaryNavigationFragment() instanceof NotificationFragment);
    }
    public void testMerchantMessageRefreshUpdatesBothUnreadChannels()throws Exception{
        openMerchant();getInstrumentation().runOnMainSync(()->merchant.selectTab(4));
        await(()->((com.google.android.material.bottomnavigation.BottomNavigationView)view(merchant,R.id.bottomNav)).getBadge(R.id.nav_fourth)!=null&&((com.google.android.material.bottomnavigation.BottomNavigationView)view(merchant,R.id.bottomNav)).getBadge(R.id.nav_fourth).getNumber()==3);
        extraNotice=true;getInstrumentation().runOnMainSync(()->((NotificationFragment)merchant.getSupportFragmentManager().getPrimaryNavigationFragment()).refreshLocalData());
        await(()->((com.google.android.material.bottomnavigation.BottomNavigationView)view(merchant,R.id.bottomNav)).getBadge(R.id.nav_fourth).getNumber()==4);
        getInstrumentation().runOnMainSync(()->view(merchant,R.id.systemNotices).performClick());extraChat=true;getInstrumentation().runOnMainSync(()->((NotificationFragment)merchant.getSupportFragmentManager().getPrimaryNavigationFragment()).refreshLocalData());
        await(()->((com.google.android.material.bottomnavigation.BottomNavigationView)view(merchant,R.id.bottomNav)).getBadge(R.id.nav_fourth).getNumber()==5);
    }
    public void testMerchantSubmitIncludesUnsavedChanges()throws Exception{
        prepare();opened=getInstrumentation().startActivitySync(new Intent(activity,ManageProductActivity.class).putExtra("product_id","MP2").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        long end=SystemClock.uptimeMillis()+5000;while(!opened.findViewById(R.id.save).isEnabled()&&SystemClock.uptimeMillis()<end)SystemClock.sleep(80);
        getInstrumentation().runOnMainSync(()->{((TextView)opened.findViewById(R.id.name)).setText("当前输入的新绘本");((TextView)opened.findViewById(R.id.price)).setText("39.80");opened.findViewById(R.id.submitReview).performClick();});
        end=SystemClock.uptimeMillis()+6000;while(submittedPrice<0&&SystemClock.uptimeMillis()<end)SystemClock.sleep(80);
        assertEquals("送审必须先保存当前价格",3980,submittedPrice);assertNotNull(saved);assertEquals("当前输入的新绘本",saved.optString("name"));
        await(()->!opened.findViewById(R.id.name).isEnabled());capture("merchant-review-readonly");
    }
    public void testMerchantWorkbenchCountsAndDirectFilters()throws Exception{
        openMerchant();await(()->text(merchant,R.id.todoShip).startsWith("1\n")&&text(merchant,R.id.todoSale).startsWith("2\n")&&text(merchant,R.id.todoMessages).startsWith("3\n"));assertEquals(3,((com.google.android.material.bottomnavigation.BottomNavigationView)view(merchant,R.id.bottomNav)).getBadge(R.id.nav_fourth).getNumber());capture("merchant-workbench");
        getInstrumentation().runOnMainSync(()->view(merchant,R.id.todoShip).performClick());await(()->rows()==1);assertTrue(text(merchant,R.id.resultCount).contains("1 / 4"));capture("merchant-orders-filtered");
        getInstrumentation().runOnMainSync(()->{merchant.selectTab(1);view(merchant,R.id.todoProducts).performClick();});await(()->rows()==1);assertEquals("待修改 / 提交",((android.widget.Spinner)view(merchant,R.id.statusFilter)).getSelectedItem().toString());
        saleVersion=1;getInstrumentation().runOnMainSync(()->merchant.selectTab(1));await(()->text(merchant,R.id.todoSale).startsWith("1\n"));
        android.app.Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(ServiceActivity.class.getName(),null,false);getInstrumentation().runOnMainSync(()->view(merchant,R.id.todoSale).performClick());opened=getInstrumentation().waitForMonitorWithTimeout(monitor,4000);getInstrumentation().removeMonitor(monitor);assertNotNull(opened);assertEquals("待我处理",opened.getIntent().getStringExtra("filter"));capture("merchant-after-sales");
    }
    public void testMerchantProductSearchCategoryAndTabState()throws Exception{
        openMerchant();getInstrumentation().runOnMainSync(()->merchant.selectTab(2));await(()->rows()==4);
        getInstrumentation().runOnMainSync(()->((TextView)view(merchant,R.id.searchQuery)).setText("MP2"));await(()->rows()==1);
        getInstrumentation().runOnMainSync(()->{merchant.selectTab(3);merchant.selectTab(2);});assertEquals("MP2",text(merchant,R.id.searchQuery));assertEquals(1,rows());
        getInstrumentation().runOnMainSync(()->{view(merchant,R.id.clearFilters).performClick();((android.widget.Spinner)view(merchant,R.id.categoryFilter)).setSelection(2);});await(()->rows()==2);capture("merchant-products-category");
        getInstrumentation().runOnMainSync(()->view(merchant,R.id.clearFilters).performClick());await(()->rows()==4);
        getInstrumentation().runOnMainSync(()->((TextView)view(merchant,R.id.searchQuery)).setText("不存在的商品"));await(()->rows()==0);assertTrue(text(merchant,R.id.empty).contains("重置"));
    }
    public void testMerchantOrderSearchAndAfterSaleFilter()throws Exception{
        openMerchant();getInstrumentation().runOnMainSync(()->merchant.selectTab(3));await(()->rows()==4);
        getInstrumentation().runOnMainSync(()->((TextView)view(merchant,R.id.searchQuery)).setText("mo2"));await(()->rows()==1);
        getInstrumentation().runOnMainSync(()->{view(merchant,R.id.clearFilters).performClick();((android.widget.Spinner)view(merchant,R.id.statusFilter)).setSelection(4);});await(()->rows()==1);capture("merchant-orders-refund");
    }
    public void testMerchantFailedSaveDoesNotSubmit()throws Exception{
        prepare();failSave=true;opened=getInstrumentation().startActivitySync(new Intent(activity,ManageProductActivity.class).putExtra("product_id","MP2").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));await(()->opened.findViewById(R.id.save).isEnabled());
        getInstrumentation().runOnMainSync(()->opened.findViewById(R.id.submitReview).performClick());await(()->text(opened,R.id.editorNotice).contains("模拟保存失败"));assertEquals(-1,submittedPrice);assertNull(saved);assertTrue(opened.findViewById(R.id.submitReview).isEnabled());
    }
    public void testMerchantFailedSubmitKeepsSavedDraftAndAllowsRetry()throws Exception{
        prepare();failSubmit=true;opened=getInstrumentation().startActivitySync(new Intent(activity,ManageProductActivity.class).putExtra("product_id","MP2").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));await(()->opened.findViewById(R.id.save).isEnabled());
        getInstrumentation().runOnMainSync(()->{((TextView)opened.findViewById(R.id.price)).setText("39.80");opened.findViewById(R.id.submitReview).performClick();});await(()->text(opened,R.id.editorNotice).contains("草稿已保存，送审未完成"));assertNotNull(saved);assertEquals(-1,submittedPrice);assertEquals("39.80",text(opened,R.id.price));failSubmit=false;
        getInstrumentation().runOnMainSync(()->opened.findViewById(R.id.submitReview).performClick());await(()->submittedPrice==3980);
    }
    public void testMerchantCommunityRemainsReachableFromProfile()throws Exception{
        openMerchant();getInstrumentation().runOnMainSync(()->merchant.selectTab(5));assertTrue(text(merchant,R.id.publish).contains("内容运营"));capture("merchant-profile");
        android.app.Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(MerchantCommunityActivity.class.getName(),null,false);getInstrumentation().runOnMainSync(()->view(merchant,R.id.publish).performClick());opened=getInstrumentation().waitForMonitorWithTimeout(monitor,4000);getInstrumentation().removeMonitor(monitor);assertNotNull(opened);await(()->opened.findViewById(R.id.publish)!=null&&opened.hasWindowFocus());SystemClock.sleep(700);capture("merchant-content-operations");
    }
    public void testMerchantCreateCanSaveAndSubmitInOneAction()throws Exception{
        prepare();opened=getInstrumentation().startActivitySync(new Intent(activity,AddProductActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));await(()->((android.widget.Spinner)opened.findViewById(R.id.category)).getCount()>0);
        getInstrumentation().runOnMainSync(()->{((TextView)opened.findViewById(R.id.name)).setText("新增星空绘本");((TextView)opened.findViewById(R.id.summary)).setText("新的商品简介");((TextView)opened.findViewById(R.id.age)).setText("3–6岁");((TextView)opened.findViewById(R.id.price)).setText("39.80");((TextView)opened.findViewById(R.id.stock)).setText("10");opened.findViewById(R.id.submitReview).performClick();});await(()->submittedPrice==3980);assertEquals("新增星空绘本",saved.optString("name"));
    }

    public void testMerchantFormActionsStayVisibleAndOptionalMediaFolds()throws Exception{
        prepare();opened=getInstrumentation().startActivitySync(new Intent(activity,AddProductActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        await(()->((android.widget.Spinner)opened.findViewById(R.id.category)).getCount()>0);
        int footerId=opened.getResources().getIdentifier("formActions","id",opened.getPackageName());
        assertTrue("表单底部应有独立操作栏",footerId!=0);
        android.view.View footer=opened.findViewById(footerId);assertTrue(footer.getParent() instanceof android.widget.LinearLayout);
        assertFalse("保存操作不能跟随长表单滚动",footer.getParent() instanceof android.widget.ScrollView);
        int mediaId=opened.getResources().getIdentifier("mediaFields","id",opened.getPackageName());
        assertEquals(android.view.View.GONE,opened.findViewById(mediaId).getVisibility());
        getInstrumentation().runOnMainSync(()->opened.findViewById(R.id.productMediaTitle).performClick());
        assertEquals(android.view.View.VISIBLE,opened.findViewById(mediaId).getVisibility());
        capture("merchant-form-fixed-actions");
    }
    public void testMerchantInventoryUpdateDoesNotSaveOrSubmitProduct()throws Exception{
        prepare();draft.put("status","ONLINE");
        opened=getInstrumentation().startActivitySync(new Intent(activity,ManageProductActivity.class).putExtra("product_id","MP2").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        await(()->opened.findViewById(R.id.save).isEnabled());
        int stockId=opened.getResources().getIdentifier("stockQuick","id",opened.getPackageName());
        getInstrumentation().runOnMainSync(()->opened.findViewById(stockId).performClick());
        android.view.accessibility.AccessibilityNodeInfo input=null;
        long until=SystemClock.uptimeMillis()+6000;
        while(input==null&&SystemClock.uptimeMillis()<until){input=findEditable(getInstrumentation().getUiAutomation().getRootInActiveWindow());SystemClock.sleep(100);}
        assertNotNull("调整库存应显示输入框",input);
        android.os.Bundle args=new android.os.Bundle();args.putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"25");
        assertTrue(input.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT,args));
        var root=getInstrumentation().getUiAutomation().getRootInActiveWindow();
        var buttons=root.findAccessibilityNodeInfosByText("确认调整");assertFalse(buttons.isEmpty());assertTrue(buttons.get(0).performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK));
        await(()->stockUpdates==1&&text(opened,R.id.stock).equals("25"));
        assertNull("独立库存不能调用完整商品保存",saved);assertEquals(-1,submittedPrice);assertEquals("ONLINE",draft.optString("status"));capture("merchant-stock-updated");
    }
    private android.view.accessibility.AccessibilityNodeInfo findEditable(android.view.accessibility.AccessibilityNodeInfo node){
        if(node==null)return null;if(node.isEditable())return node;
        for(int i=0;i<node.getChildCount();i++){var found=findEditable(node.getChild(i));if(found!=null)return found;}return null;
    }
    @Override protected void tearDown()throws Exception{
        if(opened!=null)getInstrumentation().runOnMainSync(opened::finish);
        if(merchant!=null)getInstrumentation().runOnMainSync(merchant::finish);
        if(auditQueue!=null){auditQueue.stop();java.lang.reflect.Field requests=com.huimeng.bookshop.HuiMengApp.class.getDeclaredField("requestQueue");requests.setAccessible(true);requests.set(getInstrumentation().getTargetContext().getApplicationContext(),previousTransport);}
        super.tearDown();
    }
}
