package com.huimeng.bookshop.core.sync;
import android.content.Context;
import com.android.volley.*;
import com.android.volley.toolbox.JsonObjectRequest;
import com.huimeng.bookshop.BuildConfig;
import com.huimeng.bookshop.HuiMengApp;
import com.huimeng.bookshop.core.SessionStore;
import com.huimeng.bookshop.core.local.*;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Consumer;
public final class SyncTransport {
 public static final class TransportError {public final int status;public final long retryAfterMs;public final String message,code;public TransportError(int status,long retryAfterMs,String message,String code){this.status=status;this.retryAfterMs=retryAfterMs;this.message=message;this.code=code;}}
 private final Context context;
 public SyncTransport(Context context){this.context=context.getApplicationContext();}
 public static String canonical(Object value)throws Exception{
  if(value==null||value==JSONObject.NULL)return "null";if(value instanceof String)return quote((String)value);if(value instanceof Boolean)return value.toString();
  if(value instanceof Number){double n=((Number)value).doubleValue();if(!Double.isFinite(n))throw new IllegalArgumentException("同步数据包含无效数字");if(n==0)return "0";java.math.BigDecimal number=new java.math.BigDecimal(value.toString()).stripTrailingZeros();if(Math.abs(n)>=1e21||Math.abs(n)<1e-6){String text=number.toString().toLowerCase(Locale.ROOT);if(text.contains("e")&&!text.contains("e-"))text=text.replace("e+","e").replace("e","e+");return text;}return number.toPlainString();}
  if(value instanceof JSONArray){JSONArray rows=(JSONArray)value;List<String> values=new ArrayList<>();for(int i=0;i<rows.length();i++)values.add(canonical(rows.get(i)));return "["+String.join(",",values)+"]";}
  if(value instanceof JSONObject){JSONObject row=(JSONObject)value;List<String> keys=new ArrayList<>();row.keys().forEachRemaining(keys::add);Collections.sort(keys);List<String> values=new ArrayList<>();for(String key:keys)values.add(quote(key)+":"+canonical(row.get(key)));return "{"+String.join(",",values)+"}";}
  throw new IllegalArgumentException("同步数据格式错误");
 }
 private static String quote(String value){StringBuilder out=new StringBuilder("\"");for(int i=0;i<value.length();i++){char c=value.charAt(i);switch(c){case '"':out.append("\\\"");break;case '\\':out.append("\\\\");break;case '\b':out.append("\\b");break;case '\f':out.append("\\f");break;case '\n':out.append("\\n");break;case '\r':out.append("\\r");break;case '\t':out.append("\\t");break;default:if(c<32||(Character.isHighSurrogate(c)&&(i+1==value.length()||!Character.isLowSurrogate(value.charAt(i+1))))||(Character.isLowSurrogate(c)&&(i==0||!Character.isHighSurrogate(value.charAt(i-1)))))out.append(String.format(Locale.ROOT,"\\u%04x",(int)c));else out.append(c);}}return out.append('"').toString();}
 public static String hash(Object value)throws Exception{StringBuilder out=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(canonical(value).getBytes(StandardCharsets.UTF_8)))out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}
 public void send(LocalOperation op,Consumer<JSONObject> ok,Consumer<TransportError> fail){if(!op.spaceId.equals(new SessionStore(context).localSpaceId())){fail.accept(new TransportError(409,0,"账号空间已切换，旧请求已取消","SPACE_CHANGED"));return;}if(op.frozenEnvelope==null){fail.accept(new TransportError(400,0,"同步请求尚未冻结","LOCAL_ENVELOPE"));return;}json(Request.Method.POST,"sync/operations",op.frozenEnvelope,ok,fail);}
 public void json(int method,String path,JSONObject body,Consumer<JSONObject> ok,Consumer<TransportError> fail){String token=new SessionStore(context).token();JsonObjectRequest request=new JsonObjectRequest(method,BuildConfig.API_BASE_URL+path,body,ok::accept,error->fail.accept(error(error))){@Override public Map<String,String> getHeaders(){Map<String,String> headers=new HashMap<>();if(!token.isEmpty())headers.put("Authorization","Bearer "+token);return headers;}};request.setShouldCache(false);request.setRetryPolicy(new DefaultRetryPolicy(5000,0,1));((HuiMengApp)context).requests().add(request);}
 public void media(byte[] bytes,String id,String purpose,String hash,Consumer<JSONObject> ok,Consumer<TransportError> fail){String token=new SessionStore(context).token();JsonObjectRequest request=new JsonObjectRequest(Request.Method.POST,BuildConfig.API_BASE_URL+"sync/media",null,ok::accept,error->fail.accept(error(error))){@Override public byte[] getBody(){return bytes;}@Override public String getBodyContentType(){return "image/jpeg";}@Override public Map<String,String> getHeaders(){Map<String,String> headers=new HashMap<>();headers.put("Authorization","Bearer "+token);headers.put("X-Operation-Id",id);headers.put("X-Media-Purpose",purpose);headers.put("X-Content-SHA256",hash);return headers;}};request.setRetryPolicy(new DefaultRetryPolicy(10000,0,1));((HuiMengApp)context).requests().add(request);}
 private TransportError error(VolleyError error){int status=error.networkResponse==null?0:error.networkResponse.statusCode;String message="服务未连接，操作已保留本地",code="NETWORK";long retry=0;if(error.networkResponse!=null){try{JSONObject body=new JSONObject(new String(error.networkResponse.data,StandardCharsets.UTF_8));message=body.optString("message",message);code=body.optString("code",code);}catch(Exception ignored){}if(error.networkResponse.headers!=null)try{String value=error.networkResponse.headers.get("Retry-After");retry=Long.parseLong(value)*1000;}catch(Exception ignored){}}return new TransportError(status,retry,message,code);}
}
