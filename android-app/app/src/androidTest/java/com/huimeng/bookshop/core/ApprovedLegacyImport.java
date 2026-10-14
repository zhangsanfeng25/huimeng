package com.huimeng.bookshop.core;

import android.test.InstrumentationTestCase;
import android.content.*;
import com.huimeng.bookshop.core.local.*;
import org.json.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Explicit local migration command. No file means no changes; never part of normal app startup. */
@SuppressWarnings("deprecation")
public class ApprovedLegacyImport extends InstrumentationTestCase {
 public void testImportCurrentAccount()throws Exception{
  Context context=getInstrumentation().getTargetContext();File input=new File(context.getFilesDir(),"approved-import/snapshot.json");
  assertTrue("Provide the explicitly approved local migration file first",input.isFile());
  JSONObject snapshot=new JSONObject(new String(Files.readAllBytes(input.toPath()),StandardCharsets.UTF_8));SessionStore session=new SessionStore(context);
  assertFalse("An original account identity is required",session.accountId().isEmpty());assertEquals(session.accountId(),snapshot.getString("userId"));assertEquals(com.huimeng.bookshop.BuildConfig.API_BASE_URL,snapshot.getString("origin"));
  getInstrumentation().runOnMainSync(()->com.huimeng.bookshop.core.sync.SyncCoordinator.get(context).stop());
  LocalDatabase db=LocalDatabase.get(context);LocalSpaceStore spaces=new LocalSpaceStore(db);String space=spaces.bind(session.localSpaceId(),snapshot.getString("origin"),session.accountId());
  new LocalDataImporter(db).apply(space,snapshot,session.accountId());spaces.select(space);LocalBootstrap.prepare(context,db,space);
  JSONObject profile=db.entity(space,"profile","self");if(profile!=null){session.updateProfile(profile.optString("username"),profile.optString("name"));session.setAvatarUrl(profile.optString("avatarUrl"));}session.localSpace(space);
  JSONObject counts=new JSONObject();java.util.Iterator<String> names=snapshot.getJSONObject("resources").keys();while(names.hasNext()){String name=names.next();counts.put(name,db.list(space,name).length());}
  Files.write(new File(context.getFilesDir(),"approved-import/receipt.json").toPath(),counts.toString().getBytes(StandardCharsets.UTF_8));assertTrue(input.delete());
 }
 public void testPreviewCurrentAccount()throws Exception{
  Context context=getInstrumentation().getTargetContext();SessionStore session=new SessionStore(context);assertEquals("U10001",session.accountId());LocalDatabase db=LocalDatabase.get(context);String space=session.localSpaceId();assertEquals(20,db.list(space,"products").length());assertEquals(8,db.list(space,"posts").length());assertEquals(3,db.list(space,"notifications").length());
  java.util.concurrent.CountDownLatch ready=new java.util.concurrent.CountDownLatch(1);LocalRepository.prepare(context,ready::countDown,error->{throw new AssertionError(error);});assertTrue(ready.await(8,java.util.concurrent.TimeUnit.SECONDS));
  com.huimeng.bookshop.ui.MainActivity activity=(com.huimeng.bookshop.ui.MainActivity)getInstrumentation().startActivitySync(new Intent(context,com.huimeng.bookshop.ui.MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  for(int tab=1;tab<=3;tab++){final int selected=tab;getInstrumentation().runOnMainSync(()->activity.selectTab(selected));getInstrumentation().waitForIdleSync();Thread.sleep(700);android.graphics.Bitmap bitmap=getInstrumentation().getUiAutomation().takeScreenshot();try(java.io.FileOutputStream output=new java.io.FileOutputStream(new File(context.getExternalFilesDir(null),"migrated-tab-"+tab+".png"))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output);}finally{bitmap.recycle();}}
  getInstrumentation().runOnMainSync(()->activity.selectTab(1));
 }
}
