package com.huimeng.bookshop.ui;
import android.app.Activity;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;
import com.huimeng.bookshop.core.WallpaperStore;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import org.json.*;
public class BookDetailsTest extends RefreshNavigationTest {
    @Override protected String fixture(com.android.volley.Request<?> request){
        if(request.getUrl().contains("catalog/products/book-"))try{
            JSONObject value=new JSONObject(super.fixture(request)).getJSONObject("data");
            value.put("summary","小熊和朋友们在森林里寻找温暖的约定。".repeat(18)).put("authorName","林禾").put("illustratorName","陈悠").put("publisher","绘梦童书出版社").put("publicationDate","2025-09-01").put("authorBio","林禾关注儿童日常与亲子共读。".repeat(30)).put("illustratorBio","陈悠喜欢以柔和色彩描绘森林。").put("metadataSource","fictional_demo").put("editorialReview","和孩子一起观察画面中的细节。").put("audioUrl","/public/test-audio.mp3").put("tableOfContents",new JSONArray().put("森林的傍晚").put("很长的目录标题".repeat(12)).put("月光下的梦")).put("recommendations",new JSONArray().put(new JSONObject().put("id","book-1").put("name","森林里的邮递员").put("stock",10).put("priceCents",3280)));
            return new JSONObject().put("data",value).toString();
        }catch(Exception error){throw new RuntimeException(error);}
        return super.fixture(request);
    }
    private View view(Activity page,String key){int id=page.getResources().getIdentifier(key,"id",page.getPackageName());assertTrue("应有控件 "+key,id!=0);View view=page.findViewById(id);assertNotNull(view);return view;}
    private Activity open(Class<?> type){return getInstrumentation().startActivitySync(new Intent(activity,type).putExtra("product_id","book-0").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
    private void screenshot(Activity page,String name)throws Exception{getInstrumentation().waitForIdleSync();SystemClock.sleep(500);try(var descriptor=getInstrumentation().getUiAutomation().executeShellCommand("screencap -p");var input=new android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor);var output=new java.io.FileOutputStream(new java.io.File(page.getExternalFilesDir(null),name+".png"))){byte[] bytes=new byte[8192];int count;while((count=input.read(bytes))!=-1)output.write(bytes,0,count);}}
    public void testPureBackgroundIsVisibleAndCanBeCancelled()throws Throwable{
        Activity page=open(SettingsActivity.class);getInstrumentation().waitForIdleSync();SystemClock.sleep(350);
        try{runTestOnUiThread(()->{
            View plain=view(page,"wallpaperDefault");assertTrue("纯色入口不能被头像按钮挤出",plain.getWidth()>48);
            plain.performClick();assertTrue(plain.isSelected());assertEquals("SOLID",new WallpaperStore(page).wallpaper());
            plain.performClick();assertFalse(plain.isSelected());assertEquals(WallpaperStore.DEFAULT,new WallpaperStore(page).wallpaper());
            for(String key:new String[]{"wallpaperMint","wallpaperTwilight","wallpaperSunrise","wallpaperOcean","wallpaperGarden"}){View card=view(page,key);assertTrue(card.getWidth()>48);card.performClick();assertTrue(card.isSelected());card.performClick();assertFalse(card.isSelected());assertEquals(WallpaperStore.DEFAULT,new WallpaperStore(page).wallpaper());}
        });}finally{runTestOnUiThread(page::finish);}
    }
    public void testBookMetadataNoAudioAndRoundSheets()throws Throwable{
        Activity page=open(ProductDetailActivity.class);
        try{
            long deadline=SystemClock.uptimeMillis()+5000;
            while(SystemClock.uptimeMillis()<deadline){int id=page.getResources().getIdentifier("bookMetadata","id",page.getPackageName());if(id!=0&&((TextView)page.findViewById(id)).getText().toString().contains("林禾"))break;SystemClock.sleep(100);}
            runTestOnUiThread(()->{
                assertTrue(((TextView)view(page,"bookMetadata")).getText().toString().contains("2025年9月1日"));
                int audioId=page.getResources().getIdentifier("audio","id",page.getPackageName());assertTrue(audioId==0||page.findViewById(audioId)==null||page.findViewById(audioId).getVisibility()==View.GONE);
                view(page,"contentsCard").performClick();
            });getInstrumentation().waitForIdleSync();SystemClock.sleep(350);
            var manager=((androidx.fragment.app.FragmentActivity)page).getSupportFragmentManager();
            BottomSheetDialogFragment sheet=(BottomSheetDialogFragment)manager.findFragmentByTag("book-details-sheet");assertNotNull(sheet);assertTrue(sheet.getDialog().isShowing());
            runTestOnUiThread(sheet::dismiss);getInstrumentation().waitForIdleSync();
            runTestOnUiThread(()->view(page,"authorCard").performClick());getInstrumentation().waitForIdleSync();
            assertNotNull(manager.findFragmentByTag("book-details-sheet"));
        }finally{runTestOnUiThread(page::finish);}
    }
    public void testLongAuthorSheetRestoresAfterRecreation()throws Throwable{
        Activity page=open(ProductDetailActivity.class);java.util.concurrent.atomic.AtomicReference<Activity> resumed=new java.util.concurrent.atomic.AtomicReference<>();
        android.app.Application.ActivityLifecycleCallbacks callbacks=new android.app.Application.ActivityLifecycleCallbacks(){public void onActivityResumed(Activity item){if(item instanceof ProductDetailActivity)resumed.set(item);}public void onActivityCreated(Activity a,android.os.Bundle b){}public void onActivityStarted(Activity a){}public void onActivityPaused(Activity a){}public void onActivityStopped(Activity a){}public void onActivitySaveInstanceState(Activity a,android.os.Bundle b){}public void onActivityDestroyed(Activity a){}};
        page.getApplication().registerActivityLifecycleCallbacks(callbacks);
        try{
            long deadline=SystemClock.uptimeMillis()+5000;while(SystemClock.uptimeMillis()<deadline){int id=page.getResources().getIdentifier("authorPreview","id",page.getPackageName());if(id!=0&&((TextView)page.findViewById(id)).getText().toString().contains("林禾"))break;SystemClock.sleep(100);}
            screenshot(page,"book-details-page");runTestOnUiThread(()->view(page,"authorCard").performClick());getInstrumentation().waitForIdleSync();SystemClock.sleep(500);
            var manager=((androidx.fragment.app.FragmentActivity)page).getSupportFragmentManager();var sheet=(BottomSheetDialogFragment)manager.findFragmentByTag("book-details-sheet");assertNotNull(sheet);
            View surface=sheet.getDialog().findViewById(com.google.android.material.R.id.design_bottom_sheet);var shape=(com.google.android.material.shape.MaterialShapeDrawable)surface.getBackground();float corner=shape.getShapeAppearanceModel().getTopLeftCornerSize().getCornerSize(new android.graphics.RectF(0,0,surface.getWidth(),surface.getHeight()));assertEquals(28*page.getResources().getDisplayMetrics().density,corner,1f);assertEquals(0f,shape.getShapeAppearanceModel().getBottomLeftCornerSize().getCornerSize(new android.graphics.RectF()),.1f);
            screenshot(page,"book-details-author");runTestOnUiThread(page::recreate);deadline=SystemClock.uptimeMillis()+8000;while(resumed.get()==null&&SystemClock.uptimeMillis()<deadline)SystemClock.sleep(100);assertNotNull(resumed.get());getInstrumentation().waitForIdleSync();
            var restored=((androidx.fragment.app.FragmentActivity)resumed.get()).getSupportFragmentManager().findFragmentByTag("book-details-sheet");assertNotNull("打开的作者弹层应随重建恢复",restored);assertTrue(((BottomSheetDialogFragment)restored).getDialog().isShowing());
        }finally{page.getApplication().unregisterActivityLifecycleCallbacks(callbacks);Activity target=resumed.get()==null?page:resumed.get();runTestOnUiThread(target::finish);}
    }
    public void testSettingsAndLongDirectoryScreenshots()throws Throwable{
        Activity settings=open(SettingsActivity.class);try{screenshot(settings,"book-details-settings");}finally{runTestOnUiThread(settings::finish);}
        Activity page=open(ProductDetailActivity.class);try{getInstrumentation().waitForIdleSync();SystemClock.sleep(700);runTestOnUiThread(()->view(page,"contentsCard").performClick());screenshot(page,"book-details-contents");}finally{runTestOnUiThread(page::finish);}
    }
    public void testCompactHeaderUsesVerticalLayout()throws Throwable{
        Activity page=open(ProductDetailActivity.class);android.content.res.Resources resources=page.getResources();android.content.res.Configuration before=new android.content.res.Configuration(resources.getConfiguration());
        try{runTestOnUiThread(()->{
            android.content.res.Configuration compact=new android.content.res.Configuration(before);compact.screenWidthDp=320;compact.fontScale=1.3f;resources.updateConfiguration(compact,resources.getDisplayMetrics());
            android.view.ViewGroup content=page.findViewById(android.R.id.content);var binding=com.huimeng.bookshop.databinding.ActivityProductDetailBinding.bind(content.getChildAt(0));new BookDetailsView((androidx.appcompat.app.AppCompatActivity)page,binding,null);
            android.widget.LinearLayout hero=(android.widget.LinearLayout)binding.cover.getParent().getParent();assertEquals("小屏及大字体应将封面与交易信息顺序排布",android.widget.LinearLayout.VERTICAL,hero.getOrientation());assertEquals(-1,((View)binding.cover.getParent()).getLayoutParams().width);
        });}finally{runTestOnUiThread(()->{resources.updateConfiguration(before,resources.getDisplayMetrics());page.finish();});}
    }
    public void testMerchantBookFormRetainsDraftAndChapterLines()throws Throwable{
        runTestOnUiThread(()->{try{
            android.widget.LinearLayout container=new android.widget.LinearLayout(activity);android.widget.Button anchor=new android.widget.Button(activity);container.addView(anchor);BookMetadataEditor editor=new BookMetadataEditor(activity,anchor,null);
            editor.setData(new JSONObject().put("authorName","测试作者").put("publisher","测试童书社").put("publicationDate","2025-02-28").put("tableOfContents",new JSONArray().put("第一节").put("第二节")));
            android.widget.LinearLayout group=(android.widget.LinearLayout)container.getChildAt(0);group.getChildAt(0).performClick();
            org.json.JSONObject values=editor.applyTo(new JSONObject());assertEquals("测试作者",values.getString("authorName"));assertEquals(2,values.getJSONArray("tableOfContents").length());
            android.widget.LinearLayout fields=(android.widget.LinearLayout)group.getChildAt(1);var author=(com.google.android.material.textfield.TextInputLayout)fields.getChildAt(0);author.getEditText().setText("尚未保存的新作者");
            android.os.Bundle state=new android.os.Bundle();editor.save(state);android.widget.LinearLayout restoredContainer=new android.widget.LinearLayout(activity);android.widget.Button restoredAnchor=new android.widget.Button(activity);restoredContainer.addView(restoredAnchor);BookMetadataEditor restored=new BookMetadataEditor(activity,restoredAnchor,state);restored.setData(new JSONObject().put("authorName","服务器旧值"));
            assertEquals("尚未保存的新作者",restored.applyTo(new JSONObject()).getString("authorName"));assertEquals(2,restored.applyTo(new JSONObject()).getJSONArray("tableOfContents").length());assertEquals(View.VISIBLE,((android.widget.LinearLayout)restoredContainer.getChildAt(0)).getChildAt(1).getVisibility());
        }catch(Exception error){throw new RuntimeException(error);}});
    }
    public void testThemePreview()throws Throwable{
        Activity page=open(ProductDetailActivity.class);try{getInstrumentation().waitForIdleSync();SystemClock.sleep(700);screenshot(page,"book-details-theme");runTestOnUiThread(()->view(page,"authorCard").performClick());screenshot(page,"book-details-theme-author");}finally{runTestOnUiThread(page::finish);}
    }
}
