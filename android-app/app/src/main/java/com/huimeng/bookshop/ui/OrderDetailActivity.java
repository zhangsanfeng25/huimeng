package com.huimeng.bookshop.ui;

import android.content.Intent;
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

public class OrderDetailActivity extends AppCompatActivity{
    private ActivityOrderDetailBinding binding;private com.google.android.material.button.MaterialButton serviceSale;
    private String orderId,itemId="",status="",logistics="",groupId="",returnProductId="";
    private int totalCents;
    private final Handler handler=new Handler(Looper.getMainLooper());private boolean loading;
    private String displayed="";
    private final java.util.List<JSONObject> reviewItems=new java.util.ArrayList<>();
    private final Runnable refresh=new Runnable(){@Override public void run(){load(false);handler.postDelayed(this,5000);}};
    @Override protected void onCreate(Bundle state){super.onCreate(state);binding=ActivityOrderDetailBinding.inflate(getLayoutInflater());setContentView(binding.getRoot());
        orderId=getIntent().getStringExtra("order_id");binding.back.setOnClickListener(v->finish());binding.action.setOnClickListener(v->act());
        serviceSale=ServiceEntries.orderActions(binding.timeline,"联系商家",()->com.huimeng.bookshop.core.ServiceClient.remoteOrder(this,orderId,remote->new com.huimeng.bookshop.core.ServiceClient(this).get("me/orders/"+remote,r->{JSONObject d=r.optJSONObject("data");if(d!=null)ServiceActivity.contact(this,d.optString("merchant_id"),remote,null);},this::error),this::error),"申请售后",()->ServiceActivity.sale(this,orderId))[1];}
    @Override protected void onResume(){super.onResume();handler.removeCallbacks(refresh);load(displayed.isEmpty());handler.postDelayed(refresh,5000);}
    @Override protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
    private void load(boolean showLoading){if(loading)return;loading=true;if(showLoading)binding.loading.setVisibility(View.VISIBLE);
        new ApiClient(this).get("me/orders/"+orderId,result->{loading=false;binding.loading.setVisibility(View.GONE);
            JSONObject data=result.optJSONObject("data");if(data==null||isFinishing()||isDestroyed())return;String next=data.toString();if(next.equals(displayed))return;displayed=next;
            status=data.optString("status");logistics=data.optString("logistics_status",status);
            groupId=data.optString("checkout_group_id");totalCents=data.optInt("total_cents");
            binding.orderId.setText("订单号："+data.optString("id"));binding.shop.setText(data.optString("shop"));
            binding.status.setText(zh(logistics)+(data.has("pendingAction")?" · 本地操作待联网确认":""));binding.total.setText(String.format(Locale.CHINA,"¥%.2f",totalCents/100.0));
            JSONObject address=data.optJSONObject("address_snapshot_json");if(address==null){try{address=new JSONObject(data.optString("address_snapshot_json"));}catch(Exception ignored){}}
            if(address!=null&&!address.optString("recipient").isEmpty()){binding.address.setText("收货信息  "+address.optString("recipient")+"  "+address.optString("phone")+"\n"+address.optString("region")+address.optString("detail"));binding.address.setVisibility(View.VISIBLE);}else binding.address.setVisibility(View.GONE);
            binding.itemList.removeAllViews();itemId="";returnProductId="";reviewItems.clear();
            JSONArray items=data.optJSONArray("items");if(items!=null)for(int i=0;i<items.length();i++){
                JSONObject item=items.optJSONObject(i);if(item==null)continue;
                if(itemId.isEmpty())itemId=item.optString("id");
                if(returnProductId.isEmpty())returnProductId=item.optString("productId",item.optString("product_id"));
                if(item.optInt("rated",0)==0)reviewItems.add(item);
                ItemOrderProductBinding line=ItemOrderProductBinding.inflate(LayoutInflater.from(this),binding.itemList,false);
                String name=item.optString("productName","绘本");line.name.setText(name);
                line.quantity.setText("单价 "+String.format(Locale.CHINA,"¥%.2f",item.optInt("unitPriceCents")/100.0)+"  ·  数量 × "+item.optInt("quantity",1)+(item.optInt("cancelledQuantity")>0?"\n已取消 "+item.optInt("cancelledQuantity")+" 件 · 剩余履约 "+item.optInt("fulfillableQuantity")+" 件":""));
                line.price.setText(String.format(Locale.CHINA,"¥%.2f",item.optInt("subtotalCents")/100.0));
                Glide.with(line.cover).load(ApiClient.assetUrl(item.optString("coverUrl")))
                    .placeholder(new BookCoverDrawable(name)).error(new BookCoverDrawable(name)).centerCrop().into(line.cover);
                binding.itemList.addView(line.getRoot());
            }
            if("PENDING_PAYMENT".equals(status))addCancelButton();
            binding.timeline.setText(OrderProgress.format(data));
            binding.action.setEnabled(true);
            if("PENDING_PAYMENT".equals(status)){binding.action.setText("去付款");binding.action.setVisibility(View.VISIBLE);}
            else if("OUT_FOR_DELIVERY".equals(logistics)){binding.action.setText("确认收货");binding.action.setVisibility(View.VISIBLE);}
            else if("COMPLETED".equals(logistics)){binding.action.setText(reviewItems.isEmpty()?"全部商品已评价":"评价商品");binding.action.setVisibility(View.VISIBLE);binding.action.setEnabled(!reviewItems.isEmpty());}
            else binding.action.setVisibility(View.GONE);
            String afterSale=data.isNull("after_sale_state")?"":data.optString("after_sale_state");serviceSale.setVisibility(!afterSale.isEmpty()||java.util.Arrays.asList("PAID","SHIPPED","COMPLETED").contains(status)?View.VISIBLE:View.GONE);serviceSale.setText(afterSale.isEmpty()?"申请售后":"售后进度");if(!afterSale.isEmpty()){if("COMPLETED".equals(afterSale))binding.status.append("\n"+AfterSaleState.refundLabel(data)+" · 售后完成");else if(!java.util.Arrays.asList("REJECTED","CANCELLED").contains(afterSale))binding.status.setText("售后处理中\n"+ServiceActivity.label(afterSale));else binding.status.append("\n售后："+ServiceActivity.label(afterSale));if(AfterSaleState.blocked(data))binding.action.setVisibility(View.GONE);}
        },this::error);
    }
    private void act(){if("PENDING_PAYMENT".equals(status))startActivity(new Intent(this,PaymentActivity.class)
            .putExtra("group_id",groupId).putExtra("amount_cents",totalCents).putExtra("return_product_id",returnProductId));
        else if("OUT_FOR_DELIVERY".equals(logistics))new ApiClient(this).post("me/orders/"+orderId+"/confirm",new JSONObject(),result->{Toast.makeText(this,result.optBoolean("pending")?"收货意向已保存，待联网确认":"已确认收货",Toast.LENGTH_SHORT).show();load(false);},this::error);
        else if("COMPLETED".equals(logistics)){if(reviewItems.isEmpty())return;String[] names=new String[reviewItems.size()];for(int i=0;i<names.length;i++)names[i]=reviewItems.get(i).optString("productName");new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle("选择要评价的绘本").setItems(names,(dialog,index)->startActivity(new Intent(this,RatingActivity.class).putExtra("order_item_id",reviewItems.get(index).optString("id")))).show();}}
    private void addCancelButton(){
        com.google.android.material.button.MaterialButton cancel=new com.google.android.material.button.MaterialButton(this,null,com.google.android.material.R.attr.materialButtonOutlinedStyle);
        cancel.setText("取消待付款订单");
        cancel.setOnClickListener(v->new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setMessage("取消本次结算单？同次结算的其他店铺订单也会一起关闭，预留库存会释放。").setNegativeButton("保留",null).setPositiveButton("取消订单",(dialog,which)->{
            cancel.setEnabled(false);new ApiClient(this).post("me/orders/"+orderId+"/cancel",new JSONObject(),response->{displayed="";load(false);},message->{cancel.setEnabled(true);Toast.makeText(this,message,Toast.LENGTH_LONG).show();});
        }).show());binding.itemList.addView(cancel);
    }
    private String zh(String value){return switch(value){case "PENDING_PAYMENT","WAITING_PAYMENT"->"等待付款";case "PAID","PREPARING"->"商家准备出库";case "READY_TO_SHIP"->"等待商家发货";case "SHIPPED"->"商品已发货";case "IN_TRANSIT"->"运输途中";case "OUT_FOR_DELIVERY"->"正在派送";case "COMPLETED"->"订单已完成";case "CANCELLED"->"订单已取消";default->value;};}
    private void error(String value){loading=false;binding.loading.setVisibility(View.GONE);if(!displayed.isEmpty())return;Toast.makeText(this,value,Toast.LENGTH_SHORT).show();}
}
