package com.huimeng.bookshop.ui;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.huimeng.bookshop.databinding.ActivityPaymentSuccessBinding;

public class PaymentSuccessActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle state){super.onCreate(state);
        ActivityPaymentSuccessBinding binding=ActivityPaymentSuccessBinding.inflate(getLayoutInflater());setContentView(binding.getRoot());
        binding.viewOrders.setOnClickListener(v->{startActivity(new Intent(this,OrdersActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));finish();});
        binding.home.setOnClickListener(v->{startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));finish();});
        if(state==null)binding.check.playOnce();else binding.check.showCompleted();
    }
}
