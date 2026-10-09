package com.huimeng.bookshop.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.huimeng.bookshop.core.ApiClient;
import com.huimeng.bookshop.databinding.ActivityOrdersBinding;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class OrdersActivity extends AppCompatActivity{
    private ActivityOrdersBinding binding;private ActionAdapter adapter;
    private final Handler handler=new Handler(Looper.getMainLooper());private boolean loading,initialized;
    private final Runnable refresh=new Runnable(){@Override public void run(){load(false);handler.postDelayed(this,5000);}};
    @Override protected void onCreate(Bundle state){super.onCreate(state);binding=ActivityOrdersBinding.inflate(getLayoutInflater());setContentView(binding.getRoot());
        binding.back.setOnClickListener(v->finish());adapter=new ActionAdapter(this::action,this::detail);
        binding.list.setLayoutManager(new LinearLayoutManager(this));binding.list.setAdapter(adapter);}
    @Override protected void onResume(){super.onResume();handler.removeCallbacks(refresh);load(!initialized);handler.postDelayed(refresh,5000);}
    @Override protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
    private void detail(ActionAdapter.Row row){startActivity(new Intent(this,OrderDetailActivity.class).putExtra("order_id",row.id()));}
    private void action(ActionAdapter.Row row){if("去付款".equals(row.action())){
        new ApiClient(this).get("me/orders/"+row.id(),result->{if(isFinishing()||isDestroyed())return;
            JSONObject data=result.optJSONObject("data");
            if(data==null||data.isNull("checkout_group_id")||data.optString("checkout_group_id").isEmpty()){
                showError("结算单号暂不可用，请稍后重试");return;
            }
            startActivity(new Intent(this,PaymentActivity.class).putExtra("group_id",data.optString("checkout_group_id"))
                .putExtra("amount_cents",data.optInt("total_cents")));},this::showError);
        }else detail(row);}
    private void load(boolean showLoading){if(loading)return;loading=true;if(showLoading)binding.loading.setVisibility(View.VISIBLE);
        String filter=getIntent().getStringExtra("status");new ApiClient(this).get("me/orders",result->{loading=false;if(isFinishing()||isDestroyed())return;initialized=true;binding.loading.setVisibility(View.GONE);
            List<ActionAdapter.Row> rows=new ArrayList<>();JSONArray data=result.optJSONArray("data");
            if(data!=null)for(int i=0;i<data.length();i++){JSONObject item=data.optJSONObject(i);
                if(item==null||!matches(filter,item))continue;
                String logistics=item.optString("logistics_status",item.optString("status"));
                String name=item.optString("productName","绘本订单");
                if(name.isEmpty())name="绘本订单";
                String action="PENDING_PAYMENT".equals(item.optString("status"))?"去付款":"COMPLETED".equals(logistics)?"评价":"查看";
                String subtitle=item.optString("shop")+" · "+zh(logistics)+" · 实付 "+String.format(Locale.CHINA,"¥%.2f",item.optInt("total_cents")/100.0);
                if(item.optInt("itemCount")>1)subtitle+=" · 共 "+item.optInt("itemCount")+" 件";
                String date=item.optString("created_at");if(date.length()>=16)subtitle+="\n下单 "+date.substring(0,16).replace('T',' ');
                rows.add(new ActionAdapter.Row(item.optString("id"),name,subtitle,action,true,item.optString("coverUrl"),name));
            }adapter.submit(rows);binding.empty.setText("暂无相关订单");binding.empty.setVisibility(rows.isEmpty()?View.VISIBLE:View.GONE);
        },error->{loading=false;if(isFinishing()||isDestroyed())return;binding.loading.setVisibility(View.GONE);showError(error);});}
    private void showError(String value){if(isFinishing()||isDestroyed())return;binding.empty.setText(value);binding.empty.setVisibility(View.VISIBLE);}
    private boolean matches(String filter,JSONObject item){if(filter==null||filter.isEmpty())return true;
        String status=item.optString("status");
        if("PAID".equals(filter))return "PAID".equals(status);
        if("SHIPPED".equals(filter))return "SHIPPED".equals(status);
        return filter.equals(status);
    }
    private String zh(String value){return switch(value){case "PENDING_PAYMENT","WAITING_PAYMENT"->"待付款";case "PAID","PREPARING"->"准备出库";case "READY_TO_SHIP"->"待发货";case "SHIPPED"->"已发货";case "IN_TRANSIT"->"运输中";case "OUT_FOR_DELIVERY"->"派送中";case "COMPLETED"->"已完成";case "CANCELLED"->"已取消";default->value;};}
}
