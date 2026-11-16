package com.huimeng.bookshop.core;

import android.content.Context;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.huimeng.bookshop.BuildConfig;
import com.huimeng.bookshop.HuiMengApp;
import org.json.JSONObject;
import android.os.Handler;
import android.os.Looper;
import com.huimeng.bookshop.core.local.LocalDatabase;
import com.huimeng.bookshop.core.local.LocalRepository;
import com.huimeng.bookshop.core.local.ServiceRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Customer services with a local buyer view and account-scoped network cache. */
public final class ServiceClient {
    private final Context context;private final SessionStore session;
    private final Handler main=new Handler(Looper.getMainLooper());
    public ServiceClient(Context context){this.context=context.getApplicationContext();session=new SessionStore(context);}
    private String scope(){return BuildConfig.API_BASE_URL+"|"+session.accountId()+"|"+session.role()+"|"+session.localSpaceId();}
    private boolean localBuyerService(String path){
        return session.authenticated()&&session.hasLocalSpace()&&session.role().equals("USER")
            &&(path.equals("service/feedback")||path.startsWith("service/feedback/")
                ||path.equals("service/after-sales")||path.startsWith("service/after-sales/")
                ||path.startsWith("service/order-after-sale/"));
    }
    public void get(String path,Consumer<JSONObject> ok,Consumer<String> fail){
        if(!localBuyerService(path)){request(Request.Method.GET,path,null,ok,fail);return;}
        String captured=scope(),space=session.localSpaceId();
        LocalRepository.IO.execute(()->{
            ServiceRepository services=new ServiceRepository(LocalDatabase.get(context),space);
            String remote;
            try{
                JSONObject owner=new com.huimeng.bookshop.core.local.LocalSpaceStore(LocalDatabase.get(context)).info(space);
                if(!owner.optString("userId").equals(session.accountId())||!owner.optString("origin").equals(BuildConfig.API_BASE_URL))
                    throw new IllegalStateException("账号空间已变化");
                remote=services.networkPath(path);
            }catch(Exception error){
                main.post(()->{if(captured.equals(scope()))fail.accept("暂时无法读取这条记录");});
                return;
            }
            main.post(()->{
                if(!captured.equals(scope()))return;
                if(remote.isEmpty()||session.token().isEmpty()){
                    readLocal(services,path,remote.isEmpty()?null:cached(captured,remote),captured,ok,fail);
                }else{
                    request(Request.Method.GET,remote,null,
                        result->readLocal(services,path,result,captured,ok,fail),
                        error->readLocal(services,path,null,captured,ok,fail));
                }
            });
        });
    }
    private void readLocal(ServiceRepository services,String path,JSONObject result,String captured,
                           Consumer<JSONObject> ok,Consumer<String> fail){
        LocalRepository.IO.execute(()->{
            try{
                JSONObject view=services.read(path,result);
                main.post(()->{if(captured.equals(scope()))ok.accept(view);});
            }catch(Exception error){
                main.post(()->{if(captured.equals(scope()))fail.accept("暂时无法读取这条记录");});
            }
        });
    }
    public void post(String path,JSONObject body,Consumer<JSONObject> ok,Consumer<String> fail){
        if(!localBuyerService(path)){request(Request.Method.POST,path,body,ok,fail);return;}
        String captured=scope(),space=session.localSpaceId();
        LocalRepository.IO.execute(()->{
            try{
                LocalDatabase db=LocalDatabase.get(context);
                JSONObject owner=new com.huimeng.bookshop.core.local.LocalSpaceStore(db).info(space);
                if(!owner.optString("userId").equals(session.accountId())||!owner.optString("origin").equals(BuildConfig.API_BASE_URL))
                    throw new IllegalStateException("账号空间已变化");
                JSONObject result=new ServiceRepository(db,space).write(path,body);
                main.post(()->{
                    if(!captured.equals(scope()))return;
                    ok.accept(result);
                    com.huimeng.bookshop.core.sync.SyncCoordinator.get(context).start(space);
                });
            }catch(Exception error){
                String message=error instanceof IllegalArgumentException?error.getMessage():"提交未完成，请重试";
                main.post(()->{if(captured.equals(scope()))fail.accept(message);});
            }
        });
    }
    private JSONObject cached(String account,String path){
        String key="service-cache:"+account+"|"+path;
        String saved=context.getSharedPreferences("huimeng_services",0).getString(key,"");
        if(saved.isEmpty())return null;
        try{return new JSONObject(saved).put("cached",true);}
        catch(org.json.JSONException error){return null;}
    }
    private void request(int method,String path,JSONObject body,Consumer<JSONObject> ok,Consumer<String> fail){
        String captured=scope(),token=session.token(),key="service-cache:"+captured+"|"+path;
        var cache=context.getSharedPreferences("huimeng_services",0);
        Consumer<String> failure=message->{if(!captured.equals(scope()))return;if(method==Request.Method.GET){String saved=cache.getString(key,"");if(!saved.isEmpty())try{JSONObject value=new JSONObject(saved);value.put("cached",true);ok.accept(value);return;}catch(Exception ignored){}}fail.accept(message);};
        if(token.isEmpty()){failure.accept(session.authenticated()?"请重新登录":"请先登录后操作");return;}
        JsonObjectRequest request=new JsonObjectRequest(method,BuildConfig.API_BASE_URL+path,body,result->{if(!captured.equals(scope()))return;if(method==Request.Method.GET)cache.edit().putString(key,result.toString()).apply();ok.accept(result);},error->{
            if(!captured.equals(scope()))return;String message="暂时无法完成，请重试";
            if(error.networkResponse!=null){try{message=new JSONObject(new String(error.networkResponse.data,java.nio.charset.StandardCharsets.UTF_8)).optString("message",message);}catch(Exception ignored){}if(error.networkResponse.statusCode==401){if(token.equals(session.token()))session.invalidateNetworkCredentials(context);message="请重新登录";}}
            failure.accept(message);
        }){@Override public Map<String,String> getHeaders(){Map<String,String> headers=new HashMap<>();headers.put("Authorization","Bearer "+token);headers.put("X-Service-Role",session.role());return headers;}};
        request.setShouldCache(false);request.setRetryPolicy(new com.android.volley.DefaultRetryPolicy(localBuyerService(path)?2500:7000,0,1));((HuiMengApp)context).requests().add(request);
    }
    public void upload(byte[] jpeg,Consumer<JSONObject> ok,Consumer<String> fail){
        if(localBuyerService("service/after-sales")){
            new ApiClient(context).uploadJpeg(jpeg,ok,fail);
            return;
        }
        String token=session.token(),captured=scope();if(token.isEmpty()){fail.accept("请先登录网络账号");return;}
        JsonObjectRequest request=new JsonObjectRequest(Request.Method.POST,BuildConfig.API_BASE_URL+"community/uploads",null,result->{if(captured.equals(scope()))ok.accept(result);},error->{if(captured.equals(scope()))fail.accept("凭证上传失败，请选择较小图片并检查网络");}){
            @Override public byte[] getBody(){return jpeg;}@Override public String getBodyContentType(){return "image/jpeg";}
            @Override public Map<String,String> getHeaders(){return Map.of("Authorization","Bearer "+token);}
        };request.setShouldCache(false);request.setRetryPolicy(new com.android.volley.DefaultRetryPolicy(10000,0,1));((HuiMengApp)context).requests().add(request);
    }
    public static void remoteOrder(Context context,String id,Consumer<String> ok,Consumer<String> fail){
        if(id==null||id.isEmpty()){fail.accept("订单不存在");return;}if(!id.startsWith("local-")){ok.accept(id);return;}
        String space=new SessionStore(context).localSpaceId();com.huimeng.bookshop.core.local.LocalRepository.IO.execute(()->{
            JSONObject order=com.huimeng.bookshop.core.local.LocalDatabase.get(context).entity(space,"buyerOrders",id);String remote=order==null?"":order.optString("remoteId");
            new android.os.Handler(android.os.Looper.getMainLooper()).post(()->{if(!space.equals(new SessionStore(context).localSpaceId()))return;if(remote.isEmpty()||remote.startsWith("local-"))fail.accept("订单尚未上传，请先登录网络账号并完成同步");else ok.accept(remote);});
        });
    }
}
