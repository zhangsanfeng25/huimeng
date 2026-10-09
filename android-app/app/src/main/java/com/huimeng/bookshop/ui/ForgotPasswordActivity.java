package com.huimeng.bookshop.ui;

import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.huimeng.bookshop.core.ApiClient;
import com.huimeng.bookshop.databinding.ActivityForgotPasswordBinding;
import org.json.JSONObject;

public class ForgotPasswordActivity extends AppCompatActivity{
    private ActivityForgotPasswordBinding binding;
    @Override protected void onCreate(Bundle state){super.onCreate(state);binding=ActivityForgotPasswordBinding.inflate(getLayoutInflater());setContentView(binding.getRoot());binding.roleNote.setText("MERCHANT".equals(getIntent().getStringExtra(LoginActivity.EXTRA_ROLE))?"正在找回商家账号密码":"正在找回用户账号密码");binding.back.setOnClickListener(v->finish());binding.submit.setOnClickListener(v->reset());}
    private void reset(){String account=String.valueOf(binding.account.getText()).trim(),password=String.valueOf(binding.password.getText());if(account.isEmpty()||password.length()<8){toast("请输入账号和至少 8 位新密码");return;}binding.submit.setEnabled(false);try{new ApiClient(this).post("auth/forgot-password",new JSONObject().put("account",account).put("newPassword",password),result->{toast("密码已重置");finish();},error->{binding.submit.setEnabled(true);toast(error);});}catch(Exception ignored){binding.submit.setEnabled(true);}}
    private void toast(String value){Toast.makeText(this,value,Toast.LENGTH_SHORT).show();}
}
