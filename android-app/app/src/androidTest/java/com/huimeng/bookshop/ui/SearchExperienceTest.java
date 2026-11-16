package com.huimeng.bookshop.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.textfield.TextInputEditText;
import com.huimeng.bookshop.R;

public class SearchExperienceTest extends RefreshNavigationTest {
    private Activity search;private volatile String requestedQuery="";
    @Override protected String fixture(com.android.volley.Request<?> request){if(request.getUrl().contains("catalog/products?"))requestedQuery=android.net.Uri.parse(request.getUrl()).getQueryParameter("q");return super.fixture(request);}
    private TextView text(View root,String value){if(root instanceof TextView&&value.contentEquals(((TextView)root).getText()))return (TextView)root;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){TextView found=text(((ViewGroup)root).getChildAt(i),value);if(found!=null)return found;}return null;}
    private TextInputEditText input(View root){if(root instanceof TextInputEditText)return (TextInputEditText)root;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){TextInputEditText found=input(((ViewGroup)root).getChildAt(i));if(found!=null)return found;}return null;}
    private void launch(){search=getInstrumentation().startActivitySync(new Intent(activity,SearchActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK));getInstrumentation().waitForIdleSync();}
    private void screenshot(String name)throws Exception{getInstrumentation().waitForIdleSync();var image=getInstrumentation().getUiAutomation().takeScreenshot();if(image!=null)try(var out=new java.io.FileOutputStream(new java.io.File(activity.getExternalFilesDir(null),name))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{image.recycle();}}
    public void testEmptySearchOnlyShowsHistoryAndNoCatalogFilters()throws Throwable{
        launch();SystemClock.sleep(500);runTestOnUiThread(()->{View root=search.getWindow().getDecorView();assertNull(text(root,"分类"));assertNull(text(root,"综合"));assertNull(text(root,"最新"));assertNotNull(text(root,"搜索历史"));assertNotNull(text(root,"清空"));assertEquals(0,((RecyclerView)search.findViewById(R.id.list)).getAdapter().getItemCount());});screenshot("search-empty.png");
    }
    public void testTypingShowsCandidatesAndClearingDiscardsOldResults()throws Throwable{
        launch();getInstrumentation().runOnMainSync(()->input(search.getWindow().getDecorView()).setText("晚安"));
        long end=SystemClock.uptimeMillis()+5000;int[] count={0};while(SystemClock.uptimeMillis()<end){getInstrumentation().runOnMainSync(()->count[0]=((RecyclerView)search.findViewById(R.id.list)).getAdapter().getItemCount());if(count[0]>0&&"晚安".equals(requestedQuery))break;SystemClock.sleep(80);}assertEquals("晚安",requestedQuery);assertTrue("输入后应自动显示候选商品",count[0]>0);screenshot("search-candidates.png");
        runTestOnUiThread(()->{View root=search.getWindow().getDecorView();text(root,"搜索").performClick();});SystemClock.sleep(200);
        getInstrumentation().runOnMainSync(()->input(search.getWindow().getDecorView()).setText(""));SystemClock.sleep(600);
        runTestOnUiThread(()->{assertEquals(0,((RecyclerView)search.findViewById(R.id.list)).getAdapter().getItemCount());View root=search.getWindow().getDecorView();assertNotNull(text(root,"晚安"));text(root,"清空").performClick();assertNull(text(root,"晚安"));});
    }
    @Override protected void tearDown()throws Exception{if(search!=null)getInstrumentation().runOnMainSync(search::finish);super.tearDown();}
}
