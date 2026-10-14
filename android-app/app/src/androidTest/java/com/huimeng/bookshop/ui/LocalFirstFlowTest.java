package com.huimeng.bookshop.ui;
import android.test.InstrumentationTestCase;
import android.content.*;
import android.app.Activity;
import com.huimeng.bookshop.BuildConfig;
import com.huimeng.bookshop.core.*;
import com.huimeng.bookshop.core.local.*;
import com.huimeng.bookshop.core.sync.*;
import org.json.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.lang.reflect.Field;
@SuppressWarnings("deprecation")
public class LocalFirstFlowTest extends InstrumentationTestCase {
 private Context context;private LocalDatabase db,previous;private SharedPreferences prefs;private Map<String,?> saved;private String token;private Activity activity;
 @Override protected void setUp()throws Exception{super.setUp();context=getInstrumentation().getTargetContext();prefs=context.getSharedPreferences("huimeng_session",0);saved=new HashMap<>(prefs.getAll());Field temp=SessionStore.class.getDeclaredField("temporaryToken");temp.setAccessible(true);token=(String)temp.get(null);getInstrumentation().runOnMainSync(()->SyncCoordinator.get(context).stop());Field instance=LocalDatabase.class.getDeclaredField("instance");instance.setAccessible(true);previous=(LocalDatabase)instance.get(null);String fixture=((android.test.InstrumentationTestRunner)getInstrumentation()).getArguments().getString("testFixture","default");if(!fixture.matches("[A-Za-z0-9_-]{1,40}"))throw new IllegalArgumentException("Invalid test fixture");db=new LocalDatabase(context,"offline-e2e-"+fixture+".db");instance.set(null,db);prefs.edit().clear().putBoolean("remember_choice_initialized",true).commit();temp.set(null,"");}
 @Override protected void tearDown()throws Exception{if(activity!=null)getInstrumentation().runOnMainSync(()->activity.finish());getInstrumentation().runOnMainSync(()->SyncCoordinator.get(context).stop());getInstrumentation().waitForIdleSync();Field instance=LocalDatabase.class.getDeclaredField("instance");instance.setAccessible(true);instance.set(null,previous);db.close();SharedPreferences.Editor edit=prefs.edit().clear();for(Map.Entry<String,?> e:saved.entrySet()){Object v=e.getValue();if(v instanceof String)edit.putString(e.getKey(),(String)v);else if(v instanceof Boolean)edit.putBoolean(e.getKey(),(Boolean)v);else if(v instanceof Integer)edit.putInt(e.getKey(),(Integer)v);else if(v instanceof Long)edit.putLong(e.getKey(),(Long)v);}edit.commit();Field temp=SessionStore.class.getDeclaredField("temporaryToken");temp.setAccessible(true);temp.set(null,token);super.tearDown();}
 private JSONObject call(String path,JSONObject body,String auth)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(BuildConfig.API_BASE_URL+path).openConnection();c.setConnectTimeout(2000);c.setReadTimeout(5000);if(auth!=null)c.setRequestProperty("Authorization","Bearer "+auth);if(body!=null){c.setRequestMethod("POST");c.setRequestProperty("Content-Type","application/json");c.setDoOutput(true);try(java.io.OutputStream out=c.getOutputStream()){out.write(body.toString().getBytes(StandardCharsets.UTF_8));}}try{int status=c.getResponseCode();java.io.InputStream stream=status<400?c.getInputStream():c.getErrorStream();String text=new String(stream.readAllBytes(),StandardCharsets.UTF_8);assertEquals(text,200,status);return new JSONObject(text);}finally{c.disconnect();}}
 private void launch(String space)throws Exception{new SessionStore(context).localSpace(space);activity=getInstrumentation().startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));getInstrumentation().waitForIdleSync();assertFalse(activity.isFinishing());}
 private void screenshot(String name)throws Exception{getInstrumentation().waitForIdleSync();Thread.sleep(350);android.graphics.Bitmap image=getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(image);try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(context.getExternalFilesDir(null),name))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{image.recycle();}}
 public void testOfflineCreatesPersistentContent()throws Exception{
  try{call("health",null,null);fail("Offline test requires the isolated service to be stopped");}catch(java.io.IOException expected){}
  String space=new LocalSpaceStore(db).ensureActiveSpace();SampleCatalog.seed(db,space);launch(space);
  String marker="本地同步验收-"+java.util.UUID.randomUUID();db.metadata("e2e-marker",marker);
  new BuyerRepository(db,space).handle(1,"me/addresses",new JSONObject().put("recipient","离线验收").put("phone","13800138000").put("region","测试地区").put("detail",marker));
  android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(20,20,android.graphics.Bitmap.Config.ARGB_8888);image.eraseColor(0xff6c9c80);java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();image.compress(android.graphics.Bitmap.CompressFormat.JPEG,90,bytes);image.recycle();LocalMediaStore media=new LocalMediaStore(context,db);String mediaId=media.saveJpeg(space,bytes.toByteArray(),"COMMUNITY");
  new CommunityRepository(db,space).handle(1,"community/posts",new JSONObject().put("title",marker).put("content","服务关闭时创建，重连后上传").put("images",new JSONArray().put(media.resolveUri(space,mediaId).toString())));
  assertEquals(2,db.pending(space).length());db.close();assertEquals(2,db.pending(space).length());assertEquals(1,db.list(space,"posts").length());screenshot("offline-home.png");
 }
 public void testOnlineUploadsAndPulls()throws Exception{
  String space=new LocalSpaceStore(db).ensureActiveSpace();assertEquals(2,db.pending(space).length());
  JSONObject auth=call("auth/login",new JSONObject().put("username","xingxing").put("password","demo12345").put("role","USER"),null),user=auth.getJSONObject("user");
  new LocalSpaceStore(db).bind(space,BuildConfig.API_BASE_URL,user.getString("id"));SessionStore session=new SessionStore(context);session.save(auth.getString("token"),"USER",user.optString("name"),false,user.getString("id"),user.getString("username"),false);launch(space);
  long deadline=System.currentTimeMillis()+60000;while(System.currentTimeMillis()<deadline){if(db.pending(space).length()==0&&!db.metadata("last-sync:"+space).isEmpty())break;Thread.sleep(200);}
  assertEquals("Pending operations: "+db.pending(space),0,db.pending(space).length());assertFalse(db.metadata("last-sync:"+space).isEmpty());
  JSONArray posts=call("community/posts",null,auth.getString("token")).getJSONArray("data");boolean found=false;for(int i=0;i<posts.length();i++){JSONObject post=posts.getJSONObject(i);if(db.metadata("e2e-marker").equals(post.optString("title"))){found=true;assertTrue(post.optString("coverUrl").startsWith("/public/community-"));}}assertTrue("Created offline post must exist on shared server",found);JSONArray addresses=call("me/addresses",null,auth.getString("token")).getJSONArray("data");boolean addressFound=false;for(int i=0;i<addresses.length();i++)if(db.metadata("e2e-marker").equals(addresses.getJSONObject(i).optString("detail")))addressFound=true;assertTrue("Created offline address must exist on shared server",addressFound);assertTrue(db.list(space,"products").length()>1);String expected="共 "+db.list(space,"products").length()+" 本";java.util.concurrent.atomic.AtomicReference<String> displayed=new java.util.concurrent.atomic.AtomicReference<>("");long renderDeadline=System.currentTimeMillis()+8000;do{getInstrumentation().runOnMainSync(()->displayed.set(((android.widget.TextView)activity.findViewById(com.huimeng.bookshop.R.id.filterSummary)).getText().toString()));if(expected.equals(displayed.get()))break;Thread.sleep(100);}while(System.currentTimeMillis()<renderDeadline);assertEquals("Home must render the imported catalog",expected,displayed.get());screenshot("online-home.png");
 }
 public void testWelcomeEntersWithoutNetworkLogin()throws Exception{
  android.app.Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(MainActivity.class.getName(),null,false);
  Activity welcome=getInstrumentation().startActivitySync(new Intent(context,WelcomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  try{activity=getInstrumentation().waitForMonitorWithTimeout(monitor,8000);assertNotNull("Welcome must prepare SQLite and enter without a network token",activity);assertTrue(new SessionStore(context).hasLocalSpace());assertTrue(new SessionStore(context).token().isEmpty());screenshot("welcome-local-home.png");}finally{getInstrumentation().removeMonitor(monitor);getInstrumentation().runOnMainSync(()->welcome.finish());}
 }
 public void testPendingOrderAndSyncScreens()throws Exception{
  String space=new LocalSpaceStore(db).ensureActiveSpace();SampleCatalog.seed(db,space);new SessionStore(context).localSpace(space);
  String address=new BuyerRepository(db,space).handle(1,"me/addresses",new JSONObject().put("recipient","本地预览").put("phone","13800138000").put("region","测试地区").put("detail","本地地址")).getString("id");
  JSONObject checkout=new OrderRepository(db,space).handle(1,"checkout",new JSONObject().put("addressId",address).put("items",new JSONArray().put(new JSONObject().put("productId","sample-book").put("quantity",1))));
  String id=checkout.getJSONArray("orderIds").getString(0);activity=getInstrumentation().startActivitySync(new Intent(context,OrderDetailActivity.class).putExtra("order_id",id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));getInstrumentation().waitForIdleSync();Thread.sleep(600);
  String label=((android.widget.TextView)activity.findViewById(com.huimeng.bookshop.R.id.status)).getText().toString();assertTrue(label,label.contains("待联网确认"));screenshot("pending-order.png");getInstrumentation().runOnMainSync(()->activity.finish());
  activity=getInstrumentation().startActivitySync(new Intent(context,SyncStatusActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));getInstrumentation().waitForIdleSync();Thread.sleep(500);screenshot("sync-status.png");assertFalse(activity.isFinishing());
 }
 public void testOriginalPublicDataAndServiceWithoutLogin()throws Exception{
  java.util.concurrent.CountDownLatch ready=new java.util.concurrent.CountDownLatch(1);java.util.concurrent.atomic.AtomicReference<String> error=new java.util.concurrent.atomic.AtomicReference<>("");LocalRepository.prepare(context,ready::countDown,message->{error.set(message);ready.countDown();});assertTrue(ready.await(10,java.util.concurrent.TimeUnit.SECONDS));assertEquals("",error.get());String space=new SessionStore(context).localSpaceId();launch(space);
  assertTrue("Original products must replace the one-book sample",db.list(space,"products").length()>1);assertTrue(db.list(space,"posts").length()>0);assertTrue(db.list(space,"notifications").length()>0);assertNull(db.entity(space,"products","sample-book"));assertTrue(new SessionStore(context).token().isEmpty());
  int oldBanner=context.getResources().getIdentifier("syncStatus","id",context.getPackageName());assertTrue(oldBanner==0||activity.findViewById(oldBanner)==null);
  long until=System.currentTimeMillis()+10000;while(!db.metadata("service-connected:"+space).equals("1")&&System.currentTimeMillis()<until)Thread.sleep(100);assertEquals("Service health must be detected without login","1",db.metadata("service-connected:"+space));screenshot("recovery-home.png");
  getInstrumentation().runOnMainSync(()->activity.finish());activity=getInstrumentation().startActivitySync(new Intent(context,SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));assertNotNull(activity.findViewById(com.huimeng.bookshop.R.id.localSync));screenshot("recovery-settings.png");
 }
}
