package com.huimeng.bookshop.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.bumptech.glide.Glide;
import com.huimeng.bookshop.R;
import com.huimeng.bookshop.core.ApiClient;
import com.huimeng.bookshop.core.LocalCartStore;
import com.huimeng.bookshop.databinding.ActivityCheckoutBinding;
import com.huimeng.bookshop.databinding.ItemOrderProductBinding;
import com.huimeng.bookshop.model.Product;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class CheckoutActivity extends AppCompatActivity {
    private ActivityCheckoutBinding binding;
    private String addressId="",checkoutKey,payloadSignature="";
    private JSONArray items=new JSONArray();
    private long total;
    private boolean submitting,ready;
    private int loadRequest;
    private LinearLayout lines;
    private ActivityResultLauncher<Intent> addressPicker;
    private ActivityResultLauncher<Intent> addressEditor;
    private JSONObject selectedAddress;

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        addressEditor=registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),result->{if(result.getResultCode()==RESULT_OK&&result.getData()!=null){addressId=result.getData().getStringExtra("saved_address_id");if(addressId==null)addressId="";load();}});
        addressPicker=registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),result->{if(result.getResultCode()==RESULT_OK&&result.getData()!=null)try{JSONObject address=new JSONObject(result.getData().getStringExtra("selected_address"));addressId=address.optString("id");load();}catch(Exception ignored){error("地址选择失败，请重试");}});
        checkoutKey=state==null?UUID.randomUUID().toString():state.getString("checkout_key",UUID.randomUUID().toString());
        if(state!=null){payloadSignature=state.getString("payload_signature","");addressId=state.getString("address_id","");}
        binding=ActivityCheckoutBinding.inflate(getLayoutInflater());setContentView(binding.getRoot());
        lines=new LinearLayout(this);lines.setOrientation(LinearLayout.VERTICAL);int pad=Math.round(16*getResources().getDisplayMetrics().density);lines.setPadding(pad,pad,pad,pad);
        ((ViewGroup)binding.items.getParent()).addView(lines,new ViewGroup.LayoutParams(-1,-2));binding.items.setVisibility(View.GONE);
        binding.back.setOnClickListener(v->finish());binding.addAddress.setOnClickListener(v->{if(!submitting)addressEditor.launch(new Intent(this,AddAddressActivity.class));});
        binding.editAddress.setOnClickListener(v->{if(!submitting&&selectedAddress!=null)addressEditor.launch(new Intent(this,AddAddressActivity.class).putExtra("copy_address",selectedAddress.toString()));});
        binding.changeAddress.setOnClickListener(v->{if(!submitting)addressPicker.launch(new Intent(this,AddressesActivity.class).putExtra("select_address",true));});
        binding.submit.setOnClickListener(v->checkout());
    }
    @Override protected void onSaveInstanceState(Bundle state){state.putString("checkout_key",checkoutKey);state.putString("payload_signature",payloadSignature);state.putString("address_id",addressId);super.onSaveInstanceState(state);}
    @Override protected void onResume(){super.onResume();if(!submitting)load();}
    private boolean alive(){return !isFinishing()&&!isDestroyed();}
    private void load(){final int request=++loadRequest;ready=false;binding.submit.setEnabled(false);binding.loading.setVisibility(View.VISIBLE);new ApiClient(this).get("me/addresses",result->{if(!alive()||request!=loadRequest)return;JSONArray data=result.optJSONArray("data");JSONObject chosen=null;if(data!=null)for(int i=0;i<data.length();i++){JSONObject row=data.optJSONObject(i);if(row!=null&&(chosen==null||addressId.equals(row.optString("id"))))chosen=row;if(row!=null&&addressId.equals(row.optString("id")))break;}
        selectedAddress=chosen;addressId=chosen==null?"":chosen.optString("id");binding.editAddress.setVisibility(chosen==null?View.GONE:View.VISIBLE);binding.editAddress.setEnabled(chosen!=null);binding.changeAddress.setVisibility(chosen==null?View.GONE:View.VISIBLE);binding.address.setText(chosen==null?"尚未添加收货地址":chosen.optString("recipient")+"  "+chosen.optString("phone")+"\n"+chosen.optString("region")+chosen.optString("detail"));
        loadProducts(request);
    },message->{if(request==loadRequest)error(message);});}
    private void loadProducts(int request){String productId=getIntent().getStringExtra("product_id");
        if(productId!=null&&!productId.isEmpty()){new ApiClient(this).get("catalog/products/"+productId,result->{if(!alive()||request!=loadRequest)return;JSONObject data=result.optJSONObject("data");if(data==null){error("商品不存在或已下架");return;}Product product=Product.from(data);int quantity=getIntent().getIntExtra("quantity",1);if(quantity<1||quantity>product.stock()){error("商品库存不足，请返回重新选择数量");return;}render(List.of(new LocalCartStore.Entry(product,quantity,true)));},message->{if(request==loadRequest)error(message);});}
        else LocalCartStore.resolve(this,cart->{if(alive()&&request==loadRequest)render(cart.list());},message->{if(request==loadRequest)error(message);});
    }
    private void render(List<LocalCartStore.Entry> cart){
        items=new JSONArray();total=0;lines.setVisibility(View.VISIBLE);binding.items.setVisibility(View.GONE);lines.removeAllViews();boolean valid=true;Map<String,List<LocalCartStore.Entry>> shops=new LinkedHashMap<>();
        for(LocalCartStore.Entry row:cart){if(!row.selected())continue;Product product=row.product();if(row.quantity()>product.stock())valid=false;try{items.put(new JSONObject().put("productId",product.id()).put("quantity",row.quantity()));}catch(Exception ignored){}total+=(long)product.priceCents()*row.quantity();shops.computeIfAbsent(product.shop(),key->new ArrayList<>()).add(row);}
        for(Map.Entry<String,List<LocalCartStore.Entry>> shop:shops.entrySet()){TextView title=new TextView(this);title.setText(shop.getKey());title.setTextColor(ContextCompat.getColor(this,R.color.ink));title.setTextSize(16);title.setTypeface(null,android.graphics.Typeface.BOLD);title.setPadding(0,0,0,12);lines.addView(title);
            for(LocalCartStore.Entry row:shop.getValue()){Product product=row.product();ItemOrderProductBinding line=ItemOrderProductBinding.inflate(getLayoutInflater(),lines,false);line.name.setText(product.name());line.quantity.setText("单价 "+product.price()+" · 数量 × "+row.quantity()+(row.quantity()>product.stock()?"\n库存不足，请返回调整":""));line.price.setText(String.format(Locale.CHINA,"¥%.2f",product.priceCents()*row.quantity()/100.0));Glide.with(this).load(ApiClient.assetUrl(product.coverUrl())).placeholder(new BookCoverDrawable(product.name())).error(new BookCoverDrawable(product.name())).fitCenter().into(line.cover);lines.addView(line.getRoot());}
        }
        TextView summary=new TextView(this);summary.setTextColor(ContextCompat.getColor(this,R.color.muted));summary.setTextSize(14);summary.setText(items.length()==0?"尚未选择商品，请返回购物车勾选":String.format(Locale.CHINA,"商品金额  ¥%.2f\n运费  ¥0.00（本地演示）\n应付合计  ¥%.2f",total/100.0,total/100.0));lines.addView(summary);
        binding.total.setText(String.format(Locale.CHINA,"¥%.2f",total/100.0));binding.loading.setVisibility(View.GONE);ready=valid;binding.submit.setEnabled(ready&&!submitting&&!addressId.isEmpty()&&items.length()>0);
    }
    private void checkout(){if(submitting||!ready||addressId.isEmpty()||items.length()==0)return;String signature=addressId+"|"+items;if(!signature.equals(payloadSignature)){checkoutKey=UUID.randomUUID().toString();payloadSignature=signature;}submitting=true;binding.submit.setEnabled(false);Map<String,String> headers=new HashMap<>();headers.put("Idempotency-Key","android-"+checkoutKey);
        try{JSONObject body=new JSONObject().put("addressId",addressId).put("items",items);new ApiClient(this).post("checkout",body,headers,result->{if(!alive())return;String group=result.optString("checkoutGroupId");if(group.isEmpty()){error("订单创建失败，请到订单列表确认后重试");return;}startActivity(new Intent(this,PaymentActivity.class).putExtra("group_id",group).putExtra("amount_cents",result.optInt("totalCents",(int)total)));finish();},this::error);}catch(Exception exception){error("订单参数错误");}
    }
    private void error(String message){if(!alive())return;submitting=false;ready=false;binding.loading.setVisibility(View.GONE);binding.submit.setEnabled(false);Toast.makeText(this,message,Toast.LENGTH_LONG).show();binding.items.setText(message+"\n点击重试加载");binding.items.setVisibility(View.VISIBLE);lines.setVisibility(View.GONE);binding.items.setOnClickListener(v->{binding.items.setVisibility(View.GONE);lines.setVisibility(View.VISIBLE);load();});}
}
