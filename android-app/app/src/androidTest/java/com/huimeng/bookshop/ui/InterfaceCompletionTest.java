package com.huimeng.bookshop.ui;

import android.app.Activity;
import android.content.Intent;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.huimeng.bookshop.R;

/** Exercises rendered screens; no test writes to the business database. */
@SuppressWarnings("deprecation")
public class InterfaceCompletionTest extends InstrumentationTestCase {
    private Activity activity;
    private Activity open(String name,String extra) throws Exception {
        Class<?> type;
        try{type=Class.forName("com.huimeng.bookshop.ui."+name);}catch(ClassNotFoundException e){fail("缺少可导航页面："+name);return null;}
        Intent intent=new Intent(getInstrumentation().getTargetContext(),type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if(extra!=null)intent.putExtra("product_id",extra);
        activity=getInstrumentation().startActivitySync(intent);getInstrumentation().waitForIdleSync();return activity;
    }
    private boolean contains(View view,String text){if(view instanceof TextView&&String.valueOf(((TextView)view).getText()).contains(text))return true;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)if(contains(((ViewGroup)view).getChildAt(i),text))return true;return false;}
    public void testSearchScreenHasHistoryAndSort() throws Exception {open("SearchActivity",null);assertTrue(contains(activity.getWindow().getDecorView(),"搜索绘本"));assertTrue(contains(activity.getWindow().getDecorView(),"搜索历史"));assertTrue(contains(activity.getWindow().getDecorView(),"价格"));}
    public void testCategoriesScreenHasDistinctPage() throws Exception {open("CategoriesActivity",null);assertTrue(contains(activity.getWindow().getDecorView(),"绘本分类"));}
    public void testRatingsScreenHasIndependentTitle() throws Exception {open("RatingsActivity","P0001");assertTrue(contains(activity.getWindow().getDecorView(),"读者评价"));}
    public void testApplicationScreenHasFormAndTerms() throws Exception {open("MerchantApplicationActivity",null);assertTrue(contains(activity.getWindow().getDecorView(),"申请成为商家"));assertTrue(contains(activity.getWindow().getDecorView(),"真实"));}
    public void testRoleSwitchHasApplicationEntry() throws Exception {
        activity=getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(),RoleSwitchActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        getInstrumentation().waitForIdleSync();
        View application=activity.findViewById(R.id.applyMerchant);
        assertNotNull("申请入口在未申请、审核中和已通过状态下均保留",application);
        android.app.Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(MerchantApplicationActivity.class.getName(),null,false);
        try {
            getInstrumentation().runOnMainSync(application::performClick);
            Activity form=getInstrumentation().waitForMonitorWithTimeout(monitor,3000);
            assertNotNull("申请入口应打开申请资料或进度页面",form);
            getInstrumentation().runOnMainSync(form::finish);
        } finally {getInstrumentation().removeMonitor(monitor);}
    }
    @Override protected void tearDown() throws Exception {if(activity!=null)getInstrumentation().runOnMainSync(()->activity.finish());super.tearDown();}
}
