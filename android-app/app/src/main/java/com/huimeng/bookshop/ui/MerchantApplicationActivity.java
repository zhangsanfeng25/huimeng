package com.huimeng.bookshop.ui;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.huimeng.bookshop.core.ApiClient;
import com.huimeng.bookshop.core.AccountSync;
import com.huimeng.bookshop.core.SessionStore;
import com.huimeng.bookshop.databinding.ActivityMerchantApplicationBinding;
import org.json.JSONObject;

public class MerchantApplicationActivity extends AppCompatActivity {
    private ActivityMerchantApplicationBinding binding;private AccountSync sync;private boolean reading,saving,prefilled,loaded,queuedRefresh;private String status="NONE",businessStatus="";
    @Override protected void onCreate(Bundle state){super.onCreate(state);binding=ActivityMerchantApplicationBinding.inflate(getLayoutInflater());setContentView(binding.getRoot());prefilled=state!=null&&state.getBoolean("prefilled");binding.back.setOnClickListener(v->finish());binding.retry.setOnClickListener(v->load());binding.submit.setOnClickListener(v->submit());sync=new AccountSync(this,this::load);}
    private void afterRead(){reading=false;if(queuedRefresh){queuedRefresh=false;if(getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))binding.getRoot().post(this::load);}}
    @Override protected void onResume(){super.onResume();sync.start();}
    @Override protected void onPause(){sync.stop();super.onPause();}
    @Override protected void onSaveInstanceState(Bundle state){state.putBoolean("prefilled",prefilled);super.onSaveInstanceState(state);}
    private boolean alive(){return !isFinishing()&&!isDestroyed();}
    private void load(){if(reading||saving){queuedRefresh=true;return;}reading=true;new ApiClient(this).get("me/merchant-application",result->{if(!alive())return;afterRead();loaded=true;binding.loading.setVisibility(View.GONE);binding.error.setVisibility(View.GONE);binding.retry.setVisibility(View.GONE);JSONObject data=result.optJSONObject("data");status=data==null?"NONE":data.optString("status");businessStatus=data==null?"":data.optString("businessStatus");
        if(data!=null&&!prefilled){binding.shopName.setText(data.optString("shopName"));binding.contactName.setText(data.optString("contactName"));binding.contactPhone.setText(data.optString("contactPhone"));binding.businessAddress.setText(data.optString("businessAddress"));binding.description.setText(data.optString("description"));binding.terms.setChecked(true);prefilled=true;}
        boolean editable="NONE".equals(status)||"LOCAL_DRAFT".equals(status)||"REJECTED".equals(status);for(View v:new View[]{binding.shopName,binding.contactName,binding.contactPhone,binding.businessAddress,binding.description,binding.terms})v.setEnabled(editable);
        binding.submit.setEnabled(editable||("APPROVED".equals(status)&&"OPEN".equals(businessStatus)));
        if("LOCAL_DRAFT".equals(status)){binding.status.setText("申请已保存在本地 · 待联网提交");binding.statusNote.setText("资料可继续修改，关联网络账号后上传，由平台审核。");binding.submit.setText("保存本地修改");}
        else if("PENDING".equals(status)){binding.status.setText("申请已提交 · 待审核");binding.statusNote.setText("管理员正在审核资料。此页面会自动同步审核结果，通过后无需重新登录。");binding.submit.setText("等待平台审核");}
        else if("APPROVED".equals(status)){boolean open="OPEN".equals(businessStatus);binding.status.setText(open?"审核通过 · 欢迎入驻":"申请已通过 · 店铺已停用");binding.statusNote.setText(open?"商家身份已开通，可直接进入商品管理、订单和经营工作台。":"请联系平台管理员确认店铺经营状态。");binding.submit.setText(open?"切换到商家端":"店铺暂不可用");}
        else if("REJECTED".equals(status)){binding.status.setText("申请未通过 · 可修改后重提");binding.statusNote.setText("驳回原因："+data.optString("rejectionReason","请完善申请资料"));binding.submit.setText("修改并重新提交");}
        else{binding.status.setText("让好故事遇见更多家庭");binding.statusNote.setText("填写真实店铺资料，由平台审核。通过后可直接切换商家身份，管理绘本与订单。");binding.submit.setText("提交商家申请");}
    },error->{if(!alive())return;afterRead();binding.loading.setVisibility(View.GONE);binding.error.setText(error);binding.error.setVisibility(View.VISIBLE);binding.retry.setVisibility(View.VISIBLE);if(!loaded)binding.submit.setEnabled(false);});}
    private String text(TextView view){return String.valueOf(view.getText()).trim();}
    private void submit(){if(saving||!loaded)return;if("APPROVED".equals(status)){saving=true;binding.submit.setEnabled(false);try{new ApiClient(this).post("auth/switch-role",new JSONObject().put("role","MERCHANT"),result->{if(!alive())return;new SessionStore(this).updateAccess(result);new SessionStore(this).setRole("MERCHANT");startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));finish();},this::failure);}catch(Exception error){failure("切换失败，请重试");}return;}
        String shop=text(binding.shopName),contact=text(binding.contactName),phone=text(binding.contactPhone),address=text(binding.businessAddress),description=text(binding.description);
        if(shop.length()<2){binding.shopName.setError("店铺名称至少 2 字");return;}if(contact.length()<2){binding.contactName.setError("请填写联系人姓名");return;}if(!phone.matches("\\+?[0-9][0-9 -]{5,19}")){binding.contactPhone.setError("请填写有效联系电话");return;}if(address.length()<5){binding.businessAddress.setError("请填写详细经营地址");return;}if(description.length()<10){binding.description.setError("店铺介绍至少 10 字");return;}if(!binding.terms.isChecked()){failure("请确认资料真实并同意管理规则");return;}
        try{JSONObject body=new JSONObject().put("shopName",shop).put("contactName",contact).put("contactPhone",phone).put("businessAddress",address).put("description",description).put("acceptedTerms",true);saving=true;binding.submit.setEnabled(false);binding.submit.setText("正在提交…");new ApiClient(this).post("me/merchant-application",body,result->{if(!alive())return;saving=false;load();},this::failure);}catch(Exception error){failure("申请信息不完整，请重试");}
    }
    private void failure(String error){if(!alive())return;saving=false;binding.submit.setEnabled(true);binding.submit.setText("APPROVED".equals(status)?"切换到商家端":"REJECTED".equals(status)?"修改并重新提交":"提交商家申请");binding.error.setText(error);binding.error.setVisibility(View.VISIBLE);}
}
