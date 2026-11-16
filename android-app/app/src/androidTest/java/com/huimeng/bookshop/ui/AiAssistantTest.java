package com.huimeng.bookshop.ui;

import android.app.Activity;
import android.app.Instrumentation;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.os.SystemClock;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import com.huimeng.bookshop.R;
import com.huimeng.bookshop.core.AiConversation;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class AiAssistantTest extends RefreshNavigationTest {
    private final CountDownLatch releaseReply = new CountDownLatch(1);
    private boolean longReply;
    private volatile String latestPrompt;

    @Override protected void setUp() throws Exception {
        getInstrumentation().runOnMainSync(AiConversation::clear);
        super.setUp();
    }

    @Override protected String fixture(com.android.volley.Request<?> request) {
        if(request.getUrl().endsWith("/ai/chat")){
            try {
                latestPrompt=new org.json.JSONObject(new String(request.getBody(),java.nio.charset.StandardCharsets.UTF_8)).getString("message");
            } catch (org.json.JSONException | com.android.volley.AuthFailureError error) { throw new AssertionError(error); }
            try {
                releaseReply.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            if (longReply) return new org.json.JSONObject(java.util.Map.of("text",
                "历史回复开头\n" + "这一段文字用来验证聊天窗口可以上下滚动，并阅读完整回复。\n".repeat(24) + "历史回复结尾")).toString();
            if ("推荐".equals(latestPrompt)) return "{\"text\":\"请选择年龄\",\"suggestions\":[\"6–9岁\"]}";
            if ("6–9岁".equals(latestPrompt)) return "{\"text\":\"已按年龄筛选\"}";
            return """
                {"text":"推荐答复已生成","mode":"demo","products":[
                  {"id":"book-0","name":"推荐绘本测试","priceCents":2800,"reason":"标注适读年龄 3–6 岁"}
                ]}
                """;
        }
        return super.fixture(request);
    }

    public void testRecommendationAgeChoiceSendsFollowUp() {
        releaseReply.countDown();
        var automation=getInstrumentation().getUiAutomation();
        var serviceInfo=automation.getServiceInfo();
        serviceInfo.flags|=AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        automation.setServiceInfo(serviceInfo);
        getInstrumentation().runOnMainSync(ball()::performClick);
        await("直接提问，例如「推荐一本适合 5 岁孩子的绘本」");
        click("全屏");
        await("半屏");
        String inputId=getInstrumentation().getTargetContext().getResources().getResourceName(R.id.message);
        var input=automation.getRootInActiveWindow().findAccessibilityNodeInfosByViewId(inputId).get(0);
        Bundle arguments=new Bundle();
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"推荐");
        assertTrue(input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,arguments));
        click("发送");
        click("6–9岁");
        await("已按年龄筛选");
        assertEquals("6–9岁",latestPrompt);
    }

    public void testHalfSheetHistoryScrollsInBothDirections() {
        longReply=true;
        releaseReply.countDown();
        var automation=getInstrumentation().getUiAutomation();
        var serviceInfo=automation.getServiceInfo();
        serviceInfo.flags|=AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        automation.setServiceInfo(serviceInfo);
        getInstrumentation().runOnMainSync(ball()::performClick);
        await("直接提问，例如「推荐一本适合 5 岁孩子的绘本」");
        String prefix=getInstrumentation().getTargetContext().getPackageName()+":id/";
        var input=automation.getRootInActiveWindow().findAccessibilityNodeInfosByViewId(prefix+"message").get(0);
        Bundle arguments=new Bundle();
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"查看一条长回复");
        assertTrue(input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,arguments));
        click("发送");
        long deadline=SystemClock.uptimeMillis()+6000;
        AccessibilityNodeInfo history=null;
        while(SystemClock.uptimeMillis()<deadline){
            history=automation.getRootInActiveWindow().findAccessibilityNodeInfosByViewId(prefix+"history_scroll").get(0);
            if(history.isScrollable())break;
            SystemClock.sleep(50);
        }
        assertNotNull(history);
        assertTrue(history.isScrollable());
        var sheet=automation.getRootInActiveWindow().findAccessibilityNodeInfosByViewId(prefix+"expand").get(0);
        var original=new android.graphics.Rect();sheet.getBoundsInScreen(original);
        var area=new android.graphics.Rect();history.getBoundsInScreen(area);
        swipe(area.centerX(),area.bottom-20,area.top+20);
        var result=automation.getRootInActiveWindow().findAccessibilityNodeInfosByViewId(prefix+"history_scroll").get(0);
        assertTrue(result.getActionList().contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD));
        swipe(area.centerX(),area.top+20,area.bottom-20);
        swipe(area.centerX(),area.top+20,area.bottom-20);
        var position=new android.graphics.Rect();
        var controls=automation.getRootInActiveWindow().findAccessibilityNodeInfosByViewId(prefix+"expand");
        assertFalse("向下滑动聊天记录时弹层应保持打开",controls.isEmpty());
        controls.get(0).getBoundsInScreen(position);
        assertEquals("拖动聊天记录时弹层应保持原位",original,position);
    }

    private void swipe(int x,int from,int to){
        var gesture=getInstrumentation().getUiAutomation().executeShellCommand("input swipe "+x+" "+from+" "+x+" "+to+" 450");
        try(var stream=new android.os.ParcelFileDescriptor.AutoCloseInputStream(gesture)){
            stream.readAllBytes();
        }catch(java.io.IOException error){throw new AssertionError(error);}
        getInstrumentation().waitForIdleSync();
    }

    public void testAssistantExpandsAndKeepsConversationAfterOpeningProduct() throws Exception {
        var automation=getInstrumentation().getUiAutomation();
        var serviceInfo=automation.getServiceInfo();
        serviceInfo.flags|=AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        automation.setServiceInfo(serviceInfo);
        View ball=ball();
        getInstrumentation().runOnMainSync(ball::performClick);
        click("全屏");
        await("半屏");
        click("半屏");
        await("全屏");
        await("直接提问，例如「推荐一本适合 5 岁孩子的绘本」");
        String resourceId=getInstrumentation().getTargetContext().getResources().getResourceName(R.id.message);
        var inputs=automation.getRootInActiveWindow().findAccessibilityNodeInfosByViewId(resourceId);
        assertEquals("聊天输入框应可编辑",1,inputs.size());
        var input=inputs.get(0);
        Bundle arguments=new Bundle();
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"推荐一本适合 5 岁孩子的绘本");
        assertTrue(input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,arguments));
        click("发送");
        try {
            await("正在生成答复…");
            await("推荐一本适合 5 岁孩子的绘本");
        } finally {
            releaseReply.countDown();
        }
        await("推荐答复已生成");
        screenshot("ai-half-sheet");

        Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(ProductDetailActivity.class.getName(),null,false);
        Activity product=null;
        try {
            click("查看绘本：推荐绘本测试");
            product=getInstrumentation().waitForMonitorWithTimeout(monitor,5000);
            assertNotNull("推荐卡片应打开现有商品详情",product);
            assertEquals("book-0",product.getIntent().getStringExtra("product_id"));
        } finally {
            getInstrumentation().removeMonitor(monitor);
            if(product!=null)getInstrumentation().runOnMainSync(product::finish);
        }
        getInstrumentation().waitForIdleSync();
        getInstrumentation().runOnMainSync(ball::performClick);
        await("推荐答复已生成");
        click("收起");
    }

    public void testBuyerProfileKeepsMerchantApplicationEntry() {
        getInstrumentation().runOnMainSync(()->activity.selectTab(4));
        Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(MerchantApplicationActivity.class.getName(),null,false);
        Activity form=null;
        Activity settings=null;
        try {
            var settingsMonitor=getInstrumentation().addMonitor(ProfileSettingsActivity.class.getName(),null,false);
            getInstrumentation().waitForIdleSync();
            getInstrumentation().runOnMainSync(()->activity.findViewById(R.id.settings).performClick());
            settings=getInstrumentation().waitForMonitorWithTimeout(settingsMonitor,5000);
            assertNotNull(settings);
            Activity page=settings;
            getInstrumentation().runOnMainSync(()->page.findViewById(R.id.merchantApplication).performClick());
            form=getInstrumentation().waitForMonitorWithTimeout(monitor,15000);
            assertNotNull("申请入口应打开申请资料或进度页面",form);
        } finally {
            getInstrumentation().removeMonitor(monitor);
            if(form!=null)getInstrumentation().runOnMainSync(form::finish);
            if(settings!=null)getInstrumentation().runOnMainSync(settings::finish);
        }
    }

    public void testFloatingBallSnapsToEdgeAndSavesPosition() {
        View ball=ball();
        getInstrumentation().runOnMainSync(()->{
            long down=SystemClock.uptimeMillis();
            float shift=-((View)ball.getParent()).getWidth();
            dispatch(ball,down,MotionEvent.ACTION_DOWN,0,0);
            dispatch(ball,down,MotionEvent.ACTION_MOVE,shift,-100);
            dispatch(ball,down,MotionEvent.ACTION_UP,shift,-100);
        });
        var placement=activity.getSharedPreferences("huimeng_ai_placement_USER",0);
        assertFalse(placement.getBoolean("right",true));
        assertTrue(placement.contains("height"));
        long deadline=SystemClock.uptimeMillis()+2000;
        while(SystemClock.uptimeMillis()<deadline){
            boolean[] snapped={false};
            getInstrumentation().runOnMainSync(()->snapped[0]=ball.getX()<=Math.round(8*ball.getResources().getDisplayMetrics().density)+1);
            if(snapped[0])return;
            SystemClock.sleep(30);
        }
        fail("悬浮球未贴到左侧");
    }

    private static void dispatch(View view,long down,int action,float x,float y){
        MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);
        try{view.dispatchTouchEvent(event);}finally{event.recycle();}
    }

    private View ball(){
        ArrayList<View> views=new ArrayList<>();
        getInstrumentation().runOnMainSync(()->activity.getWindow().getDecorView().findViewsWithText(
            views,"打开绘梦 AI 助手",View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION));
        assertEquals(1,views.size());
        return views.get(0);
    }

    private AccessibilityNodeInfo await(String text){
        long deadline=SystemClock.uptimeMillis()+8000;
        while(SystemClock.uptimeMillis()<deadline){
            AccessibilityNodeInfo root=getInstrumentation().getUiAutomation().getRootInActiveWindow();
            if(root!=null){
                for(AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(text)){
                    CharSequence title=node.getText(),description=node.getContentDescription();
                    if(node.isVisibleToUser()&&(title!=null&&text.contentEquals(title)||description!=null&&description.toString().startsWith(text)))return node;
                }
            }
            SystemClock.sleep(80);
        }
        fail("未看到 "+text);
        return null;
    }

    private void click(String text){
        AccessibilityNodeInfo node=await(text);
        while(node!=null&&!node.isClickable())node=node.getParent();
        assertNotNull("未找到可点击控件 "+text,node);
        assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK));
    }

    private void screenshot(String name)throws Exception{
        android.graphics.Bitmap bitmap=getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        java.io.File folder=getInstrumentation().getTargetContext().getExternalFilesDir(null);
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(folder,name+".png"))){
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);
        } finally {bitmap.recycle();}
    }
}
