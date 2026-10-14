package com.huimeng.bookshop.ui;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import com.android.volley.Request;
import com.google.android.material.textfield.TextInputLayout;
import com.huimeng.bookshop.R;
import java.time.Instant;
import java.time.ZoneId;
import java.io.File;
import java.io.FileOutputStream;


/** Isolated network fixtures exercise the same gestures and transitions as the app. */
@SuppressWarnings("deprecation")
public class InteractionFixesTest extends RefreshNavigationTest {
    private volatile boolean liked;
    private volatile int noticeVersion;
    private volatile String catalogPath="";
    private boolean commentPreview;
    @Override protected String fixture(Request<?> request){
        String url=request.getUrl();
        if(url.contains("catalog/products?"))catalogPath=url;
        if(url.endsWith("community/posts/post-test/like")){
            liked=request.getMethod()==Request.Method.PUT;
            return "{\"liked\":"+liked+",\"likeCount\":"+(liked?3:2)+"}";
        }
        return super.fixture(request);
    }
    @Override protected String fixture(String url){
        if(url.endsWith("me/notifications"))return noticeVersion==0?"{\"data\":[]}":"{\"data\":[{\"id\":\"order-notice\",\"title\":\""+(noticeVersion==1?"订单支付成功":"订单已发货")+"\",\"content\":\"测试订单提醒\",\"created_at\":\"2026-10-09T10:00:00.000Z\",\"is_read\":0}]}";
        if(url.contains("community/users/"))return "{\"data\":{\"user\":{\"id\":\"reader-test\",\"nickname\":\"星星妈妈\"},\"postCount\":1,\"likeCount\":"+(liked?3:2)+",\"posts\":["+post()+"]}}";
        if(url.endsWith("community/posts/post-test"))return "{\"data\":"+post().replace("}",",\"comments\":"+commentRows()+"}")+"}";
        return super.fixture(url);
    }
    private String post(){return "{\"id\":\"post-test\",\"title\":\"共读笔记\",\"content\":\"共读笔记内容\",\"authorId\":\"reader-test\",\"authorName\":\"星星妈妈\",\"authorRole\":\"USER\",\"likeCount\":"+(liked?3:2)+",\"commentCount\":0,\"liked\":"+liked+"}";}
    private String commentRows(){if(!commentPreview)return "[]";java.time.ZonedDateTime now=java.time.ZonedDateTime.now();return "["+commentRow("今天的评论",now.toInstant())+","+commentRow("今年的评论",now.minusDays(1).toInstant())+","+commentRow("往年的评论",now.minusYears(1).toInstant())+"]";}
    private String commentRow(String name,Instant time){return "{\"authorName\":\""+name+"\",\"content\":\"评论时间显示验证\",\"createdAt\":\""+time+"\"}";}
    private void capture(Activity page,String name)throws Exception{
        getInstrumentation().waitForIdleSync();boolean[] focused={false};long deadline=SystemClock.uptimeMillis()+5000;
        while(!focused[0]&&SystemClock.uptimeMillis()<deadline){getInstrumentation().runOnMainSync(()->focused[0]=page.hasWindowFocus());SystemClock.sleep(100);}
        assertTrue("截图前目标页面必须处于前台",focused[0]);SystemClock.sleep(700);
        android.graphics.Bitmap bitmap;
        // Native capture avoids cached or blank UiAutomation frames after system recovery.
        try(java.io.InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(getInstrumentation().getUiAutomation().executeShellCommand("screencap -p"))){bitmap=android.graphics.BitmapFactory.decodeStream(in);}
        assertNotNull("必须取得实际设备画面",bitmap);
        try(FileOutputStream out=new FileOutputStream(new File(page.getExternalFilesDir(null),"interaction-"+name+".png"))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
    }
    private Activity open(Class<?> type,String key,String value){Intent intent=new Intent(activity,type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK);if(key!=null)intent.putExtra(key,value);return getInstrumentation().startActivitySync(intent);}
    private void close(Activity page)throws Throwable{runTestOnUiThread(page::finish);getInstrumentation().waitForIdleSync();}
    public void testLoginInputHintsStayInsideAndDisappearWithText()throws Throwable{
        Activity page=open(LoginActivity.class,null,null);
        try {for(int id:new int[]{R.id.accountInput,R.id.passwordInput}){
            android.widget.EditText field=page.findViewById(id);TextInputLayout box=(TextInputLayout)field.getParent().getParent();
            runTestOnUiThread(()->{field.requestFocus();field.setText("reader-test");});
            assertFalse("输入时不使用上浮提示标签",box.isHintEnabled());assertNotNull("空值时保留框内提示",field.getHint());
        }
        runTestOnUiThread(()->((android.view.inputmethod.InputMethodManager)page.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(page.findViewById(R.id.passwordInput).getWindowToken(),0));capture(page,"login-input");
        }finally{close(page);}
    }
    private Activity tapBrowse(int id)throws Throwable{
        Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(SearchActivity.class.getName(),null,false);
        try {runTestOnUiThread(()->activity.findViewById(id).performClick());Activity page=getInstrumentation().waitForMonitorWithTimeout(monitor,2500);assertNotNull("首页入口应跳转到独立绘本结果页",page);SystemClock.sleep(300);return page;}
        finally{getInstrumentation().removeMonitor(monitor);}
    }
    public void testViewAllOpensAllBooks()throws Throwable{Activity page=tapBrowse(R.id.allProducts);try{assertEquals("全部绘本",((TextView)page.findViewById(R.id.title)).getText());assertTrue(catalogPath.contains("pageSize=12"));capture(page,"all-books");}finally{close(page);}}
    public void testEveryHomeCategoryOpensItsFilteredResults()throws Throwable{
        int[] ids={R.id.ageBaby,R.id.agePreschool,R.id.ageSchool,R.id.ageOlder,R.id.themeBedtime,R.id.themeEmotion,R.id.themeScience};
        String[] filters={"age=0-3","age=3-6","age=6-9","age=9-plus","theme=bedtime","theme=emotion","theme=science"};
        for(int i=0;i<ids.length;i++){Activity page=tapBrowse(ids[i]);try{assertTrue("结果接口保留所选分类："+filters[i],catalogPath.contains(filters[i]));if(i==1||i==4)capture(page,i==1?"age-results":"theme-results");}finally{close(page);}}
    }
    public void testManualCarouselWrapsForwardAndBackward()throws Throwable{
        BannerCarouselView carousel=activity.findViewById(R.id.bannerCarousel);runTestOnUiThread(()->carousel.setActive(false));
        assertEquals(0,carousel.currentPage());
        for(int index=1;index<=5;index++){swipe(carousel,true);assertEquals("向后滑动应首尾循环",index%5,carousel.currentPage());}
        capture(activity,"carousel-wrap-first");
        swipe(carousel,false);assertEquals("第一张反向滑动可到最后一张",4,carousel.currentPage());
        capture(activity,"carousel-wrap-last");
    }
    private void swipe(View target,boolean forward)throws Throwable{
        int[] location=new int[2];runTestOnUiThread(()->target.getLocationOnScreen(location));long down=SystemClock.uptimeMillis();
        for(int step=0;step<=12;step++){float fraction=forward?.85f-.7f*step/12:.15f+.7f*step/12;int action=step==0?MotionEvent.ACTION_DOWN:step==12?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE;MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,location[0]+target.getWidth()*fraction,location[1]+target.getHeight()/2f,0);getInstrumentation().sendPointerSync(event);event.recycle();SystemClock.sleep(16);}
        SystemClock.sleep(900);getInstrumentation().waitForIdleSync();
    }
    public void testLikedPostKeepsViewerStateInAuthorProfile()throws Throwable{
        Activity detail=open(CommunityDetailActivity.class,"post_id","post-test");Activity author=null;
        try {SystemClock.sleep(400);runTestOnUiThread(()->detail.findViewById(R.id.like).performClick());SystemClock.sleep(400);
            Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(UserProfileActivity.class.getName(),null,false);
            try{runTestOnUiThread(()->detail.findViewById(R.id.avatar).performClick());author=getInstrumentation().waitForMonitorWithTimeout(monitor,2500);assertNotNull(author);}finally{getInstrumentation().removeMonitor(monitor);}
            SystemClock.sleep(400);assertTrue(((TextView)author.findViewById(R.id.meta)).getContentDescription().toString().startsWith("已点赞"));capture(author,"author-liked");close(author);author=null;
            runTestOnUiThread(()->detail.findViewById(R.id.like).performClick());SystemClock.sleep(400);
            author=open(UserProfileActivity.class,"user_id","reader-test");SystemClock.sleep(400);assertTrue(((TextView)author.findViewById(R.id.meta)).getContentDescription().toString().startsWith("未点赞"));capture(author,"author-unliked");
        }finally{if(author!=null)close(author);close(detail);}
    }
    public void testCommentsUseLocalTodayMonthAndYear()throws Throwable{
        java.lang.reflect.Method format=Class.forName("com.huimeng.bookshop.core.CommentTime").getMethod("format",String.class,Instant.class,ZoneId.class);
        Instant now=Instant.parse("2026-10-09T00:05:00Z");ZoneId zone=ZoneId.of("Asia/Shanghai");
        assertEquals("08:00",format.invoke(null,"2026-10-09T00:00:00.000Z",now,zone));
        assertEquals("10-08",format.invoke(null,"2026-10-08T15:59:00.000Z",now,zone));
        assertEquals("10-08",format.invoke(null,"2026-10-08T15:59:00Z",now,zone));
        assertEquals("2025-12-01",format.invoke(null,"2025-12-01T01:30:00Z",now,zone));
        assertEquals("2024-03-02",format.invoke(null,"2024-03-02T01:30:00Z",now,zone));
        assertEquals("00:00",format.invoke(null,"2026-10-08T16:00:00Z",now,zone));
        commentPreview=true;Activity detail=open(CommunityDetailActivity.class,"post_id","post-test");
        try{SystemClock.sleep(400);assertEquals(3,((RecyclerView)detail.findViewById(R.id.comments)).getAdapter().getItemCount());capture(detail,"comment-times");}finally{close(detail);}
    }
    public void testMessagesRefreshOnTabReturnAndWhileVisible()throws Throwable{
        runTestOnUiThread(()->activity.selectTab(3));SystemClock.sleep(400);runTestOnUiThread(()->activity.selectTab(1));noticeVersion=1;
        runTestOnUiThread(()->activity.selectTab(3));SystemClock.sleep(700);assertTrue("回到消息页应读取最新订单消息",contains(activity.getSupportFragmentManager().getPrimaryNavigationFragment().requireView(),"订单支付成功"));
        noticeVersion=2;SystemClock.sleep(5800);assertTrue("消息页保持可见时自动同步后续状态",contains(activity.getSupportFragmentManager().getPrimaryNavigationFragment().requireView(),"订单已发货"));
        capture(activity,"order-message");
    }
    private boolean contains(View view,String value){if(view instanceof TextView&&((TextView)view).getText().toString().contains(value))return true;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)if(contains(group.getChildAt(i),value))return true;}return false;}
}
