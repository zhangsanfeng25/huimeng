package com.huimeng.bookshop.core.sync;
import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;
import com.huimeng.bookshop.BuildConfig;
import com.huimeng.bookshop.core.SessionStore;
import com.huimeng.bookshop.core.local.*;
import org.json.*;
import java.io.File;
import java.nio.file.Files;

/** One foreground runner; every callback checks its space and generation before writing. */
public final class SyncCoordinator {
 private static SyncCoordinator instance;
 public static synchronized SyncCoordinator get(Context context){if(instance==null)instance=new SyncCoordinator(context.getApplicationContext());return instance;}
 private final Context context;private final Handler main=new Handler(Looper.getMainLooper());private final SyncTransport transport;
 private String space="",role="USER";private volatile int generation;private boolean running,busy;private int failures;
 private Runnable logoutReady;
 private final String[] resources={"categories","products","profile","addresses","cart","favorites","posts","comments","likes","notifications","merchantApplication","merchantProducts","checkouts","buyerOrders","merchantOrders","productRatings","ownRatings"};
 private SyncCoordinator(Context context){this.context=context;transport=new SyncTransport(context);}
 public void start(String id){if(Looper.myLooper()!=Looper.getMainLooper()){main.post(()->start(id));return;}if(running&&space.equals(id)){requestSync();return;}stop();space=id;role=new SessionStore(context).role();running=true;int g=generation;LocalRepository.IO.execute(()->{if(valid(g)){LocalDatabase.get(context).getWritableDatabase().execSQL("UPDATE sync_operations SET state='PENDING' WHERE space_id=? AND state='IN_FLIGHT'",new Object[]{space});main.post(this::requestSync);}});}
 public void stop(){if(Looper.myLooper()!=Looper.getMainLooper()){main.post(this::stop);return;}running=false;busy=false;generation++;main.removeCallbacksAndMessages(null);}
 public void flushBeforeLogout(Runnable afterFlush){
  if(!running||new SessionStore(context).token().isEmpty()){afterFlush.run();return;}
  Handler deadline=new Handler(Looper.getMainLooper());
  Runnable finish=()->{logoutReady=null;afterFlush.run();};
  logoutReady=()->{deadline.removeCallbacks(finish);finish.run();};
  deadline.postDelayed(finish,2500);
  requestSync();
 }
 public void requestSync(){if(Looper.myLooper()!=Looper.getMainLooper()){main.post(this::requestSync);return;}if(!running||busy)return;busy=true;int g=generation;String captured=space;
  LocalRepository.IO.execute(()->{try{
   if(!valid(g))return;LocalDatabase db=LocalDatabase.get(context);JSONObject binding=new LocalSpaceStore(db).info(captured);String user=binding.optString("userId");
   main.post(()->{if(!valid(g))return;transport.json(0,"health",null,health->{if(!valid(g))return;
    LocalRepository.IO.execute(()->{if(!valid(g))return;db.metadata("service-connected:"+captured,"1");main.post(()->{
     if(!valid(g))return;
     if(user.isEmpty()){finish(g,"服务器已连接，登录后同步个人数据",15000);return;}
     SessionStore session=new SessionStore(context);
     if(!BuildConfig.API_BASE_URL.equals(binding.optString("origin"))){finish(g,"服务地址与本地账号绑定不一致",15000);return;}
     if(session.token().isEmpty()){finish(g,"服务已连接 · 登录原账号后同步个人数据",15000);return;}
     transport.json(0,"auth/me",null,result->{if(!valid(g))return;JSONObject profile=result.optJSONObject("user");if(profile==null||!user.equals(profile.optString("id"))||!role.equals(profile.optString("activeRole"))){finish(g,"网络账号与本地空间不匹配，已暂停上传",15000);return;}
      LocalRepository.IO.execute(()->{if(!valid(g))return;db.getWritableDatabase().execSQL("UPDATE sync_operations SET state='PENDING' WHERE space_id=? AND state='WAITING_AUTH'",new Object[]{captured});uploadMedia(g);});
     },error->networkFailure(g,error));
    });});
   },error->{LocalRepository.IO.execute(()->{if(valid(g))db.metadata("service-connected:"+captured,"0");});networkFailure(g,error);});});
  }catch(Exception e){finish(g,"同步准备失败："+e.getMessage(),15000);}});
 }
 private boolean valid(int g){return running&&generation==g&&space.equals(new SessionStore(context).localSpaceId());}
 public static long retryDelay(int attempts,long retryAfter){if(retryAfter>0)return retryAfter;long[] steps={2000,5000,15000,30000,60000,120000,240000,300000};return steps[Math.max(0,Math.min(steps.length-1,attempts-1))];}
 private void networkFailure(int g,SyncTransport.TransportError error){if(!valid(g))return;if(error.status==401)new SessionStore(context).invalidateNetworkCredentials(context);long delay=retryDelay(++failures,error.retryAfterMs);finish(g,error.message,delay==300000?delay:Math.min(300000,delay+(long)(Math.random()*500)));}
 private void finish(int g,String message,long delay){LocalRepository.IO.execute(()->{if(!valid(g))return;LocalDatabase db=LocalDatabase.get(context);db.metadata("status:"+space,message);int pending=db.pending(space).length();main.post(()->{if(!valid(g))return;busy=false;SyncStatusStore.publish(message,pending);if(logoutReady!=null)logoutReady.run();else main.postDelayed(this::requestSync,delay);});});}
 private void uploadMedia(int g){if(!valid(g))return;LocalDatabase db=LocalDatabase.get(context);String id,purpose,path,hash;try(Cursor c=db.getReadableDatabase().rawQuery("SELECT id,purpose,path,sha256 FROM local_media WHERE space_id=? AND state='PENDING' AND submission_role=? ORDER BY rowid LIMIT 1",new String[]{space,role})){if(!c.moveToFirst()){uploadOperations(g);return;}id=c.getString(0);purpose=c.getString(1);path=c.getString(2);hash=c.getString(3);}
  try{byte[] bytes=Files.readAllBytes(new File(path).toPath());main.post(()->{if(!valid(g))return;transport.media(bytes,id,purpose,hash,result->LocalRepository.IO.execute(()->{if(!valid(g))return;ContentValues v=new ContentValues();v.put("state","READY");v.put("url",result.optString("url"));db.getWritableDatabase().update("local_media",v,"space_id=? AND id=?",new String[]{space,id});uploadMedia(g);}),error->{if(!valid(g))return;if(error.status==0||error.status==401||error.status>=500||error.status==429){networkFailure(g,error);return;}LocalRepository.IO.execute(()->{if(!valid(g))return;ContentValues v=new ContentValues();v.put("state","BLOCKED");db.getWritableDatabase().update("local_media",v,"space_id=? AND id=?",new String[]{space,id});uploadMedia(g);});});});
  }catch(Exception e){finish(g,"本地图片无法读取，内容已保留："+e.getMessage(),15000);}
 }
 private void uploadOperations(int g){if(!valid(g))return;LocalDatabase db=LocalDatabase.get(context);JSONArray rows=db.pending(space,role);java.util.Set<String> blocked=new java.util.HashSet<>();JSONObject selected=null;
  for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);String key=row.optString("resource")+":"+row.optString("entityId");if(!row.optString("state").equals("PENDING")){blocked.add(key);continue;}if(blocked.contains(key))continue;if(row.optLong("nextAttemptAt")>System.currentTimeMillis()){blocked.add(key);continue;}selected=row;break;}
  if(selected==null){pull(g,0,0,null);return;}JSONObject op=selected;
  try{
   String key="frozen:"+op.getString("operationId");String frozen=db.metadata(key);
   if(frozen.isEmpty()){
    JSONObject payload=(JSONObject)resolvePayload(op.getJSONObject("payload"),"");JSONObject entity=db.entity(space,op.getString("resource"),op.getString("entityId"));String remote=entity==null?op.getString("entityId"):entity.optString("remoteId",op.getString("entityId"));
    String client=db.metadata("client-id");if(client.isEmpty()){client=java.util.UUID.randomUUID().toString();db.metadata("client-id",client);}
    frozen=new JSONObject().put("protocolVersion",1).put("clientId",client).put("operationId",op.getString("operationId")).put("action",op.getString("action")).put("entityId",remote).put("baseVersion",op.optLong("baseVersion")).put("payload",payload).put("payloadHash",SyncTransport.hash(payload)).toString();db.metadata(key,frozen);
   }
   op.put("frozenEnvelope",new JSONObject(frozen));ContentValues update=new ContentValues();update.put("state","IN_FLIGHT");update.put("payload_hash",new JSONObject(frozen).getString("payloadHash"));update.put("attempts",op.optInt("attempts")+1);db.getWritableDatabase().update("sync_operations",update,"operation_id=?",new String[]{op.getString("operationId")});
   main.post(()->{if(!valid(g))return;transport.send(new LocalOperation(op),result->LocalRepository.IO.execute(()->{if(!valid(g))return;try{db.acknowledge(op,result);uploadOperations(g);}catch(Exception e){db.getWritableDatabase().execSQL("UPDATE sync_operations SET state='PENDING' WHERE operation_id=? AND state='IN_FLIGHT'",new Object[]{op.optString("operationId")});finish(g,"回执保存失败，重试将核对原操作："+e.getMessage(),5000);}}),error->LocalRepository.IO.execute(()->{if(!valid(g))return;ContentValues failure=new ContentValues();failure.put("state",error.status==401?"WAITING_AUTH":error.status==409?"CONFLICT":error.status==0||error.status>=500||error.status==429?"PENDING":"BLOCKED");failure.put("error",error.code+" · "+error.message);failure.put("next_attempt_at",System.currentTimeMillis()+retryDelay(op.optInt("attempts")+1,error.retryAfterMs));db.getWritableDatabase().update("sync_operations",failure,"operation_id=?",new String[]{op.optString("operationId")});if(error.status==0||error.status>=500||error.status==401||error.status==429)networkFailure(g,error);else uploadOperations(g);}));});
  }catch(Exception e){ContentValues failure=new ContentValues();failure.put("state","BLOCKED");failure.put("error","依赖内容尚未确认："+e.getMessage());db.getWritableDatabase().update("sync_operations",failure,"operation_id=?",new String[]{op.optString("operationId")});LocalRepository.IO.execute(()->uploadOperations(g));}
 }
 private Object resolvePayload(Object value,String key)throws Exception{
  LocalDatabase db=LocalDatabase.get(context);
  if(value instanceof JSONObject){JSONObject input=(JSONObject)value,out=new JSONObject();java.util.Iterator<String> keys=input.keys();while(keys.hasNext()){String name=keys.next();out.put(name,resolvePayload(input.get(name),name));}return out;}
  if(value instanceof JSONArray){JSONArray input=(JSONArray)value,out=new JSONArray();for(int i=0;i<input.length();i++)out.put(resolvePayload(input.get(i),key));return out;}
  if(value instanceof String){String text=(String)value;if(text.startsWith("file://")){String path=android.net.Uri.parse(text).getPath();try(Cursor c=db.getReadableDatabase().rawQuery("SELECT url FROM local_media WHERE space_id=? AND path=? AND state='READY'",new String[]{space,path})){if(!c.moveToFirst()||c.getString(0).isEmpty())throw new IllegalStateException("附件尚未上传");return c.getString(0);}}
   String resource=switch(key){case "postId"->"posts";case "productId"->"merchantProducts";case "addressId"->"addresses";case "groupId"->"checkouts";case "orderId"->"buyerOrders";default->"";};
   if(!resource.isEmpty()&&text.startsWith("local-")){JSONObject entity=db.entity(space,resource,text);if(entity==null||entity.optString("remoteId").isEmpty())throw new IllegalStateException("关联记录尚未上传");return entity.optString("remoteId");}
  }
  return value;
 }
 private void pull(int g,int resourceIndex,int offset,String snapshot){if(!valid(g))return;LocalDatabase db=LocalDatabase.get(context);if(resourceIndex>=resources.length){failures=0;db.metadata("last-sync:"+space,java.time.Instant.now().toString());int pending=db.pending(space).length();finish(g,pending==0?"已同步 · 本地与网络连接正常":"本地已保存 · "+pending+" 条操作需要处理",15000);return;}
  String resource=resources[resourceIndex];
  boolean merchant="MERCHANT".equals(role);
  if((resource.equals("merchantProducts")||resource.equals("merchantOrders"))&&!merchant
      ||merchant&&java.util.Arrays.asList("addresses","cart","favorites","checkouts","buyerOrders","ownRatings").contains(resource)){
   pull(g,resourceIndex+1,0,null);return;
  }
  String path="sync/snapshots/"+resource+"?offset="+offset+"&limit=100"+(snapshot==null?"":"&snapshotId="+snapshot);
  main.post(()->{if(!valid(g))return;transport.json(0,path,null,result->LocalRepository.IO.execute(()->{if(!valid(g))return;try{result.put("offset",offset);new SnapshotImporter(db).applyPage(space,resource,result);if(result.optBoolean("complete"))pull(g,resourceIndex+1,0,null);else pull(g,resourceIndex,result.getInt("nextOffset"),result.getString("snapshotId"));}catch(Exception e){finish(g,"快照未完整保存，原数据保留："+e.getMessage(),15000);}}),error->{if(error.code.equals("SNAPSHOT_EXPIRED")){LocalRepository.IO.execute(()->pull(g,resourceIndex,0,null));}else networkFailure(g,error);});});
 }
}
