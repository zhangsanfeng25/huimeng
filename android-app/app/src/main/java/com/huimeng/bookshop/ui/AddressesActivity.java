package com.huimeng.bookshop.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.huimeng.bookshop.core.ApiClient;
import com.huimeng.bookshop.databinding.ActivityAddressesBinding;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public final class AddressesActivity extends AppCompatActivity{
    private ActivityAddressesBinding binding;private AddressAdapter adapter;
    @Override protected void onCreate(Bundle state){super.onCreate(state);binding=ActivityAddressesBinding.inflate(getLayoutInflater());setContentView(binding.getRoot());
        binding.back.setOnClickListener(v->finish());binding.add.setOnClickListener(v->startActivity(new Intent(this,AddAddressActivity.class)));
        adapter=new AddressAdapter(this::edit,this::options);binding.list.setLayoutManager(new LinearLayoutManager(this));binding.list.setAdapter(adapter);
    }
    @Override protected void onResume(){super.onResume();load();}
    private void load(){binding.loading.setVisibility(View.VISIBLE);binding.empty.setVisibility(View.GONE);
        new ApiClient(this).get("me/addresses",result->{if(isFinishing()||isDestroyed())return;binding.loading.setVisibility(View.GONE);
            List<JSONObject> rows=new ArrayList<>();JSONArray data=result.optJSONArray("data");if(data!=null)for(int i=0;i<data.length();i++){JSONObject row=data.optJSONObject(i);if(row!=null)rows.add(row);}
            adapter.submit(rows);binding.empty.setText("还没有收货地址\n点击右下角＋添加");binding.empty.setVisibility(rows.isEmpty()?View.VISIBLE:View.GONE);
        },error->{if(isFinishing()||isDestroyed())return;binding.loading.setVisibility(View.GONE);binding.empty.setText(error+"\n请返回后重试");binding.empty.setVisibility(View.VISIBLE);});
    }
    private void edit(JSONObject row){startActivity(new Intent(this,AddAddressActivity.class).putExtra("copy_address",row.toString()));}
    private void options(JSONObject row){boolean choosing=getIntent().getBooleanExtra("select_address",false);String[] actions=choosing?new String[]{"使用此地址","编辑地址","设为默认","删除地址"}:new String[]{"编辑地址","设为默认","删除地址"};new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle(row.optString("recipient")+"的收货地址").setItems(actions,(dialog,which)->{int action=which-(choosing?1:0);if(action==-1){setResult(RESULT_OK,new Intent().putExtra("selected_address",row.toString()));finish();}else if(action==0)edit(row);else if(action==1)setDefault(row);else delete(row);}).show();}
    private void setDefault(JSONObject row){try{JSONObject body=new JSONObject().put("recipient",row.optString("recipient")).put("phone",row.optString("phone")).put("region",row.optString("region")).put("detail",row.optString("detail")).put("isDefault",true);new ApiClient(this).put("me/addresses/"+row.optString("id"),body,result->load(),this::error);}catch(Exception ignored){error("地址更新失败");}}
    private void delete(JSONObject row){new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setMessage("删除这条收货地址？已有订单的收货信息会保留。").setNegativeButton("保留",null).setPositiveButton("删除",(dialog,which)->new ApiClient(this).delete("me/addresses/"+row.optString("id"),result->load(),this::error)).show();}
    private void error(String message){android.widget.Toast.makeText(this,message,android.widget.Toast.LENGTH_SHORT).show();}
}
