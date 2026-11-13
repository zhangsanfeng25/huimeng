package com.huimeng.bookshop.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.huimeng.bookshop.core.ApiClient;
import com.huimeng.bookshop.databinding.ActivityAddProductBinding;
import org.json.*;
import java.util.*;

public class ManageProductActivity extends AppCompatActivity {
    private ActivityAddProductBinding binding;
    private ProductImageEditor imageEditor; private ProductFormSections sections;
    private BookMetadataEditor bookEditor;
    private final List<String> categoryIds=new ArrayList<>();
    private String productId,status="";
    private boolean busy,pendingReview,loaded,stockEligible;
    @Override protected void onCreate(Bundle state){super.onCreate(state);binding=ActivityAddProductBinding.inflate(getLayoutInflater());setContentView(binding.getRoot());
        sections=new ProductFormSections(binding,state);imageEditor=new ProductImageEditor(this,binding.images,binding.addImages,binding.imageHint,state);bookEditor=new BookMetadataEditor(this,binding.productMediaTitle,state);
        productId=getIntent().getStringExtra("product_id");binding.screenTitle.setText("管理绘本商品");binding.save.setText("保存草稿");binding.submitReview.setText("保存并送审");
        binding.back.setOnClickListener(v->finish());binding.preview.setOnClickListener(v->startActivity(new Intent(this,ProductDetailActivity.class).putExtra("product_id",productId)));
        binding.save.setOnClickListener(v->save(false));binding.submitReview.setOnClickListener(v->save(true));binding.offline.setOnClickListener(v->offline());editable(false);buttons();loadCategories();
    }
    private boolean alive(){return !isFinishing()&&!isDestroyed();}
    private void loadCategories(){new ApiClient(this).get("catalog/categories",result->{if(!alive())return;List<String> names=new ArrayList<>();categoryIds.clear();JSONArray rows=result.optJSONArray("data");
        if(rows!=null)for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row!=null){categoryIds.add(row.optString("id"));names.add(row.optString("name"));}}
        binding.category.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));load();
    },this::error);}
    private void load(){new ApiClient(this).get("merchant/products/"+productId,result->{if(!alive())return;JSONObject data=result.optJSONObject("data");if(data==null){error("未读取到商品，请返回后重试");return;}
        loaded=true;status=data.optString("status");pendingReview=MerchantWork.reviewPending(data);imageEditor.setData(data);bookEditor.setData(data);
        binding.name.setText(data.optString("name"));binding.summary.setText(data.optString("summary"));binding.age.setText(data.optString("ageRange"));binding.price.setText(String.format(Locale.ROOT,"%.2f",data.optInt("priceCents")/100.0));binding.stock.setText(String.valueOf(data.optInt("stock")));
        int index=categoryIds.indexOf(data.optString("categoryId"));if(index>=0)binding.category.setSelection(index);
        binding.preview.setVisibility("ONLINE".equals(status)?View.VISIBLE:View.GONE);
        String remote=data.optString("remoteId",productId);
        binding.stockQuick.setVisibility(View.VISIBLE);stockEligible=!remote.isEmpty()&&!remote.startsWith("local-")&&!data.has("pendingAction");binding.stockQuick.setEnabled(stockEligible);
        binding.stockQuick.setOnClickListener(v->ProductStockEditor.open(this,remote,quantity->{binding.stock.setText(String.valueOf(quantity));notice("可售库存已更新为 "+quantity+" 件，商品上架与审核状态保持不变");}));
        binding.submitReview.setVisibility(("DRAFT".equals(status)||"REJECTED".equals(data.optString("reviewStatus"))||pendingReview)?View.VISIBLE:View.GONE);
        binding.offline.setVisibility("ONLINE".equals(status)?View.VISIBLE:View.GONE);
        String rejection=data.isNull("rejectionReason")?"":data.optString("rejectionReason");
        notice(pendingReview?("submit".equals(data.optString("pendingAction"))?"送审意向已保存在本地，联网确认前暂不能修改":"商品审核中，暂不能修改；审核结果会通过消息通知")
            :!rejection.isEmpty()?"驳回原因："+rejection:"ONLINE".equals(status)?"当前商品在售；保存修改后将转为草稿，需要重新送审":"保存草稿可稍后继续；保存并送审会先保存当前输入，再提交审核");
        busy=false;editable(!pendingReview);buttons();
    },message->{if(alive()){busy=false;editable(loaded&&!pendingReview);buttons();error(message);}});}
    private JSONObject body()throws Exception{
        if(categoryIds.isEmpty())throw new IllegalArgumentException("请先读取有效商品分类");
        if(text(binding.name).isEmpty()||text(binding.summary).isEmpty()||text(binding.age).isEmpty())throw new IllegalArgumentException("请完整填写名称、简介和适龄范围");
        double price=Double.parseDouble(text(binding.price));int stock=Integer.parseInt(text(binding.stock));
        if(!Double.isFinite(price)||price<0||price>Integer.MAX_VALUE/100.0||stock<0)throw new IllegalArgumentException("请填写有效的价格和库存");
        return bookEditor.applyTo(imageEditor.applyTo(new JSONObject().put("name",text(binding.name)).put("summary",text(binding.summary)).put("categoryId",categoryIds.get(Math.max(0,binding.category.getSelectedItemPosition()))).put("ageRange",text(binding.age)).put("priceCents",Math.round(price*100)).put("stock",stock)));
    }
    private void save(boolean review){
        if(busy||pendingReview||!loaded)return;if(imageEditor.isBusy()){error("请等待图片上传完成");return;}
        final JSONObject value;try{value=body();}catch(Exception e){error(e instanceof NumberFormatException?"请完整填写正确的价格和库存":e.getMessage());return;}
        busy=true;editable(false);buttons();notice(review?"正在保存当前输入并提交审核…":"正在保存草稿…");
        new ApiClient(this).put("merchant/products/"+productId,value,result->{if(!alive())return;
            if(review)postReview();else{Toast.makeText(this,result.optBoolean("pending")?"草稿已保存在本地，待联网同步":"草稿已保存",Toast.LENGTH_SHORT).show();load();}
        },message->{if(!alive())return;busy=false;editable(true);buttons();error(message);});
    }
    private void postReview(){new ApiClient(this).post("merchant/products/"+productId+"/submit",new JSONObject(),result->{if(!alive())return;Toast.makeText(this,result.optBoolean("pending")?"当前内容和送审意向已保存，待联网确认":"当前内容已保存并提交管理员审核",Toast.LENGTH_LONG).show();load();},message->{if(!alive())return;busy=false;editable(true);buttons();error("草稿已保存，送审未完成："+message);});}
    private void offline(){if(busy)return;new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setMessage("下架此商品？下架后买家将无法继续购买。").setNegativeButton("暂不",null).setPositiveButton("确认下架",(d,w)->{
        busy=true;editable(false);buttons();new ApiClient(this).post("merchant/products/"+productId+"/offline",new JSONObject(),r->{if(alive()){Toast.makeText(this,r.optBoolean("pending")?"下架意向已保存，待联网确认":"商品已下架",Toast.LENGTH_SHORT).show();load();}},message->{if(alive()){busy=false;editable(!pendingReview);buttons();error(message);}});
    }).show();}
    private void buttons(){binding.save.setEnabled(loaded&&!busy&&!pendingReview);binding.submitReview.setEnabled(loaded&&!busy&&!pendingReview);binding.offline.setEnabled(loaded&&!busy&&!pendingReview);binding.stockQuick.setEnabled(stockEligible&&!busy);}
    private void editable(boolean enabled){for(View view:new View[]{binding.name,binding.summary,binding.age,binding.price,binding.stock,binding.category})view.setEnabled(enabled);imageEditor.setEnabled(enabled);bookEditor.setEnabled(enabled);}
    private void notice(String value){binding.editorNotice.setText(value);binding.editorNotice.setVisibility(View.VISIBLE);}
    private void error(String value){if(!alive())return;notice(value==null?"操作未完成，请重试":value);Toast.makeText(this,value,Toast.LENGTH_LONG).show();}
    @Override protected void onResume(){super.onResume();imageEditor.resume();}
    @Override protected void onSaveInstanceState(Bundle state){sections.save(state);imageEditor.saveState(state);bookEditor.save(state);super.onSaveInstanceState(state);}
    private String text(android.widget.TextView view){return String.valueOf(view.getText()).trim();}
}
