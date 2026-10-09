package com.huimeng.bookshop.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import com.huimeng.bookshop.R;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** Reads the existing account and posts without changing any business data or preferences. */
@SuppressWarnings("deprecation")
public class LiveProfilePreviewTest extends InstrumentationTestCase {
    public void testExistingCatalogThemePageRenders()throws Throwable{
        Activity page=getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(),SearchActivity.class).putExtra("theme","bedtime").putExtra("browse_title","睡前故事").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK));
        try{
            assertEquals("睡前故事",((TextView)page.findViewById(R.id.title)).getText().toString());
            SystemClock.sleep(2000);getInstrumentation().waitForIdleSync();
            try(InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(getInstrumentation().getUiAutomation().executeShellCommand("screencap -p"))){
                Bitmap bitmap=BitmapFactory.decodeStream(in);assertNotNull(bitmap);
                try(FileOutputStream out=new FileOutputStream(new File(page.getExternalFilesDir(null),"interaction-live-theme.png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
            }
        }finally{getInstrumentation().runOnMainSync(page::finish);}
    }
    public void testExistingAccountProfileRendersItsLikedAndUnlikedPosts()throws Throwable{
        Activity page=getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(),UserProfileActivity.class).putExtra("user_id","U10001").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK));
        try{
            RecyclerView posts=page.findViewById(R.id.posts);boolean[] ready={false};long deadline=SystemClock.uptimeMillis()+15000;
            while(!ready[0]&&SystemClock.uptimeMillis()<deadline){getInstrumentation().runOnMainSync(()->ready[0]=posts.getAdapter().getItemCount()>=4);SystemClock.sleep(100);}
            assertTrue("实际服务应返回已有作者的帖子",ready[0]);SystemClock.sleep(1000);
            boolean[] states={false,false};getInstrumentation().runOnMainSync(()->{
                for(int index=0;index<posts.getAdapter().getItemCount();index++){
                    RecyclerView.ViewHolder holder=posts.findViewHolderForAdapterPosition(index);if(holder==null)continue;
                    TextView meta=holder.itemView.findViewById(R.id.meta);String description=String.valueOf(meta.getContentDescription());
                    if(description.startsWith("已点赞"))states[0]=true;if(description.startsWith("未点赞"))states[1]=true;
                }
            });
            assertTrue("当前账号已赞的帖子应显示已赞状态",states[0]);assertTrue("其他帖子应显示未赞状态",states[1]);
            try(InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(getInstrumentation().getUiAutomation().executeShellCommand("screencap -p"))){
                Bitmap bitmap=BitmapFactory.decodeStream(in);assertNotNull(bitmap);
                try(FileOutputStream out=new FileOutputStream(new File(page.getExternalFilesDir(null),"interaction-live-author.png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
            }
        }finally{getInstrumentation().runOnMainSync(page::finish);}
    }
}
