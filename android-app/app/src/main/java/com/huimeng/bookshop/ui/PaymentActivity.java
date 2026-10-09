package com.huimeng.bookshop.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.huimeng.bookshop.core.ApiClient;
import com.huimeng.bookshop.core.LocalCartStore;
import com.huimeng.bookshop.databinding.ActivityPaymentBinding;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class PaymentActivity extends AppCompatActivity {
    private ActivityPaymentBinding binding;
    private String groupId;
    private boolean canPay,paying;
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);binding=ActivityPaymentBinding.inflate(getLayoutInflater());setContentView(binding.getRoot());
        groupId=getIntent().getStringExtra("group_id");
        if(groupId==null||groupId.isEmpty()){finish();return;}
        int cents=getIntent().getIntExtra("amount_cents",0);
        binding.amount.setText(String.format(Locale.CHINA,"¥%.2f",cents/100.0));
        binding.order.setText("结算单号  "+groupId);
        binding.back.setOnClickListener(v->finish());
        binding.confirm.setOnClickListener(v->pay());
        binding.confirm.setEnabled(false);
        new ApiClient(this).get("checkout/"+groupId,result->{
            JSONObject data=result.optJSONObject("data");if(data==null){error("结算单不存在");return;}
            binding.amount.setText(String.format(Locale.CHINA,"¥%.2f",data.optInt("totalCents")/100.0));
            if("PENDING".equals(data.optString("status"))){canPay=true;binding.confirm.setEnabled(true);}
            else if("PAID".equals(data.optString("status"))){canPay=false;binding.confirm.setText("已完成模拟付款");}
            else {canPay=false;binding.confirm.setText("订单已关闭");}
        },this::error);
    }
    private void pay(){
        if(!canPay||paying)return;paying=true;binding.confirm.setEnabled(false);binding.loading.setVisibility(View.VISIBLE);
        String method=binding.methods.getCheckedRadioButtonId()==binding.alipay.getId()?"DEMO_ALIPAY":binding.methods.getCheckedRadioButtonId()==binding.wechat.getId()?"DEMO_WECHAT":"DEMO_BALANCE";
        Map<String,String> headers=new HashMap<>();headers.put("Idempotency-Key","pay-"+groupId);
        try{
            JSONObject body=new JSONObject().put("method",method);
            new ApiClient(this).post("checkout/"+groupId+"/pay",body,headers,result->verifyPayment(),message->{
                new ApiClient(this).get("checkout/"+groupId,status->{JSONObject data=status.optJSONObject("data");
                    if(data!=null&&"PAID".equals(data.optString("status")))showSuccess();else error(message);
                },ignored->error(message));
            });
        }catch(Exception e){error("付款参数错误");}
    }
    private void verifyPayment(){new ApiClient(this).get("checkout/"+groupId,result->{JSONObject data=result.optJSONObject("data");
        if(data!=null&&"PAID".equals(data.optString("status")))showSuccess();else error("正在确认付款状态，请返回订单页稍后查看");
    },ignored->error("付款结果暂无法确认，请返回订单页查询，勿重复付款"));}
    private void showSuccess(){binding.loading.setVisibility(View.GONE);LocalCartStore.resolve(this,cart->{},ignored->{});startActivity(new Intent(this,PaymentSuccessActivity.class).putExtra("group_id",groupId));finish();}
    private void error(String message){paying=false;binding.loading.setVisibility(View.GONE);binding.confirm.setEnabled(canPay);Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
}
