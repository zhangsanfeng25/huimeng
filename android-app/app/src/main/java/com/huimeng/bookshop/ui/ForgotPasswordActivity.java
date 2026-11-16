package com.huimeng.bookshop.ui;

import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.huimeng.bookshop.core.ApiClient;
import com.huimeng.bookshop.databinding.ActivityForgotPasswordBinding;
import org.json.JSONObject;

public class ForgotPasswordActivity extends AppCompatActivity{
    private ActivityForgotPasswordBinding binding;
    @Override protected void onCreate(Bundle state){super.onCreate(state);binding=ActivityForgotPasswordBinding.inflate(getLayoutInflater());setContentView(binding.getRoot());binding.back.setOnClickListener(v->finish());binding.submit.setOnClickListener(v->reset());}
    private void reset(){String account=String.valueOf(binding.account.getText()).trim(),password=String.valueOf(binding.password.getText());
        if(account.isEmpty()){AuthForm.error(binding.account,"请输入已绑定的邮箱或手机号");return;}
        if(password.length()<8||password.length()>64){AuthForm.error(binding.password,"密码需 8 至 64 位");return;}
        if(!password.contentEquals(binding.confirmPassword.getText())){AuthForm.error(binding.confirmPassword,"两次输入的密码不一致");return;}
        binding.submit.setEnabled(false);binding.submit.setText("正在重置…");
        try{new ApiClient(this).post("auth/forgot-password",new JSONObject().put("account",account).put("newPassword",password),result->{
            final String id=result.optString("userId");
            com.huimeng.bookshop.core.local.LocalRepository.IO.execute(()->{
                try{new com.huimeng.bookshop.core.OfflineAccounts(this).forgetAccount(com.huimeng.bookshop.BuildConfig.API_BASE_URL,id);}catch(Exception ignored){}
                runOnUiThread(()->{if(isFinishing()||isDestroyed())return;com.huimeng.bookshop.core.SessionStore session=new com.huimeng.bookshop.core.SessionStore(this);if(id.equals(session.accountId())){com.huimeng.bookshop.core.sync.SyncCoordinator.get(this).stop();session.clear();}toast("密码已重置，请使用新密码登录");finish();});
            });
        },value->{if(isFinishing()||isDestroyed())return;binding.submit.setEnabled(true);binding.submit.setText("确认重置");error("重置未完成，请重试");});}catch(Exception ignored){binding.submit.setEnabled(true);binding.submit.setText("确认重置");error("重置请求未发送，请重试");}}
    private void error(String value){toast(value);}
    private void toast(String value){Toast.makeText(this,value,Toast.LENGTH_SHORT).show();}
}
