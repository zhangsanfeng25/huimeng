package com.huimeng.bookshop.ui;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import com.huimeng.bookshop.R;
import com.huimeng.bookshop.core.SessionStore;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONObject;

/** UI regression uses an isolated Volley transport, with no business API writes. */
public class AccountFlowTest extends RefreshNavigationTest {
    private final AtomicReference<String> sent=new AtomicReference<>("");
    private Activity opened,success,guestMain;
    private com.android.volley.RequestQueue authQueue;
    @Override protected String fixture(com.android.volley.Request<?> request){
        String url=request.getUrl();
        if(url.endsWith("auth/login"))return "{\"token\":\"qa-login-token\",\"expiresAt\":\"2999-01-01T00:00:00Z\",\"user\":{\"id\":\"device-test\",\"username\":\"fixture\",\"name\":\"测试登录\",\"roles\":[\"USER\"],\"activeRole\":\"USER\"}}";
        if(url.endsWith("service/conversations"))return "{\"data\":[{\"id\":\"qa-conversation\",\"shop\":\"测试店铺会话\",\"buyer\":\"测试买家\",\"unread\":2,\"last_message\":\"可以咨询订单\"}]}";
        if(url.endsWith("service/conversations/qa-conversation")){
            if(request.getMethod()==1){try{JSONObject body=new JSONObject(new String(request.getBody(),java.nio.charset.StandardCharsets.UTF_8));if(body.optString("action").equals("send"))sent.set(body.optString("content"));}catch(Exception e){throw new RuntimeException(e);}return "{\"ok\":true}";}
            String messages=sent.get().isEmpty()?"[]":"[{\"id\":1,\"sender_role\":\"USER\",\"sender\":\"测试读者\",\"created_at\":\"2026-10-22T00:00:00Z\",\"content\":"+JSONObject.quote(sent.get())+"}]";
            return "{\"data\":{\"id\":\"qa-conversation\",\"shop\":\"测试店铺会话\",\"buyer\":\"测试买家\",\"messages\":"+messages+",\"hasMore\":false,\"peerRead\":0}}";
        }
        return super.fixture(request);
    }
    private interface Condition{boolean ready();}
    private void await(Condition condition,long timeout){long end=SystemClock.uptimeMillis()+timeout;while(SystemClock.uptimeMillis()<end){boolean[] ready={false};getInstrumentation().runOnMainSync(()->ready[0]=condition.ready());if(ready[0])return;SystemClock.sleep(80);}fail("目标界面未在限定时间内完成");}
    private Activity next(Instrumentation.ActivityMonitor monitor)throws Exception{Activity next=getInstrumentation().waitForMonitorWithTimeout(monitor,15000);getInstrumentation().removeMonitor(monitor);assertNotNull("应打开预期界面",next);getInstrumentation().waitForIdleSync();return next;}
    public void testLoginEntersAccountWithoutAssociationDialog()throws Exception{
        getInstrumentation().runOnMainSync(()->new SessionStore(activity).clear());
        opened=getInstrumentation().startActivitySync(new Intent(activity,LoginActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK));
        Instrumentation.ActivityMonitor main=getInstrumentation().addMonitor(MainActivity.class.getName(),null,false);
        getInstrumentation().runOnMainSync(()->{((EditText)opened.findViewById(R.id.accountInput)).setText("fixture");((EditText)opened.findViewById(R.id.passwordInput)).setText("fixture-password");opened.findViewById(R.id.loginButton).performClick();});
        Activity logged=next(main);opened=logged;assertTrue(new SessionStore(logged).authenticated());assertEquals("device-test",new SessionStore(logged).accountId());
    }
    public void testLogoutShowsLoginAndNameOpensLogin()throws Exception{
        Instrumentation.ActivityMonitor main=getInstrumentation().addMonitor(MainActivity.class.getName(),null,false);
        getInstrumentation().runOnMainSync(activity::logout);Activity guest=next(main);opened=guest;guestMain=guest;
        await(()->guest.findViewById(R.id.name)!=null&&"登录".contentEquals(((TextView)guest.findViewById(R.id.name)).getText()),12000);
        assertFalse(new SessionStore(guest).authenticated());assertEquals("",new SessionStore(guest).accountId());
        screenshot("guest-profile");
        Instrumentation.ActivityMonitor login=getInstrumentation().addMonitor(LoginActivity.class.getName(),null,false);
        getInstrumentation().runOnMainSync(()->guest.findViewById(R.id.name).performClick());opened=next(login);
    }
    public void testMessageTabOpensAndSendsConversation()throws Exception{
        getInstrumentation().runOnMainSync(()->{activity.selectTab(3);activity.findViewById(R.id.conversations).performClick();});
        await(()->((RecyclerView)activity.getSupportFragmentManager().getPrimaryNavigationFragment().requireView().findViewById(R.id.list)).findViewHolderForAdapterPosition(0)!=null,6000);
        screenshot("message-inbox");
        Instrumentation.ActivityMonitor chat=getInstrumentation().addMonitor(ServiceActivity.class.getName(),null,false);
        getInstrumentation().runOnMainSync(()->((RecyclerView)activity.getSupportFragmentManager().getPrimaryNavigationFragment().requireView().findViewById(R.id.list)).findViewHolderForAdapterPosition(0).itemView.performClick());opened=next(chat);
        await(()->findInput(opened.getWindow().getDecorView())!=null,5000);
        getInstrumentation().runOnMainSync(()->{findInput(opened.getWindow().getDecorView()).setText("fixture-message");TextView send=findText(opened.getWindow().getDecorView(),"发送");assertNotNull(send);send.performClick();});
        await(()->"fixture-message".equals(sent.get())&&findInput(opened.getWindow().getDecorView()).getText().length()==0&&findText(opened.getWindow().getDecorView(),"fixture-message")!=null,5000);
        getInstrumentation().waitForIdleSync();
        android.os.SystemClock.sleep(350); // Allow the confirmed message layout to reach the screenshot compositor.
        screenshot("message-conversation");
    }
    public void testPaymentSuccessReturnsToOriginalProductAfterFiveSeconds()throws Exception{
        Instrumentation.ActivityMonitor product=getInstrumentation().addMonitor(ProductDetailActivity.class.getName(),null,false);
        long started=SystemClock.elapsedRealtime();success=getInstrumentation().startActivitySync(new Intent(activity,PaymentSuccessActivity.class).putExtra("return_product_id","book-0").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK));
        await(()->((TextView)success.findViewById(R.id.countdown)).getText().toString().contains("秒后返回商品页面"),1200);
        screenshot("payment-success-countdown");
        opened=next(product);assertTrue("不能立即离开成功页",SystemClock.elapsedRealtime()-started>=4500);assertEquals("book-0",opened.getIntent().getStringExtra("product_id"));assertTrue(success.isFinishing());
    }
    private void authFailure(int status)throws Exception{
        authQueue=new com.android.volley.RequestQueue(new com.android.volley.toolbox.NoCache(),request->{
            if(request.getUrl().endsWith("auth/login")||request.getUrl().endsWith("auth/register")){
                if(status==0)throw new com.android.volley.NoConnectionError();
                throw new com.android.volley.AuthFailureError(new com.android.volley.NetworkResponse(status,"{\"message\":\"账号或密码错误\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8),false,0,java.util.Collections.emptyList()));
            }
            return new com.android.volley.NetworkResponse(200,fixture(request).getBytes(java.nio.charset.StandardCharsets.UTF_8),false,0,java.util.Collections.emptyList());
        });
        java.lang.reflect.Field requests=com.huimeng.bookshop.HuiMengApp.class.getDeclaredField("requestQueue");requests.setAccessible(true);requests.set(getInstrumentation().getTargetContext().getApplicationContext(),authQueue);authQueue.start();
    }
    private void cacheAccount()throws Exception{
        JSONObject user=new JSONObject().put("id","offline-qa-user").put("username","offline-qa").put("name","离线测试读者").put("activeRole","USER").put("roles",new org.json.JSONArray().put("USER"));
        new com.huimeng.bookshop.core.OfflineAccounts(activity).save(com.huimeng.bookshop.BuildConfig.API_BASE_URL,"offline-qa","fixture-password",new JSONObject().put("token","cached-qa-token").put("expiresAt","2999-01-01T00:00:00Z").put("user",user));
    }
    private void enterCachedLogin(){getInstrumentation().runOnMainSync(()->{((EditText)opened.findViewById(R.id.accountInput)).setText("offline-qa");((EditText)opened.findViewById(R.id.passwordInput)).setText("fixture-password");opened.findViewById(R.id.loginButton).performClick();});}
    public void testDisconnectedLoginUsesVerifiedAccount()throws Exception{
        getInstrumentation().runOnMainSync(()->new SessionStore(activity).clear());
        cacheAccount();authFailure(0);opened=getInstrumentation().startActivitySync(new Intent(activity,LoginActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK));
        Instrumentation.ActivityMonitor main=getInstrumentation().addMonitor(MainActivity.class.getName(),null,false);enterCachedLogin();opened=next(main);assertEquals("offline-qa-user",new SessionStore(opened).accountId());assertTrue(new SessionStore(opened).authenticated());
    }
    public void testServerRejectionNeverFallsBackToCachedPassword()throws Exception{
        getInstrumentation().runOnMainSync(()->new SessionStore(activity).clear());
        cacheAccount();authFailure(401);opened=getInstrumentation().startActivitySync(new Intent(activity,LoginActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK));enterCachedLogin();
        await(()->"账号或密码错误".contentEquals(((TextView)opened.findViewById(R.id.message)).getText()),6000);assertFalse(opened.isFinishing());assertFalse("offline-qa-user".equals(new SessionStore(opened).accountId()));
        assertNull(new com.huimeng.bookshop.core.OfflineAccounts(activity).login(com.huimeng.bookshop.BuildConfig.API_BASE_URL,"offline-qa","fixture-password","USER"));
    }
    public void testRegistrationRequiresConnection()throws Exception{
        authFailure(0);int before=activity.getSharedPreferences("huimeng_offline_accounts",0).getAll().size();opened=getInstrumentation().startActivitySync(new Intent(activity,RegisterActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK));
        getInstrumentation().runOnMainSync(()->{((EditText)opened.findViewById(R.id.username)).setText("offline_must_not_register");((EditText)opened.findViewById(R.id.nickname)).setText("未注册读者");((EditText)opened.findViewById(R.id.password)).setText("fixture-password");opened.findViewById(R.id.submit).performClick();});
        await(()->opened.findViewById(R.id.submit).isEnabled(),6000);assertFalse(opened.isFinishing());assertEquals(before,activity.getSharedPreferences("huimeng_offline_accounts",0).getAll().size());
    }
    private EditText findInput(View view){if(view instanceof EditText)return (EditText)view;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){EditText found=findInput(group.getChildAt(i));if(found!=null)return found;}return null;}
    private void screenshot(String name)throws Exception{android.graphics.Bitmap bitmap=getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(bitmap);try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(getInstrumentation().getTargetContext().getExternalFilesDir(null),name+".png"))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}}
    private TextView findText(View view,String text){if(view instanceof TextView label&&text.contentEquals(label.getText()))return label;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){TextView found=findText(group.getChildAt(i),text);if(found!=null)return found;}return null;}
    @Override protected void tearDown()throws Exception{if(opened!=null&&!opened.isFinishing())getInstrumentation().runOnMainSync(opened::finish);if(guestMain!=null&&!guestMain.isFinishing())getInstrumentation().runOnMainSync(guestMain::finish);if(success!=null&&!success.isFinishing())getInstrumentation().runOnMainSync(success::finish);if(authQueue!=null)authQueue.stop();super.tearDown();}
}
