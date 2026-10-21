package com.huimeng.bookshop.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import com.huimeng.bookshop.R;

/** Category entry stays compact; searching remains an explicit, scoped action. */
public class CategoryBrowseTest extends RefreshNavigationTest {
    private volatile String catalogPath="";
    @Override protected String fixture(com.android.volley.Request<?> request){
        if(request.getUrl().contains("catalog/products?"))catalogPath=request.getUrl();
        return super.fixture(request);
    }
    public void testCategoryPrioritizesProductsAndSearchKeepsFilter()throws Throwable{
        Activity page=getInstrumentation().startActivitySync(new Intent(activity,SearchActivity.class)
            .putExtra("age","6-9").putExtra("browse_title","6–9岁绘本")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try{
            getInstrumentation().waitForIdleSync();SystemClock.sleep(500);
            runTestOnUiThread(()->{
                View root=page.getWindow().getDecorView();
                assertNull(visibleText(root,"搜索历史"));assertNull(visibleText(root,"清除筛选"));
                assertNull(visibleText(root,"分类"));assertNull(visibleInput(root));
                View list=page.findViewById(R.id.list);
                assertTrue("Products must occupy at least 65 percent of the page",list.getHeight()>page.findViewById(android.R.id.content).getHeight()*.65f);
                View search=findDescription(root,"搜索绘本");assertNotNull(search);search.performClick();
                EditText input=visibleInput(root);assertNotNull(input);input.setText("晚安");
                visibleText(root,"搜索").performClick();
            });
            getInstrumentation().waitForIdleSync();SystemClock.sleep(400);
            android.net.Uri uri=android.net.Uri.parse(catalogPath);
            assertEquals("6-9",uri.getQueryParameter("age"));assertEquals("晚安",uri.getQueryParameter("q"));
            runTestOnUiThread(()->assertNull(visibleInput(page.getWindow().getDecorView())));
        }finally{runTestOnUiThread(page::finish);}
    }
    private View findDescription(View view,String text){
        if(text.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View found=findDescription(((ViewGroup)view).getChildAt(i),text);if(found!=null)return found;}
        return null;
    }
    private View visibleText(View view,String text){
        if(!view.isShown())return null;
        if(view instanceof android.widget.TextView&&((android.widget.TextView)view).getText().toString().contains(text))return view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View found=visibleText(((ViewGroup)view).getChildAt(i),text);if(found!=null)return found;}
        return null;
    }
    private EditText visibleInput(View view){
        if(!view.isShown())return null;if(view instanceof EditText)return (EditText)view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){EditText found=visibleInput(((ViewGroup)view).getChildAt(i));if(found!=null)return found;}
        return null;
    }
}
