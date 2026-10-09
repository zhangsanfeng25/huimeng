package com.huimeng.bookshop.ui;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.huimeng.bookshop.databinding.ActivityNotificationsBinding;

public final class NotificationsActivity extends AppCompatActivity{
    @Override protected void onCreate(Bundle state){super.onCreate(state);
        ActivityNotificationsBinding binding=ActivityNotificationsBinding.inflate(getLayoutInflater());setContentView(binding.getRoot());binding.back.setOnClickListener(v->finish());
        if(state==null)getSupportFragmentManager().beginTransaction().replace(binding.notificationsContainer.getId(),new NotificationFragment()).commit();
    }
}
