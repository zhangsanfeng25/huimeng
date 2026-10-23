package com.huimeng.bookshop.ui;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.huimeng.bookshop.databinding.ActivityPaymentSuccessBinding;

public class PaymentSuccessActivity extends AppCompatActivity {
    private ActivityPaymentSuccessBinding binding;
    private long remaining=5000,deadline;
    private boolean active,leaving;
    private final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable tick=new Runnable(){public void run(){if(!active||leaving)return;remaining=Math.max(0,deadline-android.os.SystemClock.elapsedRealtime());binding.countdown.setText(((remaining+999)/1000)+" 秒后返回商品页面");if(remaining==0)returnToProduct();else handler.postDelayed(this,200);}};
    @Override protected void onCreate(Bundle state){super.onCreate(state);
        binding=ActivityPaymentSuccessBinding.inflate(getLayoutInflater());setContentView(binding.getRoot());
        if(state!=null)remaining=state.getLong("remaining",5000);
        binding.viewOrders.setOnClickListener(v->{leaving=true;handler.removeCallbacks(tick);startActivity(new Intent(this,OrdersActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));finish();});
        binding.home.setText("返回商品页面");binding.home.setOnClickListener(v->returnToProduct());
        if(state==null)binding.check.playOnce();else binding.check.showCompleted();
    }
    private void returnToProduct(){if(leaving||isFinishing()||isDestroyed())return;leaving=true;handler.removeCallbacks(tick);String product=getIntent().getStringExtra("return_product_id");
        if(product!=null&&!product.isEmpty())startActivity(new Intent(this,ProductDetailActivity.class).putExtra("product_id",product).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));
        else if(getIntent().getBooleanExtra("return_to_cart",false))startActivity(new Intent(this,CartActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
    }
    @Override protected void onResume(){super.onResume();active=true;deadline=android.os.SystemClock.elapsedRealtime()+remaining;tick.run();}
    @Override protected void onPause(){remaining=Math.max(0,deadline-android.os.SystemClock.elapsedRealtime());active=false;handler.removeCallbacks(tick);super.onPause();}
    @Override protected void onSaveInstanceState(Bundle state){state.putLong("remaining",active?Math.max(0,deadline-android.os.SystemClock.elapsedRealtime()):remaining);super.onSaveInstanceState(state);}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);super.onDestroy();}
}
