package com.huimeng.bookshop.ui;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.huimeng.bookshop.R;
import org.json.*;

/** Isolated transport: no changes to actual customer accounts or orders. */
public class PartialRecoveryFlowTest extends RefreshNavigationTest {
    private volatile JSONObject submitted,reset;
    private Activity opened;
    private boolean remaining;
    @Override protected String fixture(com.android.volley.Request<?> request){
        String url=request.getUrl();
        if(url.endsWith("service/after-sales/qa-ended"))return "{\"data\":{\"id\":\"qa-ended\",\"order_id\":\"qa-order\",\"reason\":\"已处理申请\",\"type\":\"REFUND_ONLY\",\"status\":\"COMPLETED\",\"can_apply_again\":"+remaining+",\"amount_cents\":9000}}";
        if(url.endsWith("service/order-after-sale/qa-empty"))return "{\"data\":{\"order\":{\"id\":\"qa-empty\",\"status\":\"PAID\",\"total_cents\":9000},\"sale\":null,\"items\":[]}}";
        if(url.endsWith("auth/forgot-password")){reset=body(request);return "{\"ok\":true,\"userId\":\"device-test\"}";}
        if(url.endsWith("service/order-after-sale/qa-order"))return "{\"data\":{\"order\":{\"id\":\"qa-order\",\"status\":\"SHIPPED\",\"total_cents\":9000},\"sale\":null,\"items\":[{\"id\":\"qa-item\",\"product_name\":\"测试绘本\",\"unit_price_cents\":3000,\"returnable_quantity\":3,\"refundable_cents\":9000}]}}";
        if(url.endsWith("service/after-sales")&&request.getMethod()==1){submitted=body(request);return "{\"data\":{\"id\":\"qa-sale\"}}";}
        if(url.endsWith("service/after-sales/qa-sale"))return "{\"data\":{\"id\":\"qa-sale\",\"order_id\":\"qa-order\",\"reason\":\"封面破损\",\"type\":\"RETURN_REFUND\",\"status\":\"MERCHANT_REVIEW\",\"amount_cents\":350,\"items\":[{\"id\":\"qa-item\",\"product_name\":\"测试绘本\",\"quantity\":1,\"amount_cents\":350}]}}";
        return super.fixture(request);
    }
    private JSONObject body(com.android.volley.Request<?> request){try{return new JSONObject(new String(request.getBody(),java.nio.charset.StandardCharsets.UTF_8));}catch(Exception e){throw new RuntimeException(e);}}
    private interface Ready{boolean get();}
    private void await(Ready ready)throws Exception{long end=SystemClock.uptimeMillis()+8000;while(SystemClock.uptimeMillis()<end){boolean[] done={false};getInstrumentation().runOnMainSync(()->done[0]=ready.get());if(done[0])return;SystemClock.sleep(80);}fail("界面未完成预期操作");}
    private View text(View root,String label){if(root instanceof TextView t&&label.contentEquals(t.getText()))return root;if(root instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){View found=text(g.getChildAt(i),label);if(found!=null)return found;}return null;}
    private EditText input(View root,String label){if(root instanceof EditText e&&(label.contentEquals(e.getHint())||label.contentEquals(e.getContentDescription())))return e;if(root instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){EditText found=input(g.getChildAt(i),label);if(found!=null)return found;}return null;}
    private void shot(String name)throws Exception{getInstrumentation().waitForIdleSync();SystemClock.sleep(350);android.graphics.Bitmap b=getInstrumentation().getUiAutomation().takeScreenshot();try(var out=new java.io.FileOutputStream(new java.io.File(activity.getExternalFilesDir(null),name+".png"))){b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{b.recycle();}}
    public void testSelectPartialItemsAndSubmit()throws Exception{
        opened=getInstrumentation().startActivitySync(new Intent(activity,ServiceActivity.class).putExtra("kind","after-sales").putExtra("order_id","qa-order").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK));
        await(()->text(opened.getWindow().getDecorView(),"测试绘本")!=null);
        getInstrumentation().runOnMainSync(()->{View root=opened.getWindow().getDecorView();((CheckBox)text(root,"测试绘本")).setChecked(true);input(root,"测试绘本 售后数量").setText("1");input(root,"测试绘本 退款金额").setText("3.50");input(root,"申请原因").setText("封面破损");});
        shot("partial-sale-form");getInstrumentation().runOnMainSync(()->text(opened.getWindow().getDecorView(),"提交售后申请").performClick());
        await(()->submitted!=null&&text(opened.getWindow().getDecorView(),"售后进度")!=null);
        assertEquals(350,submitted.getJSONArray("items").getJSONObject(0).getInt("amountCents"));assertEquals(1,submitted.getJSONArray("items").getJSONObject(0).getInt("quantity"));shot("partial-sale-detail");
    }
    public void testForgotPasswordChecksConfirmationAndReturnsToLogin()throws Exception{
        opened=getInstrumentation().startActivitySync(new Intent(activity,ForgotPasswordActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK));
        getInstrumentation().runOnMainSync(()->{((EditText)opened.findViewById(R.id.account)).setText("13900000001");((EditText)opened.findViewById(R.id.password)).setText("new-fixture-password");((EditText)opened.findViewById(R.id.confirmPassword)).setText("different-password");opened.findViewById(R.id.submit).performClick();});
        assertNull(reset);assertEquals("两次输入的密码不一致",((TextView)opened.findViewById(R.id.message)).getText().toString());
        getInstrumentation().runOnMainSync(()->((EditText)opened.findViewById(R.id.confirmPassword)).setText("new-fixture-password"));shot("demo-password-recovery");
        getInstrumentation().runOnMainSync(()->opened.findViewById(R.id.submit).performClick());await(()->opened.isFinishing());assertEquals("13900000001",reset.getString("account"));assertEquals("new-fixture-password",reset.getString("newPassword"));
    }
    public void testRefundStateKeepsRemainingOrderActions()throws Exception{
        JSONObject order=new JSONObject().put("after_sale_state","COMPLETED").put("total_cents",9000).put("refunded_cents",3000);
        assertFalse(AfterSaleState.blocked(order));assertEquals("待发货 · 部分退款",AfterSaleState.describe(order,"待发货"));
        order.put("refunded_cents",9000);assertTrue(AfterSaleState.blocked(order));assertEquals("已全额退款",AfterSaleState.describe(order,"待发货"));
    }
    public void testFullRefundHidesRepeatApplication()throws Exception{
        remaining=false;openEnded();assertNull(text(opened.getWindow().getDecorView(),"对剩余商品再次申请"));shot("full-refund-no-repeat");
    }
    public void testPartialRefundAllowsRepeatApplication()throws Exception{
        remaining=true;openEnded();assertNotNull(text(opened.getWindow().getDecorView(),"对剩余商品再次申请"));shot("partial-refund-repeat");
    }
    private void openEnded()throws Exception{
        opened=getInstrumentation().startActivitySync(new Intent(activity,ServiceActivity.class).putExtra("kind","after-sales").putExtra("record_id","qa-ended").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK));
        await(()->text(opened.getWindow().getDecorView(),"售后完成")!=null);
    }
    public void testEmptyRefundableItemsDoNotOpenSubmissionForm()throws Exception{
        opened=getInstrumentation().startActivitySync(new Intent(activity,ServiceActivity.class).putExtra("kind","after-sales").putExtra("order_id","qa-empty").putExtra("mode","create").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK));
        await(()->text(opened.getWindow().getDecorView(),"当前订单已无可申请售后的商品或金额。")!=null||text(opened.getWindow().getDecorView(),"提交售后申请")!=null);
        assertNull(text(opened.getWindow().getDecorView(),"提交售后申请"));assertNotNull(text(opened.getWindow().getDecorView(),"当前订单已无可申请售后的商品或金额。"));shot("no-refundable-items");
    }
    @Override protected void tearDown()throws Exception{if(opened!=null&&!opened.isFinishing())getInstrumentation().runOnMainSync(opened::finish);super.tearDown();}
}
