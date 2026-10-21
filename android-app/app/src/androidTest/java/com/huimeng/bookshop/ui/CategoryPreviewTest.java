package com.huimeng.bookshop.ui;

import android.content.Context;
import android.content.Intent;
import android.test.InstrumentationTestCase;
import com.huimeng.bookshop.core.local.LocalRepository;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Read-only visual preview using the currently selected local account. */
@SuppressWarnings("deprecation")
public class CategoryPreviewTest extends InstrumentationTestCase {
    public void testPreviewAgeCategory()throws Exception{
        Context context=getInstrumentation().getTargetContext();
        CountDownLatch ready=new CountDownLatch(1);
        LocalRepository.prepare(context,ready::countDown,error->{throw new AssertionError(error);});
        assertTrue(ready.await(8,TimeUnit.SECONDS));
        getInstrumentation().startActivitySync(new Intent(context,SearchActivity.class)
            .putExtra("age","6-9").putExtra("browse_title","6–9岁绘本")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        getInstrumentation().waitForIdleSync();Thread.sleep(700);
    }
}
