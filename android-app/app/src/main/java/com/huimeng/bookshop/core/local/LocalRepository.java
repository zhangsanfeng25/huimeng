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
    private LocalRepository(){}
    public static void request(Context context,int method,String path,JSONObject body,Consumer<JSONObject> ok,Consumer<String> fail){
        SessionStore session=new SessionStore(context);String space=session.localSpaceId();IO.execute(()->{try{
            JSONObject result=new LocalApi(LocalDatabase.get(context),space).handle(method,path,body);
            MAIN.post(()->{if(!space.equals(session.localSpaceId()))return;JSONObject user=result.optJSONObject("user");if(user!=null&&session.authenticated()){session.updateProfile(user.optString("username",session.username()),user.optString("name",session.name()));session.setAvatarUrl(user.optString("avatarUrl",session.avatarUrl()));}ok.accept(result);if(method!=0)com.huimeng.bookshop.core.sync.SyncCoordinator.get(context).requestSync();});
        }catch(Exception e){MAIN.post(()->{if(space.equals(session.localSpaceId()))fail.accept("本地操作未完成："+e.getMessage());});}});
    }
    public static void prepare(Context context,Runnable ready,Consumer<String> fail){IO.execute(()->{try{
        LocalDatabase db=LocalDatabase.get(context);LocalSpaceStore spaces=new LocalSpaceStore(db);SessionStore session=new SessionStore(context);
        String space=session.authenticated()?spaces.forAccount(BuildConfig.API_BASE_URL,session.accountId()):spaces.guestSpace();spaces.select(space);spaces.role(space,session.role());
        LocalBootstrap.prepare(context,db,space);
        if(db.entity(space,"profile","self")==null)db.saveConfirmed(space,"profile","self",new JSONObject().put("id",session.accountId().isEmpty()?space:session.accountId()).put("username",session.username().isEmpty()?"local_reader":session.username()).put("name",session.name()).put("avatarUrl",session.avatarUrl()).put("roles",new JSONArray().put("USER")).put("activeRole",spaces.info(space).optString("role","USER")),0);
        String legacy=context.getSharedPreferences("huimeng_cart",0).getString("cart_"+session.accountId(),"[]");
        if(session.authenticated())db.importLegacyCart(session.accountId(),space,new JSONArray(legacy));
        session.localSpace(space);MAIN.post(ready);
    }catch(Exception e){MAIN.post(()->fail.accept("本地数据准备失败，原数据已保留："+e.getMessage()));}});}
}
