package com.huimeng.bookshop.core;

import android.content.Context;
import android.content.Intent;
import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.huimeng.bookshop.BuildConfig;
import com.huimeng.bookshop.HuiMengApp;
import com.huimeng.bookshop.ui.LoginActivity;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public final class ApiClient {
    private static final String ASSET_ORIGIN=BuildConfig.API_BASE_URL.replaceFirst("/api/v1/$","");
    private static boolean redirecting;
    private final Context context;
    private final SessionStore session;
    public ApiClient(Context context){this.context=context.getApplicationContext();this.session=new SessionStore(context);}
    public void get(String path,Consumer<JSONObject> success,Consumer<String> failure){request(Request.Method.GET,path,null,success,failure);}
    public void post(String path,JSONObject body,Consumer<JSONObject> success,Consumer<String> failure){request(Request.Method.POST,path,body,success,failure);}
    public void post(String path,JSONObject body,Map<String,String> extraHeaders,Consumer<JSONObject> success,Consumer<String> failure){request(Request.Method.POST,path,body,extraHeaders,success,failure);}
    public void put(String path,JSONObject body,Consumer<JSONObject> success,Consumer<String> failure){request(Request.Method.PUT,path,body,success,failure);}
    public void delete(String path,Consumer<JSONObject> success,Consumer<String> failure){request(Request.Method.DELETE,path,null,success,failure);}
    public void uploadJpeg(byte[] bytes,Consumer<JSONObject> success,Consumer<String> failure){
        uploadJpeg("community/uploads",Request.Method.POST,bytes,success,failure);
    }
    public void uploadJpeg(String path,int method,byte[] bytes,Consumer<JSONObject> success,Consumer<String> failure){
        if(session.hasLocalSpace()){
            String space=session.localSpaceId();com.huimeng.bookshop.core.local.LocalRepository.IO.execute(()->{try{
                com.huimeng.bookshop.core.local.LocalMediaStore media=new com.huimeng.bookshop.core.local.LocalMediaStore(context,com.huimeng.bookshop.core.local.LocalDatabase.get(context));
                String id=media.saveJpeg(space,bytes,path.startsWith("merchant/")?"PRODUCT":"COMMUNITY");JSONObject result=new JSONObject().put("url",media.resolveUri(space,id).toString()).put("pending",true);
                new android.os.Handler(android.os.Looper.getMainLooper()).post(()->{if(space.equals(session.localSpaceId()))success.accept(result);});
            }catch(Exception e){new android.os.Handler(android.os.Looper.getMainLooper()).post(()->failure.accept("图片未保存："+e.getMessage()));}});return;
        }
        String token=session.token();
        JsonObjectRequest request=new JsonObjectRequest(method,BuildConfig.API_BASE_URL+path,null,success::accept,error->{
            if(unauthorized(path,error.networkResponse==null?0:error.networkResponse.statusCode,token))return;
            String message="图片上传失败，请检查网络";
            if(error.networkResponse!=null&&error.networkResponse.data!=null)try{message=new JSONObject(new String(error.networkResponse.data)).optString("message",message);}catch(Exception ignored){}
            failure.accept(message);
        }){
            @Override public byte[] getBody(){return bytes;}
            @Override public String getBodyContentType(){return "image/jpeg";}
            @Override public Map<String,String> getHeaders(){Map<String,String> headers=new HashMap<>();headers.put("Authorization","Bearer "+token);return headers;}
        };
        ((HuiMengApp)context).requests().add(request);
    }
    public static String assetUrl(String value){
        if(value==null||value.trim().isEmpty())return "";
        if(value.startsWith("http://")||value.startsWith("https://"))return value;
        if(value.startsWith("file://")||value.startsWith("android.resource://"))return value;
        String offline=com.huimeng.bookshop.core.local.LocalBootstrap.asset(value);if(!offline.isEmpty())return offline;
        return ASSET_ORIGIN+(value.startsWith("/")?value:"/"+value);
    }
    private void request(int method,String path,JSONObject body,Consumer<JSONObject> success,Consumer<String> failure){
        request(method,path,body,new HashMap<>(),success,failure);
    }
    private void request(int method,String path,JSONObject body,Map<String,String> extraHeaders,Consumer<JSONObject> success,Consumer<String> failure){
        if(session.hasLocalSpace()&&(!path.startsWith("auth/")||path.equals("auth/me")||path.equals("auth/me/avatar")||path.equals("auth/switch-role")||path.equals("auth/default-avatars"))){
            if(!(path.equals("auth/me")&&body!=null&&(body.has("newPassword")||body.has("currentPassword")))){
                com.huimeng.bookshop.core.local.LocalRepository.request(context,method,path,body,success,failure);return;
            }
        }
        String token=session.token();
        JsonObjectRequest request=new JsonObjectRequest(method, BuildConfig.API_BASE_URL+path,body,success::accept,error->{
            if(unauthorized(path,error.networkResponse==null?0:error.networkResponse.statusCode,token))return;
            String message="无法连接服务器，请启动控制服务后重试";
            if(error.networkResponse!=null&&error.networkResponse.data!=null){try{message=new JSONObject(new String(error.networkResponse.data)).optString("message",message);}catch(Exception ignored){}}
            failure.accept(message);
        }){
            @Override public Map<String,String> getHeaders() throws AuthFailureError {Map<String,String> headers=new HashMap<>(extraHeaders);if(!token.isEmpty())headers.put("Authorization","Bearer "+token);return headers;}
        };
        request.setTag(context.getClass().getSimpleName());
        request.setShouldCache(false);
        ((HuiMengApp)context).requests().add(request);
    }
    private boolean unauthorized(String path,int status,String requestToken){
        if(status!=401||path.startsWith("auth/"))return false;
        if(!session.token().equals(requestToken))return true;
        if(session.hasLocalSpace()){session.invalidateNetworkCredentials(context);return false;}
        session.clear();
        synchronized(ApiClient.class){if(redirecting)return true;redirecting=true;}
        Intent intent=new Intent(context,LoginActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtra("login_notice","登录已失效，请重新登录");context.startActivity(intent);return true;
    }
    public static synchronized void resetSessionRedirect(){redirecting=false;}
}
