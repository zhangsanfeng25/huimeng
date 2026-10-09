package com.huimeng.bookshop.ui;

import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.huimeng.bookshop.core.ApiClient;
import com.huimeng.bookshop.databinding.ActivityRegisterBinding;
import org.json.JSONObject;

public class RegisterActivity extends AppCompatActivity{
    private ActivityRegisterBinding binding;
    @Override protected void onCreate(Bundle state){super.onCreate(state);binding=ActivityRegisterBinding.inflate(getLayoutInflater());setContentView(binding.getRoot());binding.roleNote.setText("MERCHANT".equals(getIntent().getStringExtra(LoginActivity.EXTRA_ROLE))?"当前注册只创建用户账号；商家身份由管理员配置":"注册后即可使用用户端功能");binding.back.setOnClickListener(v->finish());binding.submit.setOnClickListener(v->register());}
    private void register(){String username=text(binding.username),nickname=text(binding.nickname),email=text(binding.email),phone=text(binding.phone),password=text(binding.password);if(username.length()<3||nickname.isEmpty()||password.length()<8){toast("账号至少 3 位，密码至少 8 位");return;}binding.submit.setEnabled(false);try{JSONObject body=new JSONObject().put("username",username).put("nickname",nickname).put("password",password);if(!email.isEmpty())body.put("email",email);if(!phone.isEmpty())body.put("phone",phone);new ApiClient(this).post("auth/register",body,result->{toast("注册成功，请登录");finish();},error->{binding.submit.setEnabled(true);toast(error);});}catch(Exception ignored){binding.submit.setEnabled(true);}}
    private String text(android.widget.TextView v){return String.valueOf(v.getText()).trim();}private void toast(String v){Toast.makeText(this,v,Toast.LENGTH_SHORT).show();}
}
