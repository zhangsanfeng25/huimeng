package com.huimeng.bookshop.ui;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LightingColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.huimeng.bookshop.BuildConfig;
import com.huimeng.bookshop.R;
import com.huimeng.bookshop.core.ApiClient;
import com.huimeng.bookshop.core.LocalCartStore;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONObject;

/** Behavior checks use local fixtures; the cover oracle is recorded from the previous APK. */
@SuppressWarnings("deprecation")
public class OptimizationPreservationTest extends RefreshNavigationTest {
    private volatile String catalogPath="";
    @Override protected String fixture(com.android.volley.Request<?> request){
        if(request.getUrl().contains("catalog/products?"))catalogPath=request.getUrl();
        return super.fixture(request);
    }
    public void testCartQuantityPreservesHistoricalJsonRules()throws Exception{
        Context context=getInstrumentation().getTargetContext();
        SharedPreferences prefs=context.getSharedPreferences("huimeng_cart",Context.MODE_PRIVATE);
        Constructor<LocalCartStore> constructor=LocalCartStore.class.getDeclaredConstructor(Context.class,String.class);
        constructor.setAccessible(true);LocalCartStore cart=constructor.newInstance(context,"optimization-test");
        prefs.edit().putString("cart_optimization-test","[null,17,\"bad\",{},"+
            "{\"id\":\"\",\"quantity\":99},{\"id\":\"dup\",\"quantity\":0},{\"id\":\"dup\",\"quantity\":9},"+
            "{\"id\":\"negative\",\"quantity\":-7},{\"id\":\"missing\"},{\"id\":\"string\",\"quantity\":\"4\"},"+
            "{\"id\":\"fraction\",\"quantity\":2.8},{\"id\":\"invalid\",\"quantity\":\"oops\"}]").commit();
        assertEquals(0,cart.quantity(""));assertEquals(0,cart.quantity(null));assertEquals(0,cart.quantity("absent"));
        assertEquals("First duplicate wins, and zero still normalizes to one",1,cart.quantity("dup"));
        assertEquals(1,cart.quantity("negative"));assertEquals(1,cart.quantity("missing"));
        assertEquals(4,cart.quantity("string"));assertEquals(2,cart.quantity("fraction"));assertEquals(1,cart.quantity("invalid"));
        prefs.edit().putString("cart_optimization-test","not valid JSON").commit();assertEquals(0,cart.quantity("dup"));
        prefs.edit().remove("cart_optimization-test").commit();assertEquals(0,cart.quantity("dup"));
    }
    public void testAssetUrlsKeepWhitespaceAndAbsoluteUrlRules(){
        String origin=BuildConfig.API_BASE_URL.replaceFirst("/api/v1/$","");
        assertEquals("",ApiClient.assetUrl(null));assertEquals("",ApiClient.assetUrl(" \t "));
        assertEquals("https://images.example/a.png?x=1",ApiClient.assetUrl("https://images.example/a.png?x=1"));
        assertEquals("http://images.example/a.png",ApiClient.assetUrl("http://images.example/a.png"));
        assertEquals(origin+"/public/a.png",ApiClient.assetUrl("/public/a.png"));
        assertEquals(origin+"/public/a.png",ApiClient.assetUrl("public/a.png"));
        assertEquals(origin+"/ public/a.png ",ApiClient.assetUrl(" public/a.png "));
    }
    public void testSearchRecreationPreservesFiltersDraftAndSort()throws Throwable{checkSearchRecreation(false);}
    public void testClearedFiltersStayClearedAfterSearchRecreation()throws Throwable{checkSearchRecreation(true);}
    private void checkSearchRecreation(boolean clear)throws Throwable{
        Application app=(Application)activity.getApplication();AtomicReference<Activity> current=new AtomicReference<>();
        AtomicReference<Activity> original=new AtomicReference<>();
        AtomicReference<Bundle> saved=new AtomicReference<>(),restored=new AtomicReference<>();
        Application.ActivityLifecycleCallbacks callbacks=new Application.ActivityLifecycleCallbacks(){
            public void onActivityResumed(Activity page){if(page instanceof SearchActivity)current.set(page);}
            public void onActivityCreated(Activity page,Bundle state){
                if(page instanceof SearchActivity&&original.get()!=null&&page!=original.get())restored.set(state==null?null:new Bundle(state));
            }
            public void onActivityStarted(Activity page){}
            public void onActivityPaused(Activity page){} public void onActivityStopped(Activity page){}
            public void onActivitySaveInstanceState(Activity page,Bundle state){if(page==original.get())saved.set(new Bundle(state));}
            public void onActivityDestroyed(Activity page){}
        };
        app.registerActivityLifecycleCallbacks(callbacks);
        Activity first=null;
        try{
            first=getInstrumentation().startActivitySync(new Intent(activity,SearchActivity.class)
                .putExtra("search_mode",clear).putExtra("query","晚安").putExtra("category","age3").putExtra("category_name","启蒙")
                .putExtra("age","3-6").putExtra("theme","bedtime").putExtra("browse_title","测试分类")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK));
            original.set(first);final Activity page=first;awaitFocused(page);
            runTestOnUiThread(()->{
                if(!clear)page.findViewById(R.id.search).performClick();
                findInput(page.getWindow().getDecorView()).setText("尚未搜索的草稿");
                findText(page.getWindow().getDecorView(),"价格").performClick();
                findText(page.getWindow().getDecorView(),"价格").performClick();
                if(clear)findText(page.getWindow().getDecorView(),"清除筛选").performClick();
                assertEquals("草稿必须先实际写入原页面", "尚未搜索的草稿",findInput(page.getWindow().getDecorView()).getText().toString());
                assertEquals(clear?"全部绘本":"测试分类",((TextView)page.findViewById(R.id.title)).getText().toString());
            });
            getInstrumentation().waitForIdleSync();SystemClock.sleep(350);
            awaitSearchQuery(clear);
            runTestOnUiThread(()->assertEquals("重建前草稿应仍存在","尚未搜索的草稿",findInput(page.getWindow().getDecorView()).getText().toString()));
            runTestOnUiThread(page::recreate);
            long deadline=SystemClock.uptimeMillis()+5000;
            while((current.get()==null||current.get()==page)&&SystemClock.uptimeMillis()<deadline)SystemClock.sleep(100);
            Activity recreated=current.get();assertNotNull(recreated);assertNotSame(page,recreated);
            awaitFocused(recreated);
            String shownInput=findInput(recreated.getWindow().getDecorView()).getText().toString();
            reportSearchState("saved",saved.get());reportSearchState("restored",restored.get());
            Bundle diagnostic=new Bundle();diagnostic.putString("stream","\nSearch after recreation: input="+shownInput+", title="+((TextView)recreated.findViewById(R.id.title)).getText()+", query="+catalogPath+"\n");
            getInstrumentation().sendStatus(0,diagnostic);
            assertNotNull("重建必须真正触发 onSaveInstanceState 才能验证保存状态的恢复",saved.get());
            assertNotNull("新页面 onCreate 必须收到先前保存的 Bundle",restored.get());
            assertEquals("尚未搜索的草稿",saved.get().getString("input"));
            assertEquals("尚未搜索的草稿",restored.get().getString("input"));
            assertEquals("尚未搜索的草稿",findInput(recreated.getWindow().getDecorView()).getText().toString());
            assertEquals(clear?"全部绘本":"测试分类",((TextView)recreated.findViewById(R.id.title)).getText().toString());
            assertSearchQuery(clear,Uri.parse(catalogPath));
        }finally{
            Activity finalPage=current.get();if(finalPage!=null)runTestOnUiThread(finalPage::finish);
            if(first!=null&&first!=finalPage){final Activity originalPage=first;runTestOnUiThread(originalPage::finish);}
            app.unregisterActivityLifecycleCallbacks(callbacks);
        }
    }
    private void awaitSearchQuery(boolean clear)throws Exception{
        long deadline=SystemClock.uptimeMillis()+5000;
        while(SystemClock.uptimeMillis()<deadline){
            Uri value=Uri.parse(catalogPath);
            if("price_desc".equals(value.getQueryParameter("sort"))&&(clear?"":"age3").equals(value.getQueryParameter("category"))){assertSearchQuery(clear,value);return;}
            SystemClock.sleep(100);
        }
        assertSearchQuery(clear,Uri.parse(catalogPath));
    }
    private void assertSearchQuery(boolean clear,Uri query){
        assertEquals("晚安",query.getQueryParameter("q"));assertEquals("price_desc",query.getQueryParameter("sort"));
        assertEquals(clear?"":"age3",query.getQueryParameter("category"));
        assertEquals(clear?"":"3-6",query.getQueryParameter("age"));assertEquals(clear?"":"bedtime",query.getQueryParameter("theme"));
    }
    private void reportSearchState(String phase,Bundle state){
        StringBuilder message=new StringBuilder("\nSearch ").append(phase).append(": ");
        if(state==null)message.append("null Bundle");
        else for(String key:new String[]{"input","query","category","age","theme","browse_title","sort"})message.append(key).append('=').append(state.getString(key)).append("; ");
        Bundle status=new Bundle();status.putString("stream",message.append('\n').toString());getInstrumentation().sendStatus(0,status);
    }
    private void awaitFocused(Activity page){
        boolean[] focused={false};long deadline=SystemClock.uptimeMillis()+10000;
        do{getInstrumentation().runOnMainSync(()->focused[0]=page.hasWindowFocus());if(!focused[0])SystemClock.sleep(100);}
        while(!focused[0]&&SystemClock.uptimeMillis()<deadline);
        assertTrue("页面必须稳定处于前台再操作",focused[0]);getInstrumentation().waitForIdleSync();SystemClock.sleep(350);
    }
    private com.google.android.material.textfield.TextInputEditText findInput(View view){
        if(view instanceof com.google.android.material.textfield.TextInputEditText)return (com.google.android.material.textfield.TextInputEditText)view;
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){var found=findInput(group.getChildAt(i));if(found!=null)return found;}
        return null;
    }
    private TextView findText(View view,String text){
        if(view instanceof TextView label&&label.getText().toString().contains(text))return label;
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){TextView found=findText(group.getChildAt(i),text);if(found!=null)return found;}
        return null;
    }
    public void testBookCoverPixelsMatchPreviousApk()throws Throwable{
        boolean baseline=((android.test.InstrumentationTestRunner)getInstrumentation()).getArguments().getString("cover_baseline","false").equals("true");
        File file=new File(activity.getExternalFilesDir(null),"optimization-cover-baseline.argb");
        JSONArray hashes=null;
        if(!baseline){
            JSONObject golden;
            try(InputStream input=getInstrumentation().getContext().getAssets().open("book-cover-1.0.35.sha256.json");
                ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
                byte[] buffer=new byte[4096];int count;
                while((count=input.read(buffer))!=-1)bytes.write(buffer,0,count);
                golden=new JSONObject(new String(bytes.toByteArray(),StandardCharsets.UTF_8));
            }
            assertEquals("1.0.35",golden.getString("sourceVersion"));
            assertEquals(220,golden.getInt("width"));assertEquals(300,golden.getInt("height"));
            hashes=golden.getJSONArray("sha256");
        }
        String[] titles={null,"","晚安，小熊","一二三四五六","一二三四五六七","一二三四五六七八九十十一","长标题绘本在第十二个字符后截断","小熊🐻的森林📚阅读日记"};
        int[][] bounds={{0,0,200,260},{13,17,213,277},{3,7,153,202},{0,0,0,0}};
        int[] alphas={255,128,0,255,90};
        ColorFilter[] filters={null,new LightingColorFilter(0xffbbeeaa,0x000011),new LightingColorFilter(0xffbbeeaa,0x000011),null,new PorterDuffColorFilter(0xffaa3333,PorterDuff.Mode.MULTIPLY)};
        if(!baseline)assertEquals(titles.length*bounds.length*alphas.length,hashes.length());
        MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] frame=new byte[220*300*4];int frameIndex=0;
        // Each hash describes big-endian ARGB ints recorded from the previous APK.
        try(DataOutputStream output=baseline?new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file))):null){
            for(int titleIndex=0;titleIndex<titles.length;titleIndex++)for(int boundsIndex=0;boundsIndex<bounds.length;boundsIndex++){
                BookCoverDrawable cover=new BookCoverDrawable(titles[titleIndex]);int[] rect=bounds[boundsIndex];cover.setBounds(rect[0],rect[1],rect[2],rect[3]);
                for(int pass=0;pass<alphas.length;pass++){
                    Bitmap bitmap=Bitmap.createBitmap(220,300,Bitmap.Config.ARGB_8888);bitmap.eraseColor(0x228899aa);
                    cover.setAlpha(alphas[pass]);cover.setColorFilter(filters[pass]);final Bitmap target=bitmap;
                    runTestOnUiThread(()->cover.draw(new Canvas(target)));
                    int[] pixels=new int[220*300];bitmap.getPixels(pixels,0,220,0,0,220,300);bitmap.recycle();
                    for(int index=0;index<pixels.length;index++){
                        if(baseline)output.writeInt(pixels[index]);
                        else{
                            int pixel=pixels[index],offset=index*4;
                            frame[offset]=(byte)(pixel>>>24);frame[offset+1]=(byte)(pixel>>>16);
                            frame[offset+2]=(byte)(pixel>>>8);frame[offset+3]=(byte)pixel;
                        }
                    }
                    if(!baseline)assertEquals("Cover pixels changed at title="+titleIndex+", bounds="+boundsIndex+", pass="+pass,
                        hashes.getString(frameIndex),hex(digest.digest(frame)));
                    frameIndex++;
                }
            }
        }
    }
    private String hex(byte[] bytes){
        char[] digits="0123456789abcdef".toCharArray(),value=new char[bytes.length*2];
        for(int i=0;i<bytes.length;i++){int octet=bytes[i]&255;value[i*2]=digits[octet>>>4];value[i*2+1]=digits[octet&15];}
        return new String(value);
    }
}
