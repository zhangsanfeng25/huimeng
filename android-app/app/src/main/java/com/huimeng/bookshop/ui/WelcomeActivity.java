package com.huimeng.bookshop.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.appcompat.app.AppCompatActivity;
import com.huimeng.bookshop.core.ApiClient;
import com.huimeng.bookshop.core.SessionStore;
import com.huimeng.bookshop.databinding.ActivityWelcomeBinding;

public class WelcomeActivity extends AppCompatActivity{
    private final Handler handler=new Handler(Looper.getMainLooper());private boolean opened;
    private final Runnable next=this::open;
    @Override protected void onCreate(Bundle state){super.onCreate(state);ActivityWelcomeBinding binding=ActivityWelcomeBinding.inflate(getLayoutInflater());setContentView(binding.getRoot());binding.skip.setOnClickListener(v->open());handler.postDelayed(next,3000);}
    private void open(){if(opened)return;opened=true;handler.removeCallbacks(next);
        SessionStore session=new SessionStore(this);
        if(!session.hasRememberedSession()){navigate(LoginActivity.class,null);return;}
        new ApiClient(this).get("auth/me",result->{
            if(result.optJSONObject("user")==null){session.clear();navigate(LoginActivity.class,"请重新登录");return;}
            org.json.JSONObject profile=result.optJSONObject("user");
            session.updateProfile(profile.optString("username",session.username()),profile.optString("name",session.name()));
            session.setAvatarUrl(profile.optString("avatarUrl",session.avatarUrl()));
            navigate(MainActivity.class,null);
        },error->{
            if(error.contains("登录")||error.contains("失效")||error.contains("认证"))session.clear();
            navigate(LoginActivity.class,"自动登录验证失败："+error);
        });
    }
    private void navigate(Class<?> target,String notice){if(isFinishing()||isDestroyed())return;Intent intent=new Intent(this,target);if(notice!=null)intent.putExtra("login_notice",notice);startActivity(intent);finish();overridePendingTransition(android.R.anim.fade_in,android.R.anim.fade_out);}
    @Override protected void onDestroy(){handler.removeCallbacks(next);super.onDestroy();}
}
