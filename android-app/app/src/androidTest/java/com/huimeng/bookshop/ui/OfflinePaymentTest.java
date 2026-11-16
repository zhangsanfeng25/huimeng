package com.huimeng.bookshop.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.SystemClock;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.CheckBox;
import android.view.View;
import android.view.ViewGroup;
import com.android.volley.*;
import com.android.volley.toolbox.NoCache;
import com.huimeng.bookshop.BuildConfig;
import com.huimeng.bookshop.HuiMengApp;
import com.huimeng.bookshop.R;
import com.huimeng.bookshop.core.SessionStore;
import com.huimeng.bookshop.core.local.*;
import com.huimeng.bookshop.core.sync.SyncCoordinator;
import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;
import org.json.JSONArray;

/** Real SQLite and Activity, disconnected transport, isolated app/database. */
public class OfflinePaymentTest extends RefreshNavigationTest {
    private LocalDatabase db,previous;private RequestQueue disconnected;private Activity payment;private String space;
    @Override protected void setUp()throws Exception{
        super.setUp();var context=getInstrumentation().getTargetContext();
        Field instance=LocalDatabase.class.getDeclaredField("instance");instance.setAccessible(true);previous=(LocalDatabase)instance.get(null);
        db=new LocalDatabase(context,"payment-"+java.util.UUID.randomUUID()+".db");instance.set(null,db);
        space=new LocalSpaceStore(db).forAccount(BuildConfig.API_BASE_URL,"device-test");
        db.saveConfirmed(space,"checkouts","offline-checkout",new JSONObject().put("id","offline-checkout").put("status","PENDING").put("totalCents",2980),1);
        db.saveConfirmed(space,"profile","self",new JSONObject().put("id","device-test").put("username","111").put("name","1"),0);
        db.saveConfirmed(space,"buyerOrders","local-purchase",new JSONObject().put("id","local-purchase")
            .put("checkout_group_id","offline-checkout").put("status","PENDING_PAYMENT").put("total_cents",2980)
            .put("items",new JSONArray().put(new JSONObject().put("id","local-item").put("productId","book-1")
                .put("productName","测试绘本").put("quantity",1).put("unitPriceCents",2980))),0);
        db.saveConfirmed(space,"cart","book-1",new JSONObject().put("id","book-1").put("quantity",1),0);
        disconnected=new RequestQueue(new NoCache(),request->{throw new NoConnectionError();});
        Field queue=HuiMengApp.class.getDeclaredField("requestQueue");queue.setAccessible(true);queue.set(context.getApplicationContext(),disconnected);disconnected.start();
        String cartJson=new JSONArray().put(new JSONObject().put("id","book-1").put("name","测试绘本").put("priceCents",2980).put("quantity",1)).toString();
        getInstrumentation().runOnMainSync(()->{SessionStore session=new SessionStore(context);session.save("","USER","离线用户",false,"device-test","fixture",false);session.localSpace(space);
            context.getSharedPreferences("huimeng_cart",android.content.Context.MODE_PRIVATE).edit().putString("cart_device-test",cartJson).commit();});
    }
    private void launch()throws Exception{
        payment=getInstrumentation().startActivitySync(new Intent(activity,PaymentActivity.class).putExtra("group_id","offline-checkout").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK));
        waitUntil(()->payment.findViewById(R.id.confirm).isEnabled());
    }
    private interface Ready{boolean get();}
    private void waitUntil(Ready ready){long end=SystemClock.uptimeMillis()+6000;while(SystemClock.uptimeMillis()<end){boolean[] done={false};getInstrumentation().runOnMainSync(()->done[0]=ready.get());if(done[0])return;SystemClock.sleep(60);}fail("页面状态未及时更新");}
    public void testLocalLoginCompletesPaymentWithoutNetworkAndWithoutDuplicates()throws Throwable{
        launch();runTestOnUiThread(()->assertEquals("确认付款",((TextView)payment.findViewById(R.id.confirm)).getText().toString()));
        var login=getInstrumentation().addMonitor(LoginActivity.class.getName(),null,false);
        var success=getInstrumentation().addMonitor(PaymentSuccessActivity.class.getName(),null,false);
        try{
            String number=((TextView)payment.findViewById(R.id.order)).getText().toString();
            assertTrue(number,number.matches("结算单号  JS-[0-9]{10,15}"));
            assertEquals(View.GONE,payment.findViewById(R.id.paymentStatus).getVisibility());
            getInstrumentation().runOnMainSync(()->payment.findViewById(R.id.confirm).performClick());
            Activity receipt=getInstrumentation().waitForMonitorWithTimeout(success,6000);
            assertNotNull("断开控制台后也应进入付款结果页",receipt);
            assertEquals("PAID",db.entity(space,"checkouts","offline-checkout").getString("status"));
            JSONObject projectedOrder=new OrderRepository(db,space).handle(0,"me/orders/local-purchase",null)
                .getJSONObject("data");
            assertEquals("PAID",projectedOrder.getString("status"));
            assertEquals("PREPARING",projectedOrder.getString("logistics_status"));
            assertEquals("pay",db.entity(space,"checkouts","offline-checkout").getString("pendingAction"));
            LocalRepository.IO.submit(()->{}).get(5,TimeUnit.SECONDS);
            assertEquals("[]",getInstrumentation().getTargetContext().getSharedPreferences("huimeng_cart",android.content.Context.MODE_PRIVATE).getString("cart_device-test","[]"));
            assertEquals(0,db.list(space,"cart").length());
            boolean hasCartRemove=false;for(int i=0;i<db.pending(space).length();i++)hasCartRemove|="cart.remove".equals(db.pending(space).getJSONObject(i).optString("action"));
            assertTrue(hasCartRemove);
            assertEquals(2,db.pending(space).length());assertEquals(0,login.getHits());
            getInstrumentation().runOnMainSync(receipt::finish);launch();
            var reopened=getInstrumentation().addMonitor(PaymentSuccessActivity.class.getName(),null,false);
            getInstrumentation().runOnMainSync(()->payment.findViewById(R.id.confirm).performClick());
            Activity secondReceipt=getInstrumentation().waitForMonitorWithTimeout(reopened,6000);
            assertNotNull("重新打开能查看同一笔付款的结果",secondReceipt);
            getInstrumentation().runOnMainSync(secondReceipt::finish);
            assertEquals("重新打开或重试不能重复保存付款",2,db.pending(space).length());assertEquals(0,login.getHits());
            android.graphics.Bitmap image=getInstrumentation().getUiAutomation().takeScreenshot();
            if(image!=null)try(var out=new java.io.FileOutputStream(new java.io.File(activity.getExternalFilesDir(null),"offline-payment.png"))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{image.recycle();}
        }finally{getInstrumentation().removeMonitor(login);getInstrumentation().removeMonitor(success);}
    }
    public void testCachedTokenDoesNotRequireOnlinePreflightToQueue()throws Exception{
        getInstrumentation().runOnMainSync(()->new SessionStore(activity).attachNetworkSession("cached-token"));launch();
        getInstrumentation().runOnMainSync(()->payment.findViewById(R.id.confirm).performClick());
        long end=SystemClock.uptimeMillis()+6000;while(db.pending(space).length()==0&&SystemClock.uptimeMillis()<end)SystemClock.sleep(80);
        assertEquals("断网时不应先请求 auth/me 才能保存本地付款",1,db.pending(space).length());
    }
    public void testDifferentAccountCannotQueuePaymentForOriginalSpace()throws Exception{
        getInstrumentation().runOnMainSync(()->new SessionStore(activity).save("","USER","其他账号",false,"other-account","other",false));launch();
        getInstrumentation().runOnMainSync(()->payment.findViewById(R.id.confirm).performClick());
        waitUntil(()->((TextView)payment.findViewById(R.id.paymentStatus)).getText().toString().contains("账号不一致"));
        assertEquals(0,db.pending(space).length());
    }
    public void testLegacyPendingPaymentCompletesWithoutEnqueuingAgain() throws Exception {
        JSONObject checkout=db.entity(space,"checkouts","offline-checkout").put("pendingAction","pay");
        db.mutate(space,"checkouts","offline-checkout","checkout.pay",checkout,
            new JSONObject().put("groupId","offline-checkout").put("method","DEMO_BALANCE")
                .put("expectedTotalCents",2980),false);
        new LocalApi(db,space).handle(1,"checkout/offline-checkout/pay",new JSONObject());
        assertEquals("PAID",db.entity(space,"checkouts","offline-checkout").getString("status"));
        assertEquals(1,db.pending(space).length());
    }

    public void testCheckoutSeparatesMerchantsAndBothOrdersBecomePaid() throws Exception {
        db.saveConfirmed(space,"addresses","address-test",new JSONObject().put("id","address-test"),1);
        JSONArray items=new JSONArray();
        for(int i=0;i<2;i++){
            String product="test-book-"+i;
            db.saveConfirmed(space,"products",product,new JSONObject().put("id",product).put("name","绘本"+i)
                .put("merchantId","merchant-"+i).put("shop","书店"+i).put("stock",10).put("priceCents",1000+i),1);
            items.put(new JSONObject().put("productId",product).put("quantity",1));
        }
        LocalApi api=new LocalApi(db,space);
        JSONObject checkout=api.handle(1,"checkout",new JSONObject().put("addressId","address-test").put("items",items));
        JSONArray orders=checkout.getJSONArray("orderIds");
        assertEquals(2,orders.length());
        String group=checkout.getString("checkoutGroupId");
        api.handle(1,"checkout/"+group+"/pay",new JSONObject());
        JSONObject create=db.pending(space).getJSONObject(0);
        db.acknowledge(create,new JSONObject().put("operationId",create.getString("operationId"))
            .put("entityId","remote-group").put("version",1)
            .put("data",new JSONObject().put("orderIds",new JSONArray().put("remote-first").put("remote-second"))));
        assertEquals("remote-first",db.entity(space,"buyerOrders",orders.getString(0)).optString("remoteId"));
        assertEquals("remote-second",db.entity(space,"buyerOrders",orders.getString(1)).optString("remoteId"));
        for(int i=0;i<2;i++)
            assertEquals("PAID",api.handle(0,"me/orders/"+orders.getString(i),null).getJSONObject("data").optString("status"));
    }

    public void testSettingsEntriesHideCurrentValues() throws Exception {
        var wallpaper=new com.huimeng.bookshop.core.WallpaperStore(activity);
        wallpaper.setWallpaper(com.huimeng.bookshop.core.WallpaperStore.GARDEN);
        getInstrumentation().runOnMainSync(()->{
            wallpaper.apply(activity);
            activity.selectTab(4);
        });
        waitUntil(()->activity.findViewById(R.id.settings)!=null);
        var monitor=getInstrumentation().addMonitor(ProfileSettingsActivity.class.getName(),null,false);
        assertNull(activity.findViewById(R.id.logout));
        getInstrumentation().runOnMainSync(()->activity.findViewById(R.id.settings).performClick());
        Activity settings=getInstrumentation().waitForMonitorWithTimeout(monitor,5000);
        assertNotNull(settings);
        try{
            assertNotNull(find(settings,"意见反馈"));
            assertNotNull(find(settings,"外观与壁纸设置"));
            assertNotNull(find(settings,"商家申请与进度"));
            assertNotNull(find(settings,"退出登录"));
            screen(settings,"buyer-settings.png");
            var accountMonitor=getInstrumentation().addMonitor(EditAccountActivity.class.getName(),null,false);
            getInstrumentation().runOnMainSync(()->settings.findViewById(R.id.editAccount).performClick());
            Activity account=getInstrumentation().waitForMonitorWithTimeout(accountMonitor,5000);
            assertNotNull(account);
            try{
                assertEquals("登录用户名",((TextView)account.findViewById(R.id.editUsername)).getText().toString());
                assertEquals("显示昵称",((TextView)account.findViewById(R.id.editNickname)).getText().toString());
                assertEquals("修改密码",((TextView)account.findViewById(R.id.editPassword)).getText().toString());
                assertNotNull(((TextView)account.findViewById(R.id.editUsername)).getCompoundDrawablesRelative()[2]);
                screen(account,"edit-account.png");
            }finally{getInstrumentation().runOnMainSync(account::finish);}
        }finally{getInstrumentation().runOnMainSync(settings::finish);}
    }

    public void testFeedbackAndAfterSaleOpenAndPersistWithoutControlCenter() throws Exception {
        new LocalApi(db,space).handle(1,"checkout/offline-checkout/pay",new JSONObject());
        Activity feedback=openService("feedback",null);
        try{
            click(feedback,"提交意见反馈");
            getInstrumentation().runOnMainSync(()->{
                field(feedback,"反馈标题").setText("断连反馈");
                field(feedback,"问题描述").setText("断开控制台时提交");
            });
            click(feedback,"提交反馈");
            waitUntil(()->find(feedback,"反馈详情")!=null);
            assertNotNull(find(feedback,"断连反馈"));
            assertNull(find(feedback,"请重新登录"));
            assertEquals(1,db.list(space,"feedback").length());
        }finally{getInstrumentation().runOnMainSync(feedback::finish);}
        Activity afterSale=openService("after-sales","local-purchase");
        try{
            waitUntil(()->find(afterSale,"测试绘本")!=null);
            getInstrumentation().runOnMainSync(()->{
                ((CheckBox)find(afterSale,"测试绘本")).setChecked(true);
                field(afterSale,"申请原因").setText("申请退款");
            });
            click(afterSale,"提交售后申请");
            waitUntil(()->find(afterSale,"售后进度")!=null);
            assertNull(find(afterSale,"请重新登录"));
            assertEquals("MERCHANT_REVIEW",db.list(space,"after-sales").getJSONObject(0).optString("status"));
        }finally{getInstrumentation().runOnMainSync(afterSale::finish);}
        Activity history=openService("after-sales",null);
        try{
            waitUntil(()->find(history,"申请退款")!=null);
            assertNull(find(history,"请重新登录"));
        }finally{getInstrumentation().runOnMainSync(history::finish);}
    }

    private void screen(Activity page,String name)throws java.io.IOException{
        getInstrumentation().waitForIdleSync();
        SystemClock.sleep(450);
        android.graphics.Bitmap image=getInstrumentation().getUiAutomation().takeScreenshot();
        if(image==null)return;
        try(var stream=new java.io.FileOutputStream(new java.io.File(page.getExternalFilesDir(null),name))){
            image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream);
        }finally{image.recycle();}
    }

    private Activity openService(String kind,String order){
        return getInstrumentation().startActivitySync(new Intent(activity,ServiceActivity.class)
            .putExtra("kind",kind).putExtra("order_id",order).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private void click(Activity page,String text){
        waitUntil(()->button(page.getWindow().getDecorView(),text)!=null);
        getInstrumentation().runOnMainSync(()->button(page.getWindow().getDecorView(),text).performClick());
    }

    private View button(View view,String text){
        if(view instanceof android.widget.Button candidate&&text.contentEquals(candidate.getText()))return view;
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){
            View result=button(group.getChildAt(i),text);
            if(result!=null)return result;
        }
        return null;
    }

    private View find(Activity page,String text){return match(page.getWindow().getDecorView(),text,false);}
    private EditText field(Activity page,String hint){return (EditText)match(page.getWindow().getDecorView(),hint,true);}
    private View match(View view,String text,boolean hint){
        if(hint&&view instanceof EditText input&&text.contentEquals(input.getHint()==null?"":input.getHint()))return view;
        if(!hint&&view instanceof TextView label&&text.contentEquals(label.getText()))return view;
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){
            View result=match(group.getChildAt(i),text,hint);
            if(result!=null)return result;
        }
        return null;
    }

    @Override protected void tearDown()throws Exception{
        if(payment!=null)getInstrumentation().runOnMainSync(payment::finish);
        getInstrumentation().runOnMainSync(activity::finish);
        getInstrumentation().runOnMainSync(()->SyncCoordinator.get(activity).stop());
        disconnected.stop();LocalRepository.IO.submit(()->{}).get(10,TimeUnit.SECONDS);
        Field instance=LocalDatabase.class.getDeclaredField("instance");instance.setAccessible(true);instance.set(null,previous);db.close();super.tearDown();
    }
}
