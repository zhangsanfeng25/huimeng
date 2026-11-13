package com.huimeng.bookshop.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.SystemClock;
import android.widget.TextView;
import com.huimeng.bookshop.R;
import org.json.JSONArray;
import org.json.JSONObject;

/** Timeline must describe recorded actions, never a full prefilled shipping ladder. */
public class OrderProgressTest extends RefreshNavigationTest {
    private String scenario="paid";
    @Override protected String fixture(com.android.volley.Request<?> request){
        String base=super.fixture(request);
        if(!request.getUrl().endsWith("me/orders/order-test"))return base;
        try{
            JSONObject result=new JSONObject(base),order=result.getJSONObject("data");
            JSONArray events=new JSONArray().put(event("CREATE","PENDING_PAYMENT","2026-10-21T03:00:00Z"));
            if(!scenario.equals("pending"))events.put(event("PAY","PREPARING","2026-10-21T03:01:00Z"));
            if(scenario.equals("shipped"))events.put(event("ADVANCE","READY_TO_SHIP","2026-10-21T03:02:00Z")).put(event("SHIP","SHIPPED","2026-10-21T03:03:00Z"));
            order.put("status",scenario.equals("pending")?"PENDING_PAYMENT":scenario.equals("shipped")?"SHIPPED":"PAID").put("logistics_status",scenario.equals("pending")?"WAITING_PAYMENT":scenario.equals("shipped")?"SHIPPED":"PREPARING").put("events",events);
            order.remove("after_sale_state");order.remove("after_sale_id");
            if(scenario.equals("pending"))order.put("pendingAction","pay");
            if(scenario.equals("refund"))order.put("after_sale_state","COMPLETED").put("after_sale_id","AS1").put("serviceEvents",new JSONArray().put(service("CREATE","2026-10-21T03:02:00Z")).put(service("merchant-review","2026-10-21T03:03:00Z")).put(service("approve","2026-10-21T03:04:00Z")).put(service("refund","2026-10-21T03:05:00Z")));
            return result.toString();
        }catch(Exception e){throw new RuntimeException(e);}
    }
    private JSONObject event(String action,String state,String time)throws Exception{return new JSONObject().put("action",action).put("toStatus",state).put("createdAt",time);}
    private JSONObject service(String action,String time)throws Exception{return new JSONObject().put("action",action).put("type","REFUND_ONLY").put("createdAt",time);}
    private String progress(String state)throws Exception{
        scenario=state;Activity order=getInstrumentation().startActivitySync(new Intent(activity,OrderDetailActivity.class).putExtra("order_id","order-test").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK));
        try{long end=SystemClock.uptimeMillis()+5000;String value="";while(SystemClock.uptimeMillis()<end){value=((TextView)order.findViewById(R.id.timeline)).getText().toString();if(value.contains("2026")){if(order.findViewById(R.id.progressToggle).getVisibility()==android.view.View.VISIBLE)getInstrumentation().runOnMainSync(()->order.findViewById(R.id.progressToggle).performClick());return ((TextView)order.findViewById(R.id.timeline)).getText().toString();}SystemClock.sleep(100);}fail("订单进度未载入");return value;}finally{getInstrumentation().runOnMainSync(order::finish);}
    }
    public void testPaymentIsAnActualCompletedAction()throws Exception{String text=progress("paid");assertTrue(text,text.contains("付款已完成"));assertFalse(text,text.contains("已发货"));assertFalse(text,text.contains("正在运输"));}
    public void testRefundIsMergedAndSortedBeforePayment()throws Exception{String text=progress("refund");assertTrue(text,text.contains("退款已完成"));assertTrue(text,text.indexOf("退款已完成")<text.indexOf("付款已完成"));assertFalse(text,text.contains("已发货"));assertFalse(text,text.contains("退货已寄出"));}
    public void testShippingDoesNotInventTransportOrCompletion()throws Exception{String text=progress("shipped");assertTrue(text,text.contains("商家已发货"));assertTrue(text,text.contains("出库准备已完成"));assertFalse(text,text.contains("正在运输"));assertFalse(text,text.contains("已确认收货"));}
    public void testPendingPaymentDoesNotClaimSuccess()throws Exception{String text=progress("pending");assertTrue(text,text.contains("待付款"));assertFalse(text,text.contains("付款已完成"));assertFalse(text,text.contains("准备出库"));}
}
