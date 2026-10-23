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
    private void reset(){String account=String.valueOf(binding.account.getText()).trim(),password=String.valueOf(binding.password.getText());
        if(account.isEmpty()||password.length()<8||password.length()>64){error("请输入已保存的邮箱或手机号，以及 8 至 64 位新密码");return;}
        if(!password.contentEquals(binding.confirmPassword.getText())){error("两次输入的密码不一致");return;}
        binding.submit.setEnabled(false);binding.message.setText("正在核对联系方式…");
        try{new ApiClient(this).post("auth/forgot-password",new JSONObject().put("account",account).put("newPassword",password),result->{
            final String id=result.optString("userId");
            com.huimeng.bookshop.core.local.LocalRepository.IO.execute(()->{
                try{new com.huimeng.bookshop.core.OfflineAccounts(this).forgetAccount(com.huimeng.bookshop.BuildConfig.API_BASE_URL,id);}catch(Exception ignored){}
                runOnUiThread(()->{if(isFinishing()||isDestroyed())return;com.huimeng.bookshop.core.SessionStore session=new com.huimeng.bookshop.core.SessionStore(this);if(id.equals(session.accountId())){com.huimeng.bookshop.core.sync.SyncCoordinator.get(this).stop();session.clear();}toast("密码已重置，请使用新密码登录");finish();});
            });
        },value->{if(isFinishing()||isDestroyed())return;binding.submit.setEnabled(true);error(value);});}catch(Exception ignored){binding.submit.setEnabled(true);error("重置请求未发送，请重试");}}
    private void error(String value){binding.message.setText(value);toast(value);}
    private void toast(String value){Toast.makeText(this,value,Toast.LENGTH_SHORT).show();}
}
