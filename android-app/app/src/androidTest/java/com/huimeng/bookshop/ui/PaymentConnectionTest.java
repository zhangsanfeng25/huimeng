package com.huimeng.bookshop.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.SystemClock;
import android.widget.TextView;
import com.android.volley.*;
import com.android.volley.toolbox.NoCache;
import com.huimeng.bookshop.HuiMengApp;
import com.huimeng.bookshop.R;
import com.huimeng.bookshop.core.SessionStore;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONObject;

/** Failure and stale-response tests use a fixture transport; never touch the business backend. */
public class PaymentConnectionTest extends RefreshNavigationTest {
    private RequestQueue queue;private Activity payment,auth;
    private final CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1);
    private final AtomicInteger payments=new AtomicInteger();private boolean offline;
    @Override protected String fixture(com.android.volley.Request<?> request){if(request.getUrl().endsWith("checkout/payment-fixture"))return "{\"data\":{\"id\":\"payment-fixture\",\"status\":\"PENDING\",\"totalCents\":2980}}";return super.fixture(request);}
    private void launch(boolean disconnected)throws Exception{
        offline=disconnected;queue=new RequestQueue(new NoCache(),request->{
            if(request.getUrl().endsWith("auth/me")){started.countDown();if(offline)throw new NoConnectionError();try{release.await(10,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}throw new AuthFailureError(new NetworkResponse(401,"{}".getBytes(StandardCharsets.UTF_8),false,0,Collections.emptyList()));}
            if(request.getUrl().endsWith("/pay"))payments.incrementAndGet();return new NetworkResponse(200,fixture(request).getBytes(StandardCharsets.UTF_8),false,0,Collections.emptyList());
        });var field=HuiMengApp.class.getDeclaredField("requestQueue");field.setAccessible(true);field.set(getInstrumentation().getTargetContext().getApplicationContext(),queue);queue.start();
        payment=getInstrumentation().startActivitySync(new Intent(activity,PaymentActivity.class).putExtra("group_id","payment-fixture").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK));
        waitFor(()->payment.findViewById(R.id.confirm).isEnabled(),5000);getInstrumentation().runOnMainSync(()->payment.findViewById(R.id.confirm).performClick());assertTrue(started.await(4,TimeUnit.SECONDS));
    }
    private interface Ready{boolean done();}
    public void testOnlineBuyerPaysOnceAndReachesConfirmedResult()throws Exception{
        queue=new RequestQueue(new NoCache(),request->{
            String body;
            if(request.getUrl().endsWith("auth/me"))body="{\"user\":{\"id\":\"device-test\",\"activeRole\":\"USER\"}}";
            else if(request.getUrl().endsWith("checkout/payment-fixture/pay")){payments.incrementAndGet();body="{\"ok\":true}";}
            else if(request.getUrl().endsWith("checkout/payment-fixture"))body="{\"data\":{\"id\":\"payment-fixture\",\"status\":\""+(payments.get()>0?"PAID":"PENDING")+"\",\"totalCents\":2980}}";
            else body=fixture(request);
            return new NetworkResponse(200,body.getBytes(StandardCharsets.UTF_8),false,0,Collections.emptyList());
        });
        var field=HuiMengApp.class.getDeclaredField("requestQueue");field.setAccessible(true);field.set(getInstrumentation().getTargetContext().getApplicationContext(),queue);queue.start();
        var monitor=getInstrumentation().addMonitor(PaymentSuccessActivity.class.getName(),null,false);
        try{
            payment=getInstrumentation().startActivitySync(new Intent(activity,PaymentActivity.class).putExtra("group_id","payment-fixture").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK));
            waitFor(()->payment.findViewById(R.id.confirm).isEnabled(),5000);
            getInstrumentation().runOnMainSync(()->{assertEquals("确认付款",((TextView)payment.findViewById(R.id.confirm)).getText().toString());payment.findViewById(R.id.confirm).performClick();});
            Activity success=getInstrumentation().waitForMonitorWithTimeout(monitor,8000);
            assertNotNull("服务端确认后应进入付款结果页",success);
            assertEquals("只允许提交一次付款",1,payments.get());
            getInstrumentation().runOnMainSync(success::finish);
        }finally{getInstrumentation().removeMonitor(monitor);}
    }
    public void testRestoredNetworkSessionRefreshesPaymentWithoutReopening()throws Exception{
        getInstrumentation().runOnMainSync(()->new SessionStore(activity).save("","USER","测试账号",false,"device-test","fixture",false));
        payment=getInstrumentation().startActivitySync(new Intent(activity,PaymentActivity.class).putExtra("group_id","payment-fixture").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK));
        waitFor(()->payment.findViewById(R.id.confirm).isEnabled(),5000);
        getInstrumentation().runOnMainSync(()->{
            new SessionStore(activity).attachNetworkSession("restored-token");
            com.huimeng.bookshop.core.sync.SyncStatusStore.publish("",0);
        });
        waitFor(()->((TextView)payment.findViewById(R.id.confirm)).getText().toString().equals("确认付款")&&((TextView)payment.findViewById(R.id.paymentStatus)).getText().length()==0,5000);
    }
    public void testPaymentLoginCannotFallBackToOfflineSuccess()throws Exception{
        var context=getInstrumentation().getTargetContext();
        new com.huimeng.bookshop.core.OfflineAccounts(context).save(com.huimeng.bookshop.BuildConfig.API_BASE_URL,"payment-offline-fixture","fixture-password",new JSONObject("{\"token\":\"old\",\"user\":{\"id\":\"device-test\",\"username\":\"payment-offline-fixture\",\"name\":\"测试\",\"roles\":[\"USER\"],\"activeRole\":\"USER\"}}"));
        queue=new RequestQueue(new NoCache(),request->{if(request.getUrl().endsWith("auth/login")){started.countDown();throw new ServerError(new NetworkResponse(503,"{}".getBytes(StandardCharsets.UTF_8),false,0,Collections.emptyList()));}return new NetworkResponse(200,fixture(request).getBytes(StandardCharsets.UTF_8),false,0,Collections.emptyList());});
        var field=HuiMengApp.class.getDeclaredField("requestQueue");field.setAccessible(true);field.set(context.getApplicationContext(),queue);queue.start();
        getInstrumentation().runOnMainSync(()->new SessionStore(activity).save("","USER","测试账号",false,"device-test","payment-offline-fixture",false));
        auth=getInstrumentation().startActivitySync(new Intent(activity,LoginActivity.class).putExtra("return_to_payment",true).putExtra("required_account_id","device-test").putExtra("login_account","payment-offline-fixture").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK));
        getInstrumentation().runOnMainSync(()->{((TextView)auth.findViewById(R.id.passwordInput)).setText("fixture-password");auth.findViewById(R.id.loginButton).performClick();});
        assertTrue(started.await(5,TimeUnit.SECONDS));
        waitFor(()->auth.isFinishing()||auth.findViewById(R.id.loginButton).isEnabled(),10000);
        assertFalse("付款验证失败必须留在登录页，不能以离线成功返回付款形成循环",auth.isFinishing());
    }
    private void waitFor(Ready ready,long timeout){long end=SystemClock.uptimeMillis()+timeout;while(SystemClock.uptimeMillis()<end){final boolean[] done={false};getInstrumentation().runOnMainSync(()->done[0]=ready.done());if(done[0])return;SystemClock.sleep(80);}fail("付款界面没有在预期时间恢复");}
    public void testDisconnectedPaymentCanRetryWithoutClaimingSuccess()throws Exception{launch(true);waitFor(()->((TextView)payment.findViewById(R.id.paymentStatus)).getText().toString().contains("付款暂未完成"),5000);waitFor(()->payment.findViewById(R.id.confirm).isEnabled(),3000);assertFalse("断网不能结束付款页",payment.isFinishing());assertEquals("断网验证不能提交付款",0,payments.get());}
    public void testOldUnauthorizedResponseCannotClearReloginOrKeepButtonBusy()throws Exception{
        launch(false);auth=getInstrumentation().startActivitySync(new Intent(activity,LoginActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK));
        getInstrumentation().runOnMainSync(()->{new SessionStore(activity).save("restored-token","USER","测试账号",false,"device-test","fixture",false);auth.finish();});
        waitFor(()->payment.findViewById(R.id.confirm).isEnabled(),5000);release.countDown();SystemClock.sleep(500);
        assertEquals("restored-token",new SessionStore(activity).token());assertTrue(payment.findViewById(R.id.confirm).isEnabled());assertEquals(0,payments.get());
    }
    public void testAlreadyPaidRemoteCheckoutDoesNotSubmitAnotherPayment()throws Exception{
        AtomicInteger queries=new AtomicInteger();
        queue=new RequestQueue(new NoCache(),request->{
            String body;
            if(request.getUrl().endsWith("auth/me"))body="{\"user\":{\"id\":\"device-test\"}}";
            else if(request.getUrl().endsWith("checkout/payment-fixture")){
                String status=queries.incrementAndGet()==1?"PENDING":"PAID";
                body="{\"data\":{\"id\":\"payment-fixture\",\"status\":\""+status+"\",\"totalCents\":2980}}";
            }else{if(request.getUrl().endsWith("/pay"))payments.incrementAndGet();body=fixture(request);}
            return new NetworkResponse(200,body.getBytes(StandardCharsets.UTF_8),false,0,Collections.emptyList());
        });
        var field=HuiMengApp.class.getDeclaredField("requestQueue");field.setAccessible(true);
        field.set(getInstrumentation().getTargetContext().getApplicationContext(),queue);queue.start();
        payment=getInstrumentation().startActivitySync(new Intent(activity,PaymentActivity.class)
            .putExtra("group_id","payment-fixture").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK));
        waitFor(()->payment.findViewById(R.id.confirm).isEnabled(),5000);
        getInstrumentation().runOnMainSync(()->payment.findViewById(R.id.confirm).performClick());
        waitFor(()->payment.isFinishing(),10000);
        assertEquals("已付款结算单不能重复提交",0,payments.get());
    }
    @Override protected void tearDown()throws Exception{release.countDown();if(auth!=null&&!auth.isFinishing())getInstrumentation().runOnMainSync(auth::finish);if(payment!=null&&!payment.isFinishing())getInstrumentation().runOnMainSync(payment::finish);if(queue!=null)queue.stop();super.tearDown();}
}
