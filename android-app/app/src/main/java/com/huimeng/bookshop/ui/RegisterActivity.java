package com.huimeng.bookshop.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.huimeng.bookshop.BuildConfig;
import com.huimeng.bookshop.core.OfflineAccounts;
import com.huimeng.bookshop.core.local.LocalRepository;
import com.huimeng.bookshop.databinding.ActivityRegisterBinding;
import org.json.JSONObject;

public class RegisterActivity extends AppCompatActivity {
    private ActivityRegisterBinding binding;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        binding=ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.back.setOnClickListener(view->finish());
        binding.submit.setOnClickListener(view->register());
    }

    private void register() {
        String username=value(binding.username), nickname=value(binding.nickname);
        String email=value(binding.email), phone=value(binding.phone);
        String password=binding.password.getText().toString();
        if(username.length()<3||username.length()>32){AuthForm.error(binding.username,"账号需 3 至 32 位");return;}
        if(nickname.isEmpty()||nickname.length()>30){AuthForm.error(binding.nickname,"请输入昵称，最多 30 字");return;}
        if(!email.isEmpty()&&!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()){AuthForm.error(binding.email,"请输入有效邮箱");return;}
        if(!phone.isEmpty()&&(phone.length()<6||phone.length()>20)){AuthForm.error(binding.phone,"请输入有效手机号");return;}
        if(password.length()<8||password.length()>64){AuthForm.error(binding.password,"密码需 8 至 64 位");return;}
        binding.submit.setEnabled(false);
        LocalRepository.IO.execute(()->{
            try{
                JSONObject request=new JSONObject().put("username",username).put("nickname",nickname).put("password",password);
                if(!email.isEmpty())request.put("email",email);
                if(!phone.isEmpty())request.put("phone",phone);
                new OfflineAccounts(this).register(BuildConfig.API_BASE_URL,request);
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed())return;
                    startActivity(new Intent(this,LoginActivity.class).putExtra("registered_account",username)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
                    finish();
                });
            }catch(Exception error){
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed())return;
                    binding.submit.setEnabled(true);
                    if(error instanceof IllegalArgumentException)AuthForm.error(binding.username,error.getMessage());
                    else Toast.makeText(this,"注册未完成，请重试",Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private String value(EditText field){return field.getText().toString().trim();}
}
