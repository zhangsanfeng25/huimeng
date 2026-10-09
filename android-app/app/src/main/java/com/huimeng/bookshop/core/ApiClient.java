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
        String origin=BuildConfig.API_BASE_URL.replaceFirst("/api/v1/$","");
        return origin+(value.startsWith("/")?value:"/"+value);
    }
    private void request(int method,String path,JSONObject body,Consumer<JSONObject> success,Consumer<String> failure){
        request(method,path,body,new HashMap<>(),success,failure);
    }
    private void request(int method,String path,JSONObject body,Map<String,String> extraHeaders,Consumer<JSONObject> success,Consumer<String> failure){
        String token=session.token();
        JsonObjectRequest request=new JsonObjectRequest(method, BuildConfig.API_BASE_URL+path,body,success::accept,error->{
            if(unauthorized(path,error.networkResponse==null?0:error.networkResponse.statusCode,token))return;
            String message="网络连接失败，请确认本地服务已启动";
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
        session.clear();
        synchronized(ApiClient.class){if(redirecting)return true;redirecting=true;}
        Intent intent=new Intent(context,LoginActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtra("login_notice","登录已失效，请重新登录");context.startActivity(intent);return true;
    }
    public static synchronized void resetSessionRedirect(){redirecting=false;}
}
