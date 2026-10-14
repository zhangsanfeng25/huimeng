package com.huimeng.bookshop.core.sync;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.DefaultLifecycleObserver;
import org.json.JSONObject;
import java.util.Map;
import java.util.HashMap;
import java.util.function.Consumer;
public final class SyncStatusStore {
 private static final Map<LifecycleOwner,Consumer<JSONObject>> observers=new HashMap<>();
 private static JSONObject value=new JSONObject();
 private SyncStatusStore(){}
 public static void observe(LifecycleOwner owner,Consumer<JSONObject> listener){observers.put(owner,listener);owner.getLifecycle().addObserver(new DefaultLifecycleObserver(){@Override public void onDestroy(LifecycleOwner gone){observers.remove(owner);}});listener.accept(value);}
 public static void publish(String message,int pending){try{value=new JSONObject().put("message",message).put("pending",pending);}catch(Exception ignored){}for(Consumer<JSONObject> callback:new java.util.ArrayList<>(observers.values()))callback.accept(value);}
}
