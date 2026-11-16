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
        SessionStore session=new SessionStore(this);
        boolean paymentLogin=getIntent().getBooleanExtra("return_to_payment",false);
        if(session.authenticated()&&(!paymentLogin||!"USER".equals(session.role()))){
            android.widget.Toast.makeText(this,"请先退出当前账号，再选择登录身份",android.widget.Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        // Android 8.0 has no light-navigation-bar theme attribute: keep white icons readable.
        if(android.os.Build.VERSION.SDK_INT<27)getWindow().setNavigationBarColor(getColor(R.color.auth_green));
        float density=getResources().getDisplayMetrics().density;
        android.view.ViewGroup.LayoutParams contentParams=binding.authContent.getLayoutParams();
        contentParams.width=Math.min(getResources().getDisplayMetrics().widthPixels,(int)(480*density));
        binding.authContent.setLayoutParams(contentParams);
        android.view.ViewGroup.LayoutParams heroParams=binding.heroFrame.getLayoutParams();
        heroParams.height=(int)(Math.min(280, getResources().getConfiguration().screenWidthDp*0.62f)*density);
        binding.heroFrame.setLayoutParams(heroParams);
        setRole(state==null?getIntent().getStringExtra(EXTRA_ROLE):state.getString(EXTRA_ROLE,"USER"));
        binding.buyerTab.setOnClickListener(v->setRole("USER"));
        binding.merchantTab.setOnClickListener(v->setRole("MERCHANT"));
        binding.loginButton.setOnClickListener(v->login());
        binding.register.setOnClickListener(v->openAuthPage(RegisterActivity.class));
        binding.forgotPassword.setOnClickListener(v->openAuthPage(ForgotPasswordActivity.class));
        binding.guest.setOnClickListener(v->browse());
        if(getIntent().getBooleanExtra("return_to_payment",false)){binding.accountInput.setText(getIntent().getStringExtra("login_account"));binding.merchantTab.setEnabled(false);}
        if(paymentLogin)binding.guest.setVisibility(android.view.View.GONE);
        else binding.accountInput.setText(getIntent().getStringExtra("registered_account"));
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
        if(!"MERCHANT".equals(newRole))newRole="USER";
        if(getIntent().getBooleanExtra("return_to_payment",false))newRole="USER";
        if(!newRole.equals(role)){
            binding.accountInput.setText("");
            binding.passwordInput.setText("");
        }
        role=newRole;
        boolean merchant="MERCHANT".equals(role);
        binding.forestHero.setImageResource(merchant?R.drawable.login_forest_merchant:R.drawable.login_forest_user);
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
    }
    private void setLoginBusy(boolean busy) {
        binding.loginButton.setEnabled(!busy);
        binding.loginButton.setText(busy?"正在登录…":"登录");
        binding.buyerTab.setEnabled(!busy);
        binding.merchantTab.setEnabled(!busy&&!getIntent().getBooleanExtra("return_to_payment",false));
        binding.register.setEnabled(!busy);
        binding.forgotPassword.setEnabled(!busy);
        binding.guest.setEnabled(!busy);
    }
    private void browse(){
        setLoginBusy(true);
        new SessionStore(this).clear();
        com.huimeng.bookshop.core.local.LocalRepository.prepare(this,()->{
            if(!isFinishing()&&!isDestroyed())openMain();
        },error->loginError("书城暂时无法打开，请重试"));
    }
    private void loginError(String text){
        if(isFinishing()||isDestroyed())return;
        setLoginBusy(false);
        if("账号或密码错误".equals(text))AuthForm.error(binding.passwordInput,text);
        else android.widget.Toast.makeText(this,text,android.widget.Toast.LENGTH_SHORT).show();
    }
    private void login(){
        String account=String.valueOf(binding.accountInput.getText()).trim();
        String password=String.valueOf(binding.passwordInput.getText());
        if(account.isEmpty()||password.length()<8){
            if(account.isEmpty())AuthForm.missing(binding.accountInput,"请输入账号");
            if(password.isEmpty())AuthForm.missing(binding.passwordInput,"请输入密码");
            else if(password.length()<8)AuthForm.mark(binding.passwordInput,"密码至少 8 位");
            (account.isEmpty()?binding.accountInput:binding.passwordInput).requestFocus();
            return;
        }
        final String requestedRole=role;
        final boolean remember=binding.rememberLogin.isChecked();
        setLoginBusy(true);
        com.huimeng.bookshop.core.local.LocalRepository.IO.execute(()->{
            boolean pending;
            try{pending=new com.huimeng.bookshop.core.OfflineAccounts(this).isPendingAccount(com.huimeng.bookshop.BuildConfig.API_BASE_URL,account);}
            catch(Exception ignored){pending=false;}
            final boolean local=pending;
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed())return;
                if(local&&!getIntent().getBooleanExtra("return_to_payment",false))offlineLogin(account,password,requestedRole,remember);
                else onlineLogin(account,password,requestedRole,remember);
            });
        });
    }
    private void onlineLogin(String account,String password,String requestedRole,boolean remember){
        try {
            JSONObject body=new JSONObject().put("username",account).put("password",password).put("role",requestedRole);
            new com.huimeng.bookshop.core.sync.SyncTransport(this).json(1,"auth/login",body,result->{
                if(isFinishing()||isDestroyed())return;
                JSONObject user=result.optJSONObject("user");
                boolean canMerchant=false;
                if(user!=null&&user.optJSONArray("roles")!=null)
                    for(int i=0;i<user.optJSONArray("roles").length();i++)
                        if("MERCHANT".equals(user.optJSONArray("roles").optString(i)))canMerchant=true;
                if(user==null||user.optString("id").isEmpty()||result.optString("token").isEmpty()){loginError("登录未完成，请重试");return;}
                String required=getIntent().getStringExtra("required_account_id");if(getIntent().getBooleanExtra("return_to_payment",false)&&required!=null&&!required.isEmpty()&&!required.equals(user.optString("id"))){loginError("请登录本订单所属账号");return;}
                associate(result,user,requestedRole,canMerchant,remember,account,password,false);
            },error->{
                if(isFinishing()||isDestroyed())return;
                if(error.status==0||error.status==404||error.status>=500){
                    if(getIntent().getBooleanExtra("return_to_payment",false))loginError("付款需要连接商城服务验证账号，请检查网络后重试");
                    else offlineLogin(account,password,requestedRole,remember);
                }
                else if(error.status==401){com.huimeng.bookshop.core.local.LocalRepository.IO.execute(()->{
                    try{new com.huimeng.bookshop.core.OfflineAccounts(this).forgetAlias(com.huimeng.bookshop.BuildConfig.API_BASE_URL,account);}catch(Exception ignored){}
                    runOnUiThread(()->loginError("账号或密码错误"));
                });}else loginError(error.status==403?"此账号没有所选角色":error.status==429?"尝试次数过多，请稍后再试":"登录未完成，请重试");
            });
        }catch(Exception error){loginError("登录未完成，请重试");}
    }
    private void offlineLogin(String account,String password,String requestedRole,boolean remember){
        com.huimeng.bookshop.core.local.LocalRepository.IO.execute(()->{try{
            JSONObject result=new com.huimeng.bookshop.core.OfflineAccounts(this).login(com.huimeng.bookshop.BuildConfig.API_BASE_URL,account,password,requestedRole);
            if(result==null){runOnUiThread(()->{if(isFinishing()||isDestroyed())return;setLoginBusy(false);AuthForm.error(binding.accountInput,"本机尚无此账号");});return;}
            JSONObject user=result.getJSONObject("user");String required=getIntent().getStringExtra("required_account_id");
            if(required!=null&&!required.isEmpty()&&!required.equals(user.optString("id")))throw new IllegalArgumentException("请登录本订单所属的原账号");
            org.json.JSONArray roles=user.optJSONArray("roles");boolean merchant=false;if(roles!=null)for(int i=0;i<roles.length();i++)if("MERCHANT".equals(roles.optString(i)))merchant=true;
            final boolean allowed=merchant;runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())associate(result,user,requestedRole,allowed,remember,account,password,true);});
        }catch(Exception e){runOnUiThread(()->loginError(e instanceof IllegalArgumentException?e.getMessage():"登录未完成，请重试"));}});
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
        }catch(Exception e){runOnUiThread(()->loginError("登录未完成，请重试"));}});
    }
}
