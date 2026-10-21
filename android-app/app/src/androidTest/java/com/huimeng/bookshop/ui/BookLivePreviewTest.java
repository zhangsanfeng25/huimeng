package com.huimeng.bookshop.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import com.huimeng.bookshop.R;

/** Uses normal startup preparation, then previews P0001 without business edits. */
@SuppressWarnings("deprecation")
public class BookLivePreviewTest extends InstrumentationTestCase {
    public void testExistingBookAndAppearance()throws Throwable{
        String label=((android.test.InstrumentationTestRunner)getInstrumentation()).getArguments().getString("previewLabel","book-live");
        java.util.concurrent.CountDownLatch ready=new java.util.concurrent.CountDownLatch(1);java.util.concurrent.atomic.AtomicReference<String> failure=new java.util.concurrent.atomic.AtomicReference<>();
        com.huimeng.bookshop.core.local.LocalRepository.prepare(getInstrumentation().getTargetContext(),ready::countDown,message->{failure.set(message);ready.countDown();});
        assertTrue("正常启动准备应完成",ready.await(15,java.util.concurrent.TimeUnit.SECONDS));assertNull(failure.get());
        Activity page=getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(),ProductDetailActivity.class).putExtra("product_id","P0001").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try{
            android.widget.TextView metadata=page.findViewById(R.id.bookMetadata);long deadline=SystemClock.uptimeMillis()+15000;while(!metadata.getText().toString().contains("林禾")&&SystemClock.uptimeMillis()<deadline)SystemClock.sleep(100);
            assertTrue("已有商品应读取补齐后的图书资料",metadata.getText().toString().contains("林禾"));capture(page,label+"-product");
            int[] lines=new int[2];boolean[] fits=new boolean[2];getInstrumentation().runOnMainSync(()->{int index=0;for(int id:new int[]{R.id.addCart,R.id.buyNow}){android.widget.TextView button=page.findViewById(id);lines[index]=button.getLayout().getLineCount();fits[index]=button.getPaint().measureText(button.getText().toString())<=button.getWidth()-button.getCompoundPaddingLeft()-button.getCompoundPaddingRight();index++;}});for(int i=0;i<2;i++){assertEquals("购买操作文字应保持一行",1,lines[i]);assertTrue("购买操作文字不应被截断",fits[i]);}
            getInstrumentation().runOnMainSync(()->page.findViewById(R.id.contentsCard).performClick());capture(page,label+"-contents");
            var manager=((androidx.fragment.app.FragmentActivity)page).getSupportFragmentManager();var sheet=(com.google.android.material.bottomsheet.BottomSheetDialogFragment)manager.findFragmentByTag("book-details-sheet");getInstrumentation().runOnMainSync(sheet::dismiss);getInstrumentation().waitForIdleSync();
            getInstrumentation().runOnMainSync(()->page.findViewById(R.id.authorCard).performClick());capture(page,label+"-author");
        }finally{getInstrumentation().runOnMainSync(page::finish);}
        Activity settings=getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(),SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));try{capture(settings,label+"-settings");}finally{getInstrumentation().runOnMainSync(settings::finish);}
    }
    private void capture(Activity page,String name)throws Exception{
        getInstrumentation().waitForIdleSync();SystemClock.sleep(500);
        try(var descriptor=getInstrumentation().getUiAutomation().executeShellCommand("screencap -p");var input=new android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor);var output=new java.io.FileOutputStream(new java.io.File(page.getExternalFilesDir(null),name+".png"))){byte[] bytes=new byte[8192];int count;while((count=input.read(bytes))!=-1)output.write(bytes,0,count);}
    }
}
