package com.huimeng.bookshop.ui;
import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.huimeng.bookshop.core.local.LocalRepository;
import com.huimeng.bookshop.databinding.ActivityWelcomeBinding;
public class WelcomeActivity extends AppCompatActivity {
    private boolean opening;
    @Override protected void onCreate(Bundle state){super.onCreate(state);ActivityWelcomeBinding binding=ActivityWelcomeBinding.inflate(getLayoutInflater());setContentView(binding.getRoot());binding.skip.setOnClickListener(v->open());open();}
    private void open(){if(opening)return;opening=true;LocalRepository.prepare(this,()->{if(isFinishing()||isDestroyed())return;startActivity(new Intent(this,MainActivity.class));finish();overridePendingTransition(android.R.anim.fade_in,android.R.anim.fade_out);},error->{opening=false;android.widget.Toast.makeText(this,error,android.widget.Toast.LENGTH_LONG).show();});}
}
