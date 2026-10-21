package com.huimeng.bookshop.core;

import android.content.Context;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.huimeng.bookshop.BuildConfig;
import com.huimeng.bookshop.HuiMengApp;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Confirmed network operations, separate from the offline outbox; cache is account/role/origin scoped. */
public final class ServiceClient {
    private final Context context;private final SessionStore session;
    public ServiceClient(Context context){this.context=context.getApplicationContext();session=new SessionStore(context);}
    private String scope(){return BuildConfig.API_BASE_URL+"|"+session.accountId()+"|"+session.role()+"|"+session.localSpaceId();}
    public void get(String path,Consumer<JSONObject> ok,Consumer<String> fail){request(Request.Method.GET,path,null,ok,fail);}
    public void post(String path,JSONObject body,Consumer<JSONObject> ok,Consumer<String> fail){request(Request.Method.POST,path,body,ok,fail);}
    private void request(int method,String path,JSONObject body,Consumer<JSONObject> ok,Consumer<String> fail){
        String captured=scope(),token=session.token(),key="service-cache:"+captured+"|"+path;
        var cache=context.getSharedPreferences("huimeng_services",0);
        Consumer<String> failure=message->{if(!captured.equals(scope()))return;if(method==Request.Method.GET){String saved=cache.getString(key,"");if(!saved.isEmpty())try{JSONObject value=new JSONObject(saved);value.put("cached",true);ok.accept(value);return;}catch(Exception ignored){}}fail.accept(message);};
        if(token.isEmpty()){failure.accept("请先关联并登录原网络账号，聊天、售后和反馈需联网确认");return;}
        JsonObjectRequest request=new JsonObjectRequest(method,BuildConfig.API_BASE_URL+path,body,result->{if(!captured.equals(scope()))return;if(method==Request.Method.GET)cache.edit().putString(key,result.toString()).apply();ok.accept(result);},error->{
            if(!captured.equals(scope()))return;String message="服务未连接，内容尚未提交，请检查网络后重试";
            if(error.networkResponse!=null){try{message=new JSONObject(new String(error.networkResponse.data,java.nio.charset.StandardCharsets.UTF_8)).optString("message",message);}catch(Exception ignored){}if(error.networkResponse.statusCode==401){if(token.equals(session.token()))session.clearNetworkSession();message="网络登录已失效，请重新登录原账号；本地内容已保留";}}
            failure.accept(message);
        }){@Override public Map<String,String> getHeaders(){Map<String,String> headers=new HashMap<>();headers.put("Authorization","Bearer "+token);headers.put("X-Service-Role",session.role());return headers;}};
        request.setShouldCache(false);request.setRetryPolicy(new com.android.volley.DefaultRetryPolicy(7000,0,1));((HuiMengApp)context).requests().add(request);
    }
    public void upload(byte[] jpeg,Consumer<JSONObject> ok,Consumer<String> fail){
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
