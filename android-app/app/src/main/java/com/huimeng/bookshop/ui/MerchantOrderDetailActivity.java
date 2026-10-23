package com.huimeng.bookshop.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import com.huimeng.bookshop.core.ApiClient;
import com.huimeng.bookshop.databinding.ActivityOrderDetailBinding;
import com.huimeng.bookshop.databinding.ItemOrderProductBinding;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Locale;

public class MerchantOrderDetailActivity extends AppCompatActivity {
    private ActivityOrderDetailBinding binding;private String orderId,logistics="";private boolean loading;private com.google.android.material.button.MaterialButton serviceSale;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private String displayed="";
    private final Runnable refresh=new Runnable(){@Override public void run(){load(false);handler.postDelayed(this,5000);}};
    @Override protected void onCreate(Bundle state){super.onCreate(state);binding=ActivityOrderDetailBinding.inflate(getLayoutInflater());setContentView(binding.getRoot());
        orderId=getIntent().getStringExtra("order_id");binding.back.setOnClickListener(v->finish());binding.action.setOnClickListener(v->advance());
        serviceSale=ServiceEntries.orderActions(binding.timeline,"联系买家",()->ServiceActivity.contact(this,null,orderId,null),"查看售后",()->ServiceActivity.sale(this,orderId))[1];}
    @Override protected void onResume(){super.onResume();handler.removeCallbacks(refresh);load(displayed.isEmpty());handler.postDelayed(refresh,5000);}
    @Override protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
    private void load(boolean showLoading){if(loading)return;loading=true;if(showLoading)binding.loading.setVisibility(View.VISIBLE);
        new ApiClient(this).get("merchant/orders/"+orderId,result->{loading=false;binding.loading.setVisibility(View.GONE);
            JSONObject data=result.optJSONObject("data");if(data==null||isFinishing()||isDestroyed())return;String next=data.toString();if(next.equals(displayed))return;displayed=next;
            logistics=data.optString("logistics_status",data.optString("status"));
            binding.status.setText(zh(logistics)+(data.has("pendingAction")?" · 本地操作待联网确认":""));binding.orderId.setText("订单号："+data.optString("id"));
            binding.shop.setText("买家："+data.optString("buyer"));
            binding.total.setText(String.format(Locale.CHINA,"¥%.2f",data.optInt("total_cents")/100.0));
            JSONObject address=data.optJSONObject("address_snapshot_json");if(address==null){try{address=new JSONObject(data.optString("address_snapshot_json"));}catch(Exception ignored){}}
            if(address!=null&&!address.optString("recipient").isEmpty()){binding.address.setText("收货信息  "+address.optString("recipient")+"  "+address.optString("phone")+"\n"+address.optString("region")+address.optString("detail"));binding.address.setVisibility(View.VISIBLE);}else binding.address.setVisibility(View.GONE);
            binding.itemList.removeAllViews();JSONArray items=data.optJSONArray("items");
            if(items!=null)for(int i=0;i<items.length();i++){JSONObject item=items.optJSONObject(i);if(item==null)continue;
                ItemOrderProductBinding line=ItemOrderProductBinding.inflate(LayoutInflater.from(this),binding.itemList,false);
                String name=item.optString("productName","绘本");line.name.setText(name);
                line.quantity.setText("单价 "+String.format(Locale.CHINA,"¥%.2f",item.optInt("unitPriceCents")/100.0)+"  ·  数量 × "+item.optInt("quantity",1)+(item.optInt("cancelledQuantity")>0?"\n已取消 "+item.optInt("cancelledQuantity")+" 件 · 剩余履约 "+item.optInt("fulfillableQuantity")+" 件":""));
                line.price.setText(String.format(Locale.CHINA,"¥%.2f",item.optInt("subtotalCents")/100.0));
                Glide.with(line.cover).load(ApiClient.assetUrl(item.optString("coverUrl")))
                    .placeholder(new BookCoverDrawable(name)).error(new BookCoverDrawable(name)).centerCrop().into(line.cover);
                binding.itemList.addView(line.getRoot());}
            binding.timeline.setText(OrderProgress.format(data));
            String action=action(logistics);binding.action.setVisibility(action.isEmpty()?View.GONE:View.VISIBLE);
            binding.action.setText(action);
            String afterSale=data.isNull("after_sale_state")?"":data.optString("after_sale_state");serviceSale.setVisibility(afterSale.isEmpty()?View.GONE:View.VISIBLE);if(!afterSale.isEmpty()){if("COMPLETED".equals(afterSale))binding.status.append("\n"+AfterSaleState.refundLabel(data)+" · 售后完成");else if(!java.util.Arrays.asList("REJECTED","CANCELLED").contains(afterSale))binding.status.setText("售后处理中\n"+ServiceActivity.label(afterSale));else binding.status.append("\n售后："+ServiceActivity.label(afterSale));if(AfterSaleState.blocked(data))binding.action.setVisibility(View.GONE);}
        },this::error);
    }
    private void advance(){new ApiClient(this).post("merchant/orders/"+orderId+"/advance",new JSONObject(),result->{
        Toast.makeText(this,result.optBoolean("pending")?"订单操作已保存在本地，待联网确认":"订单已更新为："+zh(result.optString("toStatus")),Toast.LENGTH_SHORT).show();load(false);
    },this::error);}
    private String action(String value){return switch(value){case "PREPARING"->"完成出库准备";case "READY_TO_SHIP"->"确认发货";case "SHIPPED"->"更新为运输中";case "IN_TRANSIT"->"更新为派送中";default->"";};}
    private String zh(String value){return switch(value){case "PENDING_PAYMENT","WAITING_PAYMENT"->"待付款";case "PAID","PREPARING"->"准备出库";case "READY_TO_SHIP"->"待发货";case "SHIPPED"->"已发货";case "IN_TRANSIT"->"运输中";case "OUT_FOR_DELIVERY"->"派送中";case "COMPLETED"->"已完成";case "CANCELLED"->"已取消";default->value;};}
    private void error(String message){loading=false;binding.loading.setVisibility(View.GONE);if(!displayed.isEmpty())return;Toast.makeText(this,message,Toast.LENGTH_SHORT).show();}
}
