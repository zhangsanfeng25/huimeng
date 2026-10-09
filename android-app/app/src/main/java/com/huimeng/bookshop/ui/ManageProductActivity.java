package com.huimeng.bookshop.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.huimeng.bookshop.core.ApiClient;
import com.huimeng.bookshop.databinding.ActivityAddProductBinding;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class ManageProductActivity extends AppCompatActivity{
    private ActivityAddProductBinding binding;private ProductImageEditor imageEditor;private final List<String> categoryIds=new ArrayList<>();private String productId,status="",selectedCategory="";
    @Override protected void onCreate(Bundle state){super.onCreate(state);binding=ActivityAddProductBinding.inflate(getLayoutInflater());setContentView(binding.getRoot());imageEditor=new ProductImageEditor(this,binding.images,binding.addImages,binding.imageHint,state);binding.save.setEnabled(false);productId=getIntent().getStringExtra("product_id");binding.screenTitle.setText("管理绘本商品");binding.save.setText("保存修改");binding.back.setOnClickListener(v->finish());binding.preview.setOnClickListener(v->startActivity(new Intent(this,ProductDetailActivity.class).putExtra("product_id",productId)));binding.save.setOnClickListener(v->save());binding.submitReview.setOnClickListener(v->submit());binding.offline.setOnClickListener(v->offline());loadCategories();}
    private void loadCategories(){new ApiClient(this).get("catalog/categories",result->{List<String> names=new ArrayList<>();JSONArray data=result.optJSONArray("data");if(data!=null)for(int i=0;i<data.length();i++){JSONObject item=data.optJSONObject(i);if(item!=null){categoryIds.add(item.optString("id"));names.add(item.optString("name"));}}binding.category.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));load();},this::toast);}
    private void load(){new ApiClient(this).get("merchant/products/"+productId,result->{JSONObject data=result.optJSONObject("data");if(data==null)return;status=data.optString("status");imageEditor.setData(data);selectedCategory=data.optString("categoryId");binding.name.setText(data.optString("name"));binding.summary.setText(data.optString("summary"));binding.age.setText(data.optString("ageRange"));binding.price.setText(String.valueOf(data.optInt("priceCents")/100.0));binding.stock.setText(String.valueOf(data.optInt("stock")));int index=categoryIds.indexOf(selectedCategory);if(index>=0)binding.category.setSelection(index);boolean pending="PENDING_REVIEW".equals(status);binding.save.setEnabled(!pending);imageEditor.setEnabled(!pending);binding.preview.setVisibility("ONLINE".equals(status)?View.VISIBLE:View.GONE);binding.submitReview.setVisibility(("DRAFT".equals(status)||"REJECTED".equals(data.optString("reviewStatus")))?View.VISIBLE:View.GONE);binding.offline.setVisibility("ONLINE".equals(status)?View.VISIBLE:View.GONE);},this::toast);}
    private JSONObject body() throws Exception{return imageEditor.applyTo(new JSONObject().put("name",text(binding.name)).put("summary",text(binding.summary)).put("categoryId",categoryIds.get(Math.max(0,binding.category.getSelectedItemPosition()))).put("ageRange",text(binding.age)).put("priceCents",(int)Math.round(Double.parseDouble(text(binding.price))*100)).put("stock",Integer.parseInt(text(binding.stock))));}
    private void save(){if(imageEditor.isBusy()){toast("请等待图片上传完成");return;}try{binding.save.setEnabled(false);new ApiClient(this).put("merchant/products/"+productId,body(),result->{toast("修改已保存");load();},error->{binding.save.setEnabled(true);toast(error);});}catch(Exception e){binding.save.setEnabled(true);toast("请完整填写正确的价格和库存");}}
    private void submit(){if(imageEditor.isBusy()){toast("请等待图片上传完成，并保存修改后提交审核");return;}new ApiClient(this).post("merchant/products/"+productId+"/submit",new JSONObject(),result->{toast("已提交管理员审核");load();},this::toast);}
    private void offline(){new ApiClient(this).post("merchant/products/"+productId+"/offline",new JSONObject(),result->{toast("商品已下架");load();},this::toast);}
    @Override protected void onResume(){super.onResume();imageEditor.resume();}
    @Override protected void onSaveInstanceState(Bundle state){imageEditor.saveState(state);super.onSaveInstanceState(state);}
    private String text(android.widget.TextView v){return String.valueOf(v.getText()).trim();}private void toast(String value){Toast.makeText(this,value,Toast.LENGTH_SHORT).show();}
}
