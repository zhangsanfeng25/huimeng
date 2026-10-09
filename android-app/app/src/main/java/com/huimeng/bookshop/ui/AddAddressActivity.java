package com.huimeng.bookshop.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.huimeng.bookshop.core.ApiClient;
import com.huimeng.bookshop.databinding.ActivityAddAddressBinding;
import org.json.JSONObject;

public class AddAddressActivity extends AppCompatActivity{
    private ActivityAddAddressBinding binding;private String addressId="";
    @Override protected void onCreate(Bundle state){super.onCreate(state);binding=ActivityAddAddressBinding.inflate(getLayoutInflater());setContentView(binding.getRoot());binding.back.setOnClickListener(v->finish());binding.save.setOnClickListener(v->save());
        String source=getIntent().getStringExtra("copy_address");if(source!=null){try{JSONObject address=new JSONObject(source);addressId=address.optString("id");binding.title.setText("编辑收货地址");binding.note.setVisibility(View.GONE);binding.save.setText("保存修改");
            binding.recipient.setText(address.optString("recipient"));binding.phone.setText(address.optString("phone"));binding.region.setText(address.optString("region"));binding.detail.setText(address.optString("detail"));binding.defaultAddress.setChecked(address.optInt("is_default")==1);
        }catch(Exception ignored){}}
    }
    private void save(){String recipient=text(binding.recipient),phone=text(binding.phone),region=text(binding.region),detail=text(binding.detail);if(recipient.isEmpty()||phone.length()<6||region.length()<2||detail.length()<2){Toast.makeText(this,"请完整填写收货信息",Toast.LENGTH_SHORT).show();return;}binding.save.setEnabled(false);try{JSONObject body=new JSONObject().put("recipient",recipient).put("phone",phone).put("region",region).put("detail",detail).put("isDefault",binding.defaultAddress.isChecked());java.util.function.Consumer<JSONObject> success=result->{Toast.makeText(this,"地址已保存",Toast.LENGTH_SHORT).show();setResult(RESULT_OK,new android.content.Intent().putExtra("saved_address_id",addressId.isEmpty()?result.optString("id"):addressId));finish();};java.util.function.Consumer<String> failure=error->{binding.save.setEnabled(true);Toast.makeText(this,error,Toast.LENGTH_SHORT).show();};if(addressId.isEmpty())new ApiClient(this).post("me/addresses",body,success,failure);else new ApiClient(this).put("me/addresses/"+addressId,body,success,failure);}catch(Exception ignored){binding.save.setEnabled(true);}}
    private String text(android.widget.TextView view){return String.valueOf(view.getText()).trim();}
}
