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
        binding.forgotPassword.setText("账号帮助");
        binding.forgotPassword.setOnClickListener(v->new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle("账号帮助").setMessage("忘记密码时，请联系系统管理员协助处理。自助找回将在接入身份验证后开放；已有账号可登录后在设置中修改密码。").setPositiveButton("知道了",null).show());
        String notice=getIntent().getStringExtra("login_notice");if(notice!=null)binding.message.setText(notice);
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
        binding.merchantTab.setEnabled(!busy);
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
        binding.message.setText("正在连接本地服务…");
        try {
            JSONObject body=new JSONObject().put("username",account).put("password",password).put("role",requestedRole);
            new ApiClient(this).post("auth/login",body,result->{
                if(isFinishing()||isDestroyed())return;
                JSONObject user=result.optJSONObject("user");
                boolean canMerchant=false;
                if(user!=null&&user.optJSONArray("roles")!=null)
                    for(int i=0;i<user.optJSONArray("roles").length();i++)
                        if("MERCHANT".equals(user.optJSONArray("roles").optString(i)))canMerchant=true;
                if(user==null||user.optString("id").isEmpty()||result.optString("token").isEmpty()){setLoginBusy(false);binding.message.setText("网络账号验证结果不完整");return;}
                final boolean merchantAllowed=canMerchant;
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle("关联网络账号")
                    .setMessage("已验证账号："+user.optString("username",account)+"\n确认后，本地空间将关联此账号并同步待上传内容。切换其他账号会使用独立空间。")
                    .setNegativeButton("取消",(dialog,which)->setLoginBusy(false))
                    .setOnCancelListener(dialog->setLoginBusy(false))
                    .setPositiveButton("确认关联",(dialog,which)->associate(result,user,requestedRole,merchantAllowed,remember)).show();
            },error->{
                if(isFinishing()||isDestroyed())return;
                setLoginBusy(false);binding.message.setText(error);
            });
        }catch(Exception error){setLoginBusy(false);binding.message.setText("登录参数错误");}
    }
    private void openMain(){startActivity(new Intent(this,MainActivity.class));finish();}
    private void associate(JSONObject result,JSONObject user,String requestedRole,boolean canMerchant,boolean remember){
        com.huimeng.bookshop.core.local.LocalRepository.IO.execute(()->{try{
            SessionStore session=new SessionStore(this);com.huimeng.bookshop.core.local.LocalDatabase db=com.huimeng.bookshop.core.local.LocalDatabase.get(this);com.huimeng.bookshop.core.local.LocalSpaceStore spaces=new com.huimeng.bookshop.core.local.LocalSpaceStore(db);
            String space=spaces.ensureActiveSpace();JSONObject info=spaces.info(space);String origin=com.huimeng.bookshop.BuildConfig.API_BASE_URL,owner=info.optString("userId");
            if(!owner.isEmpty()&&(!owner.equals(user.optString("id"))||!origin.equals(info.optString("origin"))))space=spaces.forAccount(origin,user.optString("id"));
            else space=spaces.bind(space,origin,user.optString("id"));
            spaces.select(space);spaces.role(space,requestedRole);com.huimeng.bookshop.core.local.LocalBootstrap.prepare(this,db,space);db.saveConfirmed(space,"profile","self",user,user.optLong("version"));
            final String selectedSpace=space;
            runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()){com.huimeng.bookshop.core.sync.SyncCoordinator.get(this).stop();session.save(result.optString("token"),requestedRole,user.optString("name"),canMerchant,user.optString("id"),user.optString("username"),remember);session.localSpace(selectedSpace);session.setAvatarUrl(user.optString("avatarUrl"));ApiClient.resetSessionRedirect();openMain();}});
        }catch(Exception e){runOnUiThread(()->{setLoginBusy(false);binding.message.setText("关联未完成，本地数据已保留："+e.getMessage());});}});
    }
}
