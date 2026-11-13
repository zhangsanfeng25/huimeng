package com.huimeng.bookshop.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.SystemClock;
import android.widget.TextView;
import com.huimeng.bookshop.R;
import org.json.JSONArray;
import org.json.JSONObject;

public class OrderDetailInteractionTest extends RefreshNavigationTest {
    private boolean deliveryFixture;
    private final String shipmentTime=java.time.Instant.now().toString();
    @Override protected String fixture(com.android.volley.Request<?> request){
        String base=super.fixture(request);if(!deliveryFixture||!request.getUrl().endsWith("me/orders/order-test"))return base;
        try{
            JSONObject result=new JSONObject(base),order=result.getJSONObject("data");
            order.put("id","local-order-test").put("remoteId","HM202610250001").put("confirmedState","SHIPPED").put("status","SHIPPED").put("logistics_status","OUT_FOR_DELIVERY")
                .put("shipped_at",shipmentTime).put("events",new JSONArray()
                    .put(new JSONObject().put("toStatus","SHIPPED").put("createdAt","2026-10-25T00:00:00Z"))
                    .put(new JSONObject().put("toStatus","OUT_FOR_DELIVERY").put("createdAt","2026-10-25T01:00:00Z")));
            order.remove("after_sale_state");order.remove("after_sale_id");return result.toString();
        }catch(Exception e){throw new RuntimeException(e);}
    }
    private void capture(String name)throws Exception{
        java.io.File folder=getInstrumentation().getTargetContext().getExternalFilesDir("order-qa");
        try(java.io.FileOutputStream stream=new java.io.FileOutputStream(new java.io.File(folder,name))){getInstrumentation().getUiAutomation().takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream);}
    }
    public void testLatestByDefaultExpandRefreshAndCollapse()throws Exception{
        deliveryFixture=true;
        Activity detail=getInstrumentation().startActivitySync(new Intent(activity,OrderDetailActivity.class).putExtra("order_id","order-test").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try{
            TextView timeline=detail.findViewById(R.id.timeline);long end=SystemClock.uptimeMillis()+6000;
            while(!timeline.getText().toString().contains("订单正在派送")&&SystemClock.uptimeMillis()<end)SystemClock.sleep(100);
            assertTrue(timeline.getText().toString().contains("订单正在派送"));assertFalse(timeline.getText().toString().contains("商家已发货"));
            assertEquals("订单号：HM202610250001",((TextView)detail.findViewById(R.id.orderId)).getText().toString());
            assertTrue(((TextView)detail.findViewById(R.id.autoReceipt)).getText().toString().contains("30天后"));capture("order-collapsed.png");
            getInstrumentation().runOnMainSync(()->detail.findViewById(R.id.progressToggle).performClick());
            assertTrue(timeline.getText().toString().contains("商家已发货"));
            getInstrumentation().runOnMainSync(()->((TextView)detail.findViewById(R.id.autoReceipt)).setText("过期的倒计时提示"));
            SystemClock.sleep(5500);assertTrue(timeline.getText().toString().contains("商家已发货"));
            assertTrue("相同订单数据也应刷新倒计时",((TextView)detail.findViewById(R.id.autoReceipt)).getText().toString().contains("30天后"));capture("order-expanded.png");
            getInstrumentation().runOnMainSync(()->detail.findViewById(R.id.progressToggle).performClick());
            assertFalse(timeline.getText().toString().contains("商家已发货"));
        }finally{getInstrumentation().runOnMainSync(detail::finish);}
    }
}
