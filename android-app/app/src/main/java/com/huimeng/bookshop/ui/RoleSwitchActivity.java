package com.huimeng.bookshop.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.huimeng.bookshop.core.SessionStore;
import com.huimeng.bookshop.core.ApiClient;
import com.huimeng.bookshop.core.AccountSync;
import org.json.JSONObject;
import com.huimeng.bookshop.databinding.ActivityRoleSwitchBinding;

public class RoleSwitchActivity extends AppCompatActivity{
    private ActivityRoleSwitchBinding binding;private SessionStore session;private AccountSync sync;private boolean reading,switching,verified,queuedRefresh;
    @Override protected void onCreate(Bundle state){super.onCreate(state);binding=ActivityRoleSwitchBinding.inflate(getLayoutInflater());setContentView(binding.getRoot());session=new SessionStore(this);binding.back.setOnClickListener(v->finish());binding.buyerCard.setOnClickListener(v->switchTo("USER"));binding.merchantCard.setOnClickListener(v->{if(verified&&session.canMerchant())switchTo("MERCHANT");else Toast.makeText(this,"请先提交商家申请并等待审核通过",Toast.LENGTH_SHORT).show();});binding.applyMerchant.setOnClickListener(v->startActivity(new Intent(this,MerchantApplicationActivity.class)));sync=new AccountSync(this,this::refresh);binding.merchantCard.setAlpha(.55f);binding.merchantHint.setText("正在核对商家权限…");}
    private void afterRead(){reading=false;if(queuedRefresh){queuedRefresh=false;if(getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))binding.getRoot().post(this::refresh);}}
    @Override protected void onResume(){super.onResume();sync.start();}
    @Override protected void onPause(){sync.stop();super.onPause();}
    private void refresh(){if(reading||switching){queuedRefresh=true;return;}reading=true;new ApiClient(this).get("auth/me",result->{if(isFinishing()||isDestroyed())return;afterRead();verified=true;JSONObject user=result.optJSONObject("user");if(user!=null)session.updateAccess(user);binding.merchantCard.setAlpha(session.canMerchant()?1f:.55f);binding.merchantHint.setText(session.canMerchant()?"商家权限已开通 · 点击立即切换":"尚未开通商家权限，请在下方申请");new ApiClient(this).get("me/merchant-application",r->{if(isFinishing()||isDestroyed())return;JSONObject data=r.optJSONObject("data");String status=data==null?"NONE":data.optString("status");binding.applyMerchant.setText("PENDING".equals(status)?"查看申请进度":"REJECTED".equals(status)?"修改申请并重新提交":"APPROVED".equals(status)?"查看商家申请":"申请成为商家");binding.applicationHint.setText("PENDING".equals(status)?"申请已提交，审核结果将自动同步":"REJECTED".equals(status)?"驳回原因："+data.optString("rejectionReason"):session.canMerchant()?"申请已通过，可以直接点击商家端卡片":"提交真实店铺资料，审核通过后即可开通");},error->{if(!isFinishing()&&!isDestroyed())binding.applicationHint.setText(error);});},error->{if(isFinishing()||isDestroyed())return;afterRead();verified=false;binding.merchantCard.setAlpha(.55f);binding.merchantHint.setText("权限读取失败，将自动重试");binding.applicationHint.setText(error);});}
    private void switchTo(String role){if(switching)return;switching=true;try{new ApiClient(this).post("auth/switch-role",new JSONObject().put("role",role),result->{if(isFinishing()||isDestroyed())return;session.updateAccess(result);session.setRole(role);startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK|Intent.FLAG_ACTIVITY_NEW_TASK));finish();},error->{switching=false;Toast.makeText(this,error,Toast.LENGTH_SHORT).show();refresh();});}catch(Exception ignored){switching=false;}}
}
