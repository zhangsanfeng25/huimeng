package com.huimeng.bookshop;

import android.app.Application;
import android.app.Activity;
import android.os.Bundle;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;
import com.huimeng.bookshop.core.AppearanceStore;
import com.huimeng.bookshop.core.WallpaperStore;

public class HuiMengApp extends Application {
    private RequestQueue requestQueue;
    @Override public void onCreate() { super.onCreate(); new AppearanceStore(this).apply();registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks(){
        @Override public void onActivityCreated(Activity activity,Bundle state){}
        @Override public void onActivityResumed(Activity activity){new WallpaperStore(activity).apply(activity);}
        @Override public void onActivityStarted(Activity activity){}
        @Override public void onActivityPaused(Activity activity){}
        @Override public void onActivityStopped(Activity activity){}
        @Override public void onActivitySaveInstanceState(Activity activity,Bundle state){}
        @Override public void onActivityDestroyed(Activity activity){}
    });}
    public synchronized RequestQueue requests() {
        if (requestQueue == null) requestQueue = Volley.newRequestQueue(this);
        return requestQueue;
    }
}
