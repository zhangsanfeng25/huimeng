package com.huimeng.bookshop.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.MotionEvent;
import androidx.appcompat.app.AppCompatActivity;
import com.huimeng.bookshop.R;
import com.huimeng.bookshop.core.ApiClient;
import com.huimeng.bookshop.core.SessionStore;
import com.huimeng.bookshop.databinding.ActivityLoginBinding;
import org.json.JSONObject;

public class LoginActivity extends AppCompatActivity {
    public static final String EXTRA_ROLE = "login_role";
    private ActivityLoginBinding binding;
    private String role="USER";
    private float touchStartX;
    private float touchStartY;
    @Override protected void onCreate(Bundle state){super.onCreate(state);binding=ActivityLoginBinding.inflate(getLayoutInflater());setContentView(binding.getRoot());
        new SessionStore(this);
        // Android 8.0 has no light-navigation-bar theme attribute: keep white icons readable.
        if(android.os.Build.VERSION.SDK_INT<27)getWindow().setNavigationBarColor(getColor(R.color.auth_green));
        float density=getResources().getDisplayMetrics().density;
        android.view.ViewGroup.LayoutParams contentParams=binding.authContent.getLayoutParams();
        contentParams.width=Math.min(getResources().getDisplayMetrics().widthPixels,(int)(480*density));
        binding.authContent.setLayoutParams(contentParams);
        android.view.ViewGroup.LayoutParams heroParams=binding.heroFrame.getLayoutParams();
        heroParams.height=(int)(Math.min(280, getResources().getConfiguration().screenWidthDp*0.62f)*density);
        binding.heroFrame.setLayoutParams(heroParams);
        setRole(state==null?"USER":state.getString(EXTRA_ROLE,"USER"));
        binding.buyerTab.setOnClickListener(v->setRole("USER"));
        binding.merchantTab.setOnClickListener(v->setRole("MERCHANT"));
        binding.roleSwitch.setOnClickListener(v->setRole("USER".equals(role)?"MERCHANT":"USER"));
        binding.loginButton.setOnClickListener(v->login());
        binding.register.setOnClickListener(v->openAuthPage(RegisterActivity.class));
        binding.forgotPassword.setText("忘记密码");
        binding.forgotPassword.setOnClickListener(v->openAuthPage(ForgotPasswordActivity.class));
        String notice=getIntent().getStringExtra("login_notice");if(notice!=null)binding.message.setText(notice);
        if(getIntent().getBooleanExtra("return_to_payment",false)){binding.accountInput.setText(getIntent().getStringExtra("login_account"));binding.merchantTab.setEnabled(false);binding.roleSwitch.setVisibility(android.view.View.GONE);}
    }
    @Override protected void onSaveInstanceState(Bundle state){state.putString(EXTRA_ROLE,role);super.onSaveInstanceState(state);}
    private void openAuthPage(Class<?> target){startActivity(new Intent(this,target).putExtra(EXTRA_ROLE,role));}
    @Override public boolean dispatchTouchEvent(MotionEvent event){
        if (!binding.loginButton.isEnabled()) return super.dispatchTouchEvent(event);
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN){
            touchStartX=event.getX();
            touchStartY=event.getY();
        }else if(event.getActionMasked()==MotionEvent.ACTION_UP){
            float dx=event.getX()-touchStartX;
            float dy=event.getY()-touchStartY;
            float minDistance=72*getResources().getDisplayMetrics().density;
            if(Math.abs(dx)>minDistance&&Math.abs(dx)>Math.abs(dy)*1.5f){
                if(dx<0&&"USER".equals(role)){setRole("MERCHANT");return true;}
                if(dx>0&&"MERCHANT".equals(role)){setRole("USER");return true;}
            }
        }
        return super.dispatchTouchEvent(event);
    }
    private void setRole(String newRole){
        if(getIntent().getBooleanExtra("return_to_payment",false))newRole="USER";
        if(!newRole.equals(role)){
            binding.accountInput.setText("");
            binding.passwordInput.setText("");
        }
        role=newRole;
        boolean merchant="MERCHANT".equals(role);
        binding.roleTitle.setText(merchant?"商家登录":"用户登录");
        binding.roleSubtitle.setText(merchant?"经营一家好店，让好绘本遇见孩子":"让每一个孩子与好故事相遇");
        binding.forestHero.setImageResource(merchant?R.drawable.login_forest_merchant:R.drawable.login_forest_user);
        binding.roleSwitch.setText(merchant?"←  向右滑动，返回用户登录":"向左滑动，切换商家登录  →");
        binding.roleSwitch.setContentDescription(merchant?"切换到用户登录":"切换到商家登录");
        binding.roleTabs.post(()->{
            int width=(binding.roleTabs.getWidth()-binding.roleTabs.getPaddingLeft()-binding.roleTabs.getPaddingRight())/2;
            if(width<=0)return;
            android.widget.FrameLayout.LayoutParams params=(android.widget.FrameLayout.LayoutParams)binding.roleIndicator.getLayoutParams();
            if(params.width!=width){params.width=width;binding.roleIndicator.setLayoutParams(params);}
            binding.roleIndicator.animate().translationX(merchant?width:0).setDuration(220).start();
        });
        binding.buyerTab.setTextColor(merchant?getColor(R.color.auth_ink):android.graphics.Color.WHITE);
        binding.merchantTab.setTextColor(merchant?android.graphics.Color.WHITE:getColor(R.color.auth_ink));
        binding.buyerTab.setSelected(!merchant);binding.merchantTab.setSelected(merchant);
        binding.demoAccount.setText(merchant?"演示账号：merchant_a / demo12345":"演示账号：xingxing / demo12345");
        binding.message.setText("");
    }
    private void setLoginBusy(boolean busy) {
        binding.loginButton.setEnabled(!busy);
        binding.buyerTab.setEnabled(!busy);
        binding.merchantTab.setEnabled(!busy&&!getIntent().getBooleanExtra("return_to_payment",false));
        binding.roleSwitch.setEnabled(!busy);
        binding.register.setEnabled(!busy);
        binding.forgotPassword.setEnabled(!busy);
    }
    private void login(){
        String account=String.valueOf(binding.accountInput.getText()).trim();
        String password=String.valueOf(binding.passwordInput.getText());
        if(account.isEmpty()||password.length()<8){binding.message.setText("请输入账号和至少 8 位密码");return;}
        final String requestedRole=role;
        final boolean remember=binding.rememberLogin.isChecked();
        setLoginBusy(true);
        binding.message.setText("正在登录…");
        try {
            JSONObject body=new JSONObject().put("username",account).put("password",password).put("role",requestedRole);
            new com.huimeng.bookshop.core.sync.SyncTransport(this).json(1,"auth/login",body,result->{
                if(isFinishing()||isDestroyed())return;
                JSONObject user=result.optJSONObject("user");
                boolean canMerchant=false;
                if(user!=null&&user.optJSONArray("roles")!=null)
                    for(int i=0;i<user.optJSONArray("roles").length();i++)
                        if("MERCHANT".equals(user.optJSONArray("roles").optString(i)))canMerchant=true;
                if(user==null||user.optString("id").isEmpty()||result.optString("token").isEmpty()){setLoginBusy(false);binding.message.setText("网络账号验证结果不完整");return;}
                String required=getIntent().getStringExtra("required_account_id");if(getIntent().getBooleanExtra("return_to_payment",false)&&required!=null&&!required.isEmpty()&&!required.equals(user.optString("id"))){setLoginBusy(false);binding.message.setText("请登录本订单所属的原账号，当前账号不能继续这笔付款");return;}
                associate(result,user,requestedRole,canMerchant,remember,account,password,false);
            },error->{
                if(isFinishing()||isDestroyed())return;
                if(error.status==0)offlineLogin(account,password,requestedRole,remember);
                else if(error.status==401){com.huimeng.bookshop.core.local.LocalRepository.IO.execute(()->{
                    try{new com.huimeng.bookshop.core.OfflineAccounts(this).forgetAlias(com.huimeng.bookshop.BuildConfig.API_BASE_URL,account);}catch(Exception ignored){}
                    runOnUiThread(()->{if(isFinishing()||isDestroyed())return;setLoginBusy(false);binding.message.setText(error.message);});
                });}else{setLoginBusy(false);binding.message.setText(error.message);}
            });
        }catch(Exception error){setLoginBusy(false);binding.message.setText("登录参数错误");}
    }
    private void offlineLogin(String account,String password,String requestedRole,boolean remember){
        com.huimeng.bookshop.core.local.LocalRepository.IO.execute(()->{try{
            JSONObject result=new com.huimeng.bookshop.core.OfflineAccounts(this).login(com.huimeng.bookshop.BuildConfig.API_BASE_URL,account,password,requestedRole);
            if(result==null){runOnUiThread(()->{if(isFinishing()||isDestroyed())return;setLoginBusy(false);binding.message.setText("无法连接服务器，请启动控制服务后重试。此账号尚未在本机验证，首次登录需要联网。");});return;}
            JSONObject user=result.getJSONObject("user");String required=getIntent().getStringExtra("required_account_id");
            if(required!=null&&!required.isEmpty()&&!required.equals(user.optString("id")))throw new IllegalArgumentException("请登录本订单所属的原账号");
            org.json.JSONArray roles=user.optJSONArray("roles");boolean merchant=false;if(roles!=null)for(int i=0;i<roles.length();i++)if("MERCHANT".equals(roles.optString(i)))merchant=true;
            final boolean allowed=merchant;runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())associate(result,user,requestedRole,allowed,remember,account,password,true);});
        }catch(Exception e){runOnUiThread(()->{if(isFinishing()||isDestroyed())return;setLoginBusy(false);binding.message.setText(e instanceof IllegalArgumentException?e.getMessage():"无法读取本机登录记录，请启动控制服务后登录。");});}});
    }
    private void openMain(){if(getIntent().getBooleanExtra("return_to_payment",false)){setResult(RESULT_OK);finish();return;}startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));finish();}
    private void associate(JSONObject result,JSONObject user,String requestedRole,boolean canMerchant,boolean remember,String account,String password,boolean offline){
        com.huimeng.bookshop.core.local.LocalRepository.IO.execute(()->{try{
            SessionStore session=new SessionStore(this);com.huimeng.bookshop.core.local.LocalDatabase db=com.huimeng.bookshop.core.local.LocalDatabase.get(this);com.huimeng.bookshop.core.local.LocalSpaceStore spaces=new com.huimeng.bookshop.core.local.LocalSpaceStore(db);
            if(isFinishing()||isDestroyed())return;
            String origin=com.huimeng.bookshop.BuildConfig.API_BASE_URL,space=spaces.forAccount(origin,user.getString("id"));
            spaces.select(space);spaces.role(space,requestedRole);com.huimeng.bookshop.core.local.LocalBootstrap.prepare(this,db,space);db.saveConfirmed(space,"profile","self",user,user.optLong("version"));
            final String selectedSpace=space;
            if(!offline)try{new com.huimeng.bookshop.core.OfflineAccounts(this).save(origin,account,password,result);}catch(Exception cacheError){android.util.Log.w("HuiMengLogin","Offline login cache could not be saved");}
            runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()){com.huimeng.bookshop.core.sync.SyncCoordinator.get(this).stop();session.save(result.optString("token"),requestedRole,user.optString("name"),canMerchant,user.optString("id"),user.optString("username"),remember);session.localSpace(selectedSpace);session.setAvatarUrl(user.optString("avatarUrl"));ApiClient.resetSessionRedirect();openMain();}});
        }catch(Exception e){runOnUiThread(()->{if(isFinishing()||isDestroyed())return;setLoginBusy(false);binding.message.setText("登录未完成，原数据已保留，请重试。");});}});
    }
}
