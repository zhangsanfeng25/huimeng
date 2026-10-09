package com.huimeng.bookshop.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;
import com.huimeng.bookshop.R;
import com.huimeng.bookshop.core.WallpaperStore;
import com.huimeng.bookshop.model.CommunityPost;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;

/** Uses the same isolated Volley fixture as the regression suite. */
@SuppressWarnings("deprecation")
public class UiPolishPreviewTest extends RefreshNavigationTest {
    private void capture(Activity page,String name)throws Exception{
        SystemClock.sleep(700);android.graphics.Bitmap bitmap=getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(bitmap);
        try(FileOutputStream stream=new FileOutputStream(new File(page.getExternalFilesDir(null),"polish-"+name+".png"))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream);}bitmap.recycle();
    }
    private Activity open(Class<?> type)throws Exception{return getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(),type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK));}
    public void testWelcomePreviewAndSkip()throws Throwable{
        getInstrumentation().getTargetContext().getSharedPreferences("huimeng_session",0).edit().putBoolean("remember_login",true).putString("token","device-test").commit();
        android.app.Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(MainActivity.class.getName(),null,false);
        Activity welcome=null, next=null;
        try {
            welcome=open(WelcomeActivity.class);
            assertNotNull(welcome.findViewById(R.id.welcomeArtwork));
            capture(welcome,"welcome");
            View skip=welcome.findViewById(R.id.skip);
            runTestOnUiThread(skip::performClick);
            next=getInstrumentation().waitForMonitorWithTimeout(monitor,5000);
            assertNotNull("跳过欢迎页后打开主页",next);
        } finally {
            getInstrumentation().removeMonitor(monitor);
            if(next!=null){Activity page=next;runTestOnUiThread(page::finish);}
            if(welcome!=null){Activity page=welcome;runTestOnUiThread(page::finish);}
        }
    }
    public void testWelcomeAutomaticallyOpensMain()throws Throwable{
        getInstrumentation().getTargetContext().getSharedPreferences("huimeng_session",0).edit().putBoolean("remember_login",true).putString("token","device-test").commit();
        android.app.Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(MainActivity.class.getName(),null,false);
        Activity welcome=null, next=null;
        try {
            welcome=open(WelcomeActivity.class);
            next=getInstrumentation().waitForMonitorWithTimeout(monitor,6000);
            assertNotNull("欢迎页计时结束后自动打开主页",next);
        } finally {
            getInstrumentation().removeMonitor(monitor);
            if(next!=null){Activity page=next;runTestOnUiThread(page::finish);}
            if(welcome!=null){Activity page=welcome;runTestOnUiThread(page::finish);}
        }
    }
    public void testRenderPreviews()throws Throwable{
        Activity login=open(LoginActivity.class);capture(login,"login");runTestOnUiThread(login::finish);
        Activity register=open(RegisterActivity.class);capture(register,"register");runTestOnUiThread(register::finish);
        new WallpaperStore(getInstrumentation().getTargetContext()).setWallpaper(WallpaperStore.OCEAN);
        MainActivity main=(MainActivity)open(MainActivity.class);capture(main,"home-nav");
        runTestOnUiThread(()->main.selectTab(4));SystemClock.sleep(300);
        runTestOnUiThread(()->{View logout=main.findViewById(R.id.logout);logout.getParent().requestChildFocus(logout,logout);androidx.core.widget.NestedScrollView scroll=findScroll(main.findViewById(android.R.id.content));if(scroll!=null)scroll.fullScroll(View.FOCUS_DOWN);});capture(main,"profile-logout");
        Activity addresses=open(AddressesActivity.class);capture(addresses,"addresses");
        runTestOnUiThread(()->((TextView)addresses.findViewById(R.id.detail)).setText("辽宁省大连市高新区星海路 18 号 阅读书屋六楼 602 室（请联系收货人后送达，长地址完整换行验证）"));capture(addresses,"addresses-long");runTestOnUiThread(addresses::finish);
        Activity author=getInstrumentation().startActivitySync(new Intent(main,UserProfileActivity.class).putExtra("user_id","reader-test").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));SystemClock.sleep(600);
        TextView meta=author.findViewById(R.id.meta);
        runTestOnUiThread(()->{try{PostLikeDisplay.bind(meta,CommunityPost.from(new JSONObject().put("id","preview").put("likeCount",0).put("liked",0)),true);}catch(Exception e){throw new RuntimeException(e);}});capture(author,"author-unliked");
        runTestOnUiThread(()->{try{PostLikeDisplay.bind(meta,CommunityPost.from(new JSONObject().put("id","preview").put("likeCount",2).put("liked",1)),true);}catch(Exception e){throw new RuntimeException(e);}});capture(author,"author-liked");runTestOnUiThread(author::finish);
        android.content.SharedPreferences session=main.getSharedPreferences("huimeng_session",0);session.edit().putString("role","MERCHANT").commit();runTestOnUiThread(main::finish);
        MainActivity merchant=(MainActivity)open(MainActivity.class);capture(merchant,"merchant-nav");runTestOnUiThread(merchant::finish);
    }
    private androidx.core.widget.NestedScrollView findScroll(View view){if(view instanceof androidx.core.widget.NestedScrollView)return (androidx.core.widget.NestedScrollView)view;if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++){androidx.core.widget.NestedScrollView found=findScroll(group.getChildAt(i));if(found!=null)return found;}}return null;}
}
