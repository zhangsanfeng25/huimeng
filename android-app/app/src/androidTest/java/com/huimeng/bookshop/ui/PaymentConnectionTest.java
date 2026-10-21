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
    private void waitFor(Ready ready,long timeout){long end=SystemClock.uptimeMillis()+timeout;while(SystemClock.uptimeMillis()<end){final boolean[] done={false};getInstrumentation().runOnMainSync(()->done[0]=ready.done());if(done[0])return;SystemClock.sleep(80);}fail("付款界面没有在预期时间恢复");}
    public void testDisconnectedPaymentCanRetryWithoutClaimingSuccess()throws Exception{launch(true);waitFor(()->((TextView)payment.findViewById(R.id.paymentStatus)).getText().toString().contains("无法连接商城服务"),5000);waitFor(()->payment.findViewById(R.id.confirm).isEnabled(),3000);assertFalse("断网不能结束付款页",payment.isFinishing());assertEquals("断网验证不能提交付款",0,payments.get());}
    public void testOldUnauthorizedResponseCannotClearReloginOrKeepButtonBusy()throws Exception{
        launch(false);auth=getInstrumentation().startActivitySync(new Intent(activity,LoginActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK));
        getInstrumentation().runOnMainSync(()->{new SessionStore(activity).save("restored-token","USER","测试账号",false,"device-test","fixture",false);auth.finish();});
        waitFor(()->payment.findViewById(R.id.confirm).isEnabled(),5000);release.countDown();SystemClock.sleep(500);
        assertEquals("restored-token",new SessionStore(activity).token());assertTrue(payment.findViewById(R.id.confirm).isEnabled());assertEquals(0,payments.get());
    }
    public void testRetryDuringPendingQueryContinuesConfirmation()throws Exception{
        AtomicInteger queries=new AtomicInteger();
        queue=new RequestQueue(new NoCache(),request->{
            String body;
            if(request.getUrl().endsWith("auth/me"))body="{\"user\":{\"id\":\"device-test\"}}";
            else if(request.getUrl().endsWith("checkout/payment-fixture")){
                int count=queries.incrementAndGet();
                if(count==2){started.countDown();try{release.await(10,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
                body="{\"data\":{\"id\":\"payment-fixture\",\"status\":\""+(count>=7?"PAID":"PENDING")+"\",\"pendingAction\":\"pay\",\"totalCents\":2980}}";
            }else{if(request.getUrl().endsWith("/pay"))payments.incrementAndGet();body=fixture(request);}
            return new NetworkResponse(200,body.getBytes(StandardCharsets.UTF_8),false,0,Collections.emptyList());
        });
        var field=HuiMengApp.class.getDeclaredField("requestQueue");field.setAccessible(true);field.set(getInstrumentation().getTargetContext().getApplicationContext(),queue);queue.start();
        payment=getInstrumentation().startActivitySync(new Intent(activity,PaymentActivity.class).putExtra("group_id","payment-fixture").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK));
        assertTrue("必须在确认查询进行中重试",started.await(5,TimeUnit.SECONDS));
        getInstrumentation().runOnMainSync(()->payment.findViewById(R.id.confirm).performClick());
        release.countDown();waitFor(()->payment.isFinishing(),10000);
        assertEquals("已有付款意向不能重复提交",0,payments.get());
    }
    @Override protected void tearDown()throws Exception{release.countDown();if(auth!=null&&!auth.isFinishing())getInstrumentation().runOnMainSync(auth::finish);if(payment!=null&&!payment.isFinishing())getInstrumentation().runOnMainSync(payment::finish);if(queue!=null)queue.stop();super.tearDown();}
}
