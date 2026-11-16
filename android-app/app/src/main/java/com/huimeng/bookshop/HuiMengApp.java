package com.huimeng.bookshop;

import android.app.Application;
import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;
import com.huimeng.bookshop.core.AppearanceStore;
import com.huimeng.bookshop.core.WallpaperStore;

public class HuiMengApp extends Application {
    private RequestQueue requestQueue;
    private int visibleActivities;
    @Override public void onCreate() { super.onCreate(); new AppearanceStore(this).apply();registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks(){
        @Override public void onActivityCreated(Activity activity,Bundle state){}
        @Override public void onActivityPostCreated(Activity activity,Bundle state){
            if(Build.VERSION.SDK_INT>=30&&!(activity instanceof com.huimeng.bookshop.ui.WelcomeActivity)){
                WindowCompat.setDecorFitsSystemWindows(activity.getWindow(),false);
                View content=activity.findViewById(android.R.id.content);
                if(content!=null)applyContentInsets(content);
            }
        }
        @Override public void onActivityResumed(Activity activity){new WallpaperStore(activity).apply(activity);}
        @Override public void onActivityStarted(Activity activity){
            boolean foreground=visibleActivities++==0;
            if(foreground&&activity instanceof com.huimeng.bookshop.ui.MainActivity main)main.onAppForeground();
            com.huimeng.bookshop.core.SessionStore session=new com.huimeng.bookshop.core.SessionStore(HuiMengApp.this);
            if(session.hasLocalSpace())com.huimeng.bookshop.core.sync.SyncCoordinator.get(HuiMengApp.this).start(session.localSpaceId());
        }
        @Override public void onActivityPaused(Activity activity){}
        @Override public void onActivityStopped(Activity activity){visibleActivities--;new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->{if(visibleActivities==0)com.huimeng.bookshop.core.sync.SyncCoordinator.get(HuiMengApp.this).stop();},300);}
        @Override public void onActivitySaveInstanceState(Activity activity,Bundle state){}
        @Override public void onActivityDestroyed(Activity activity){}
    });
        android.net.ConnectivityManager network=(android.net.ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        if(network!=null)network.registerDefaultNetworkCallback(new android.net.ConnectivityManager.NetworkCallback(){@Override public void onAvailable(android.net.Network value){com.huimeng.bookshop.core.sync.SyncCoordinator.get(HuiMengApp.this).requestSync();}});
    }
    public synchronized RequestQueue requests() {
        if (requestQueue == null) requestQueue = Volley.newRequestQueue(this);
        return requestQueue;
    }

    private static void applyContentInsets(View content) {
        int left=content.getPaddingLeft(), top=content.getPaddingTop();
        int right=content.getPaddingRight(), bottom=content.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(content,(view,insets)->{
            var safe=insets.getInsets(WindowInsetsCompat.Type.systemBars()
                | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
            view.setPadding(left+safe.left,top+safe.top,right+safe.right,bottom+safe.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(content);
    }
}
