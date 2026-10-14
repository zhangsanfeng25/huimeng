package com.huimeng.bookshop.core.local;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.huimeng.bookshop.BuildConfig;
import com.huimeng.bookshop.core.SessionStore;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.function.Consumer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** All local disk work is serialized off the UI thread; network transport is separate. */
public final class LocalRepository {
    public static final ExecutorService IO=Executors.newSingleThreadExecutor();
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private final LocalDatabase db;
    public LocalRepository(Context context){db=LocalDatabase.get(context);}
    public static void prepare(Context context,Runnable ready,Consumer<String> fail){IO.execute(()->{try{
        LocalDatabase db=LocalDatabase.get(context);LocalSpaceStore spaces=new LocalSpaceStore(db);SessionStore session=new SessionStore(context);String space=spaces.ensureActiveSpace();
        if(!session.accountId().isEmpty()&&!session.token().isEmpty()&&spaces.info(space).optString("userId").isEmpty()){
            try{spaces.bind(space,BuildConfig.API_BASE_URL,session.accountId());}catch(android.database.SQLException conflict){space=spaces.forAccount(BuildConfig.API_BASE_URL,session.accountId());spaces.select(space);}
        }
        SampleCatalog.seed(db,space);
        if(db.entity(space,"profile","self")==null)db.saveConfirmed(space,"profile","self",new JSONObject().put("id",session.accountId().isEmpty()?space:session.accountId()).put("username",session.username().isEmpty()?"local_reader":session.username()).put("name",session.name()).put("avatarUrl",session.avatarUrl()).put("roles",new JSONArray().put("USER")).put("activeRole",spaces.info(space).optString("role","USER")),0);
        String legacy=context.getSharedPreferences("huimeng_cart",0).getString("cart_"+session.accountId(),"[]");
        db.importLegacyCart(session.accountId(),space,new JSONArray(legacy));
        session.localSpace(space);MAIN.post(ready);
    }catch(Exception e){MAIN.post(()->fail.accept("本地数据准备失败，原数据已保留："+e.getMessage()));}});}
    public void query(String space,String resource,JSONObject filter,Consumer<JSONObject> ok,Consumer<String> fail){IO.execute(()->{try{JSONObject result=new JSONObject().put("data",filter.has("id")?db.entity(space,resource,filter.optString("id")):db.list(space,resource));MAIN.post(()->ok.accept(result));}catch(Exception e){MAIN.post(()->fail.accept(e.getMessage()));}});}
    public void mutate(String space,String action,JSONObject payload,Consumer<JSONObject> ok,Consumer<String> fail){IO.execute(()->{try{String id=payload.optString("id",java.util.UUID.randomUUID().toString());String resource=payload.getString("resource");String operation=db.mutate(space,resource,id,action,payload,payload.optBoolean("sample"));JSONObject result=new JSONObject().put("id",id).put("operationId",operation).put("syncState","PENDING");MAIN.post(()->ok.accept(result));}catch(Exception e){MAIN.post(()->fail.accept(e.getMessage()));}});}
}
