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
    @Override protected void onCreate(Bundle state){super.onCreate(state);ActivityWelcomeBinding binding=ActivityWelcomeBinding.inflate(getLayoutInflater());setContentView(binding.getRoot());binding.skip.setOnClickListener(v->open());open();}
    private void open(){if(opened)return;opened=true;handler.removeCallbacks(next);
        com.huimeng.bookshop.core.local.LocalRepository.prepare(this,()->navigate(MainActivity.class,null),error->{opened=false;android.widget.Toast.makeText(this,error,android.widget.Toast.LENGTH_LONG).show();});
    }
    private void navigate(Class<?> target,String notice){if(isFinishing()||isDestroyed())return;Intent intent=new Intent(this,target);if(notice!=null)intent.putExtra("login_notice",notice);startActivity(intent);finish();overridePendingTransition(android.R.anim.fade_in,android.R.anim.fade_out);}
    @Override protected void onDestroy(){handler.removeCallbacks(next);super.onDestroy();}
}
