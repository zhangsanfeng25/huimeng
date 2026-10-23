package com.huimeng.bookshop.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import com.huimeng.bookshop.R;
import com.huimeng.bookshop.BuildConfig;
import com.huimeng.bookshop.core.SessionStore;
import com.huimeng.bookshop.core.local.*;
import org.json.*;
import java.net.*;
import java.util.concurrent.*;

/** Runs only on the separate QA package and temporary API database. Never touches the normal account space. */
@SuppressWarnings("deprecation")
public class CustomerServicesLiveTest extends InstrumentationTestCase {
    private String token="";private final java.util.List<Activity> pages=new java.util.ArrayList<>();
    private android.content.Context context(){return getInstrumentation().getTargetContext();}
    @Override protected void setUp()throws Exception{super.setUp();assertTrue("仅允许隔离 QA 包",context().getPackageName().endsWith(".qa"));assertTrue(BuildConfig.API_BASE_URL.contains(":3100/"));}
    private JSONObject http(String method,String path,JSONObject body)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(BuildConfig.API_BASE_URL+path).openConnection();c.setRequestMethod(method);c.setConnectTimeout(8000);c.setReadTimeout(8000);if(!token.isEmpty())c.setRequestProperty("Authorization","Bearer "+token);
        if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");try(var out=c.getOutputStream()){out.write(body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}}
        int status=c.getResponseCode();String value;try(var in=status>=400?c.getErrorStream():c.getInputStream()){value=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}c.disconnect();assertTrue(path+" HTTP "+status+": "+value,status<400);return new JSONObject(value);
    }
    private void login(String account,String role)throws Exception{
        JSONObject r=http("POST","auth/login",new JSONObject().put("username",account).put("password","demo12345").put("role",role));token=r.getString("token");JSONObject user=r.getJSONObject("user");
        CountDownLatch latch=new CountDownLatch(1);final String[] error={null};
        LocalRepository.IO.execute(()->{try{SessionStore s=new SessionStore(context());LocalDatabase db=LocalDatabase.get(context());LocalSpaceStore spaces=new LocalSpaceStore(db);String space=spaces.forAccount(BuildConfig.API_BASE_URL,user.getString("id"));spaces.select(space);spaces.role(space,role);LocalBootstrap.prepare(context(),db,space);db.saveConfirmed(space,"profile","self",user,user.optLong("version"));
            getInstrumentation().runOnMainSync(()->{com.huimeng.bookshop.core.sync.SyncCoordinator.get(context()).stop();s.save(token,role,user.optString("name"),role.equals("MERCHANT"),user.optString("id"),account,false);s.localSpace(space);});
        }catch(Exception e){error[0]=e.toString();}finally{latch.countDown();}});assertTrue(latch.await(15,TimeUnit.SECONDS));assertNull(error[0]);
    }
    private Activity open(Class<?> type,Intent extras){Intent i=extras==null?new Intent():extras;i.setClass(context(),type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK);Activity a=getInstrumentation().startActivitySync(i);pages.add(a);return a;}
    private View find(View v,String text){if(v instanceof TextView t&&(text.equals(t.getText().toString())||text.equals(String.valueOf(t.getHint()))))return v;if(v instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){View x=find(g.getChildAt(i),text);if(x!=null)return x;}return null;}
    private View clickable(View v,String text){if(v.isClickable()&&v instanceof TextView t&&text.equals(t.getText().toString()))return v;if(v instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){View x=clickable(g.getChildAt(i),text);if(x!=null)return x;}return null;}
    private void click(Activity a,String text)throws Exception{waitFor(()->clickable(a.getWindow().getDecorView(),text)!=null,15000,"缺少按钮 "+text);getInstrumentation().runOnMainSync(()->clickable(a.getWindow().getDecorView(),text).performClick());}
    private EditText input(View v,String hint){if(v instanceof EditText e&&hint.equals(String.valueOf(e.getHint())))return e;if(v instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){EditText e=input(g.getChildAt(i),hint);if(e!=null)return e;}return null;}
    private void selectSaleItems(View view){if(view instanceof android.widget.CheckBox box)box.setChecked(true);if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)selectSaleItems(group.getChildAt(i));}
    private void fill(Activity a,String hint,String value){getInstrumentation().runOnMainSync(()->input(a.getWindow().getDecorView(),hint).setText(value));}
    private interface Check{boolean ready()throws Exception;}
    private void waitFor(Check condition,long timeout,String message)throws Exception{long end=SystemClock.uptimeMillis()+timeout;while(SystemClock.uptimeMillis()<end){if(condition.ready())return;SystemClock.sleep(200);}fail(message);}
    private void capture(Activity a,String name)throws Exception{final android.graphics.Bitmap[] frame={null};getInstrumentation().runOnMainSync(()->{View view=a.getWindow().getDecorView();frame[0]=android.graphics.Bitmap.createBitmap(view.getWidth(),view.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);view.draw(new android.graphics.Canvas(frame[0]));});try(var out=new java.io.FileOutputStream(new java.io.File(a.getExternalFilesDir(null),name+".png"))){frame[0].compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{frame[0].recycle();}}
    public void testPurchaseChatFeedbackAndAfterSale()throws Exception{
        login("xiaoxiong","USER");Activity main=open(MainActivity.class,null);String space=new SessionStore(context()).localSpaceId();
        waitFor(()->!LocalDatabase.get(context()).metadata("last-sync:"+space).isEmpty(),45000,"初始同步未完成");
        java.util.Set<String> prior=new java.util.HashSet<>();JSONArray existing=http("GET","me/orders",null).getJSONArray("data");for(int i=0;i<existing.length();i++)prior.add(existing.getJSONObject(i).getString("id"));
        Activity checkout=open(CheckoutActivity.class,new Intent().putExtra("product_id","P0001"));waitFor(()->checkout.findViewById(R.id.submit).isEnabled(),15000,"结算未就绪");
        var paymentMonitor=getInstrumentation().addMonitor(PaymentActivity.class.getName(),null,false);getInstrumentation().runOnMainSync(()->checkout.findViewById(R.id.submit).performClick());Activity payment=getInstrumentation().waitForMonitorWithTimeout(paymentMonitor,15000);assertNotNull("应进入付款页",payment);pages.add(payment);
        waitFor(()->payment.findViewById(R.id.confirm).isEnabled(),10000,"付款按钮未就绪");getInstrumentation().runOnMainSync(()->payment.findViewById(R.id.confirm).performClick());
        final String[] order={null};waitFor(()->{JSONArray rows=http("GET","me/orders",null).getJSONArray("data");for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);if(row.optString("status").equals("PAID")&&!prior.contains(row.optString("id"))){order[0]=row.getString("id");return true;}}return false;},45000,"模拟器购买未出现在服务端订单");
        capture(main,"qa-purchase-confirmed");JSONObject orderDetail=http("GET","me/orders/"+order[0],null).getJSONObject("data");String merchant=orderDetail.getString("merchant_id");
        Activity chat=open(ServiceActivity.class,new Intent().putExtra("kind","conversations").putExtra("mode","contact").putExtra("merchant_id",merchant).putExtra("order_id",order[0]));
        waitFor(()->find(chat.getWindow().getDecorView(),"输入消息…")!=null,12000,"聊天未建立");fill(chat,"输入消息…","QA购买后咨询：请确认订单");click(chat,"发送");
        waitFor(()->find(chat.getWindow().getDecorView(),"QA购买后咨询：请确认订单")!=null,10000,"聊天未显示已发送内容");capture(chat,"qa-buyer-chat");
        Activity feedback=open(ServiceActivity.class,new Intent().putExtra("kind","feedback").putExtra("mode","create"));fill(feedback,"反馈标题","QA三端联调反馈");fill(feedback,"问题描述","验证模拟器提交后管理员自动出现并可回复。");click(feedback,"提交反馈");waitFor(()->find(feedback.getWindow().getDecorView(),"待处理")!=null,10000,"反馈未创建");capture(feedback,"qa-feedback");
        Activity sale=open(ServiceActivity.class,new Intent().putExtra("kind","after-sales").putExtra("order_id",order[0]));waitFor(()->find(sale.getWindow().getDecorView(),"申请原因")!=null,10000,"售后表单未打开");getInstrumentation().runOnMainSync(()->selectSaleItems(sale.getWindow().getDecorView()));fill(sale,"申请原因","QA整单退款验证");fill(sale,"补充说明（选填）","只在隔离测试数据库中验证流程。");click(sale,"提交售后申请");waitFor(()->find(sale.getWindow().getDecorView(),"待商家初审")!=null,10000,"售后未创建");capture(sale,"qa-buyer-after-sale");
        JSONObject output=new JSONObject().put("orderId",order[0]).put("afterSaleId",http("GET","service/after-sales",null).getJSONArray("data").getJSONObject(0).getString("id")).put("feedbackId",http("GET","service/feedback",null).getJSONArray("data").getJSONObject(0).getString("id")).put("conversationId",http("GET","service/conversations",null).getJSONArray("data").getJSONObject(0).getString("id"));
        try(var out=new java.io.FileOutputStream(new java.io.File(context().getExternalFilesDir(null),"qa-records.json"))){out.write(output.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    }
    public void testMerchantReplyAndInitialReview()throws Exception{
        JSONObject records;try(var in=new java.io.FileInputStream(new java.io.File(context().getExternalFilesDir(null),"qa-records.json"))){records=new JSONObject(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}
        login("merchant_a","MERCHANT");Activity chat=open(ServiceActivity.class,new Intent().putExtra("kind","conversations").putExtra("record_id",records.getString("conversationId")));
        waitFor(()->find(chat.getWindow().getDecorView(),"输入消息…")!=null,12000,"商家聊天未打开");fill(chat,"输入消息…","QA商家回复：已收到订单咨询");click(chat,"发送");waitFor(()->find(chat.getWindow().getDecorView(),"QA商家回复：已收到订单咨询")!=null,10000,"商家回复未显示");capture(chat,"qa-merchant-chat");
        Activity sale=open(ServiceActivity.class,new Intent().putExtra("kind","after-sales").putExtra("record_id",records.getString("afterSaleId")));waitFor(()->find(sale.getWindow().getDecorView(),"提交处理意见")!=null,12000,"商家售后入口缺失");capture(sale,"qa-merchant-after-sale");
        http("POST","service/after-sales/"+records.getString("afterSaleId"),new JSONObject().put("action","merchant-review").put("approve",true).put("note","QA商家初审同意退款"));
        waitFor(()->find(sale.getWindow().getDecorView(),"待平台审核")!=null,12000,"商家售后未自动更新");
    }
    public void testBuyerViewsPlatformOutcomeAndEntries()throws Exception{
        String theme=((android.test.InstrumentationTestRunner)getInstrumentation()).getArguments().getString("visualTheme","LIGHT");
        context().getSharedPreferences("huimeng_appearance",0).edit().putString("theme",theme).commit();
        getInstrumentation().runOnMainSync(()->new com.huimeng.bookshop.core.AppearanceStore(context()).apply());
        JSONObject records;try(var in=new java.io.FileInputStream(new java.io.File(context().getExternalFilesDir(null),"qa-records.json"))){records=new JSONObject(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}
        login("xiaoxiong","USER");Activity main=open(MainActivity.class,null);
        getInstrumentation().runOnMainSync(()->((MainActivity)main).selectTab(4));
        waitFor(()->find(main.getWindow().getDecorView(),"退款 / 售后")!=null,12000,"买家个人页售后入口缺失");
        View messageEntry=find(main.getWindow().getDecorView(),"商家消息");assertTrue("个人页不应显示消息页中保留的商家消息按钮",messageEntry==null||!messageEntry.isShown());assertNotNull(find(main.getWindow().getDecorView(),"意见反馈"));capture(main,"qa-profile-"+theme);
        Activity product=open(ProductDetailActivity.class,new Intent().putExtra("product_id","P0001"));
        waitFor(()->find(product.getWindow().getDecorView(),"联系商家")!=null,12000,"商品页联系入口缺失");SystemClock.sleep(700);capture(product,"qa-product-"+theme);
        Activity order=open(OrderDetailActivity.class,new Intent().putExtra("order_id",records.getString("orderId")));
        waitFor(()->find(order.getWindow().getDecorView(),"售后进度")!=null,12000,"订单没有根据售后状态调整按钮");assertNotNull(find(order.getWindow().getDecorView(),"联系商家"));
        getInstrumentation().runOnMainSync(()->{View entry=find(order.getWindow().getDecorView(),"售后进度");entry.requestRectangleOnScreen(new android.graphics.Rect(0,0,entry.getWidth(),entry.getHeight()),true);});capture(order,"qa-order-"+theme);
        Activity feedback=open(ServiceActivity.class,new Intent().putExtra("kind","feedback").putExtra("record_id",records.getString("feedbackId")));
        waitFor(()->find(feedback.getWindow().getDecorView(),"QA平台回复：已确认模拟器提交正常，已完成三端联动检查。")!=null,12000,"买家未读取到平台回复");assertNotNull(find(feedback.getWindow().getDecorView(),"已解决"));capture(feedback,"qa-feedback-replied-"+theme);
        Activity sale=open(ServiceActivity.class,new Intent().putExtra("kind","after-sales").putExtra("record_id",records.getString("afterSaleId")));
        waitFor(()->find(sale.getWindow().getDecorView(),"售后完成")!=null,12000,"买家未读取平台退款结果");assertNull(clickable(sale.getWindow().getDecorView(),"撤销申请"));capture(sale,"qa-sale-completed-"+theme);
        Activity chat=open(ServiceActivity.class,new Intent().putExtra("kind","conversations").putExtra("record_id",records.getString("conversationId")));
        waitFor(()->find(chat.getWindow().getDecorView(),"QA商家回复：已收到订单咨询")!=null,12000,"买家未读取商家回复");capture(chat,"qa-chat-"+theme);
    }
    public void testPaymentLoginRecoveryAndBackendReceipt()throws Exception{
        login("xiaoxiong","USER");Activity main=open(MainActivity.class,null);String space=new SessionStore(context()).localSpaceId();
        waitFor(()->!LocalDatabase.get(context()).metadata("last-sync:"+space).isEmpty(),45000,"初始同步未完成");
        java.util.Set<String> prior=new java.util.HashSet<>();JSONArray old=http("GET","me/orders",null).getJSONArray("data");for(int i=0;i<old.length();i++)prior.add(old.getJSONObject(i).getString("id"));
        Activity checkout=open(CheckoutActivity.class,new Intent().putExtra("product_id","P0001"));waitFor(()->checkout.findViewById(R.id.submit).isEnabled(),12000,"结算未就绪");
        var monitor=getInstrumentation().addMonitor(PaymentActivity.class.getName(),null,false);getInstrumentation().runOnMainSync(()->checkout.findViewById(R.id.submit).performClick());Activity payment=getInstrumentation().waitForMonitorWithTimeout(monitor,15000);assertNotNull(payment);pages.add(payment);
        waitFor(()->payment.findViewById(R.id.confirm).isEnabled(),12000,"付款未就绪");getInstrumentation().runOnMainSync(()->new SessionStore(context()).clearNetworkSession());
        var authMonitor=getInstrumentation().addMonitor(LoginActivity.class.getName(),null,false);getInstrumentation().runOnMainSync(()->payment.findViewById(R.id.confirm).performClick());Activity auth=getInstrumentation().waitForMonitorWithTimeout(authMonitor,8000);assertNotNull("付款无登录状态时应直接进入登录，并在完成后返回本笔付款",auth);pages.add(auth);
        getInstrumentation().runOnMainSync(()->{((EditText)auth.findViewById(R.id.accountInput)).setText("xingxing");((EditText)auth.findViewById(R.id.passwordInput)).setText("demo12345");auth.findViewById(R.id.loginButton).performClick();});
        waitFor(()->find(auth.getWindow().getDecorView(),"请登录本订单所属的原账号，当前账号不能继续这笔付款")!=null,12000,"付款必须拒绝切换到其他买家账号");
        assertTrue("错误账号不能替换会话",new SessionStore(context()).token().isEmpty());
        getInstrumentation().runOnMainSync(()->{((EditText)auth.findViewById(R.id.accountInput)).setText("xiaoxiong");((EditText)auth.findViewById(R.id.passwordInput)).setText("demo12345");auth.findViewById(R.id.loginButton).performClick();});
        waitFor(auth::isFinishing,15000,"登录未完成");assertFalse("付款页应保留",payment.isFinishing());
        waitFor(()->payment.findViewById(R.id.confirm).isEnabled(),10000,"登录后应恢复付款按钮");getInstrumentation().runOnMainSync(()->payment.findViewById(R.id.confirm).performClick());
        final JSONObject[] paid={null};waitFor(()->{JSONArray rows=http("GET","me/orders",null).getJSONArray("data");for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);if(!prior.contains(row.optString("id"))&&row.optString("status").equals("PAID")){paid[0]=row;return true;}}return false;},45000,"重新登录后付款未进入服务端");
        waitFor(payment::isFinishing,20000,"服务端已付款但页面未显示确认结果");
        try(var out=new java.io.FileOutputStream(new java.io.File(context().getExternalFilesDir(null),"qa-payment-recovery.json"))){out.write(new JSONObject().put("orderId",paid[0].getString("id")).put("groupId",paid[0].getString("checkout_group_id")).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    }
    public void testConfirmedOrderProgressScreenshots()throws Exception{
        login("xiaoxiong","USER");open(MainActivity.class,null);
        for(String kind:new String[]{"payment-recovery","records"}){
            JSONObject record;try(var in=new java.io.FileInputStream(new java.io.File(context().getExternalFilesDir(null),"qa-"+kind+".json"))){record=new JSONObject(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}
            Activity order=open(OrderDetailActivity.class,new Intent().putExtra("order_id",record.getString("orderId")));
            String expected=kind.equals("records")?"退款已完成":"付款已完成";
            waitFor(()->((TextView)order.findViewById(R.id.timeline)).getText().toString().contains(expected),20000,"实际订单进度缺少 "+expected);
            String text=((TextView)order.findViewById(R.id.timeline)).getText().toString();assertFalse("未发货订单不能显示运输",text.contains("正在运输"));
            getInstrumentation().runOnMainSync(()->{View timeline=order.findViewById(R.id.timeline);timeline.requestRectangleOnScreen(new android.graphics.Rect(0,0,timeline.getWidth(),timeline.getHeight()),true);});
            SystemClock.sleep(500);capture(order,"qa-progress-"+kind);SystemClock.sleep(7000);
        }
    }
    @Override protected void tearDown()throws Exception{for(Activity a:pages)if(!a.isFinishing())getInstrumentation().runOnMainSync(a::finish);super.tearDown();}
}
