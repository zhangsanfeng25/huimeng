package com.huimeng.bookshop.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.huimeng.bookshop.core.ApiClient;
import com.huimeng.bookshop.databinding.ActivityAddProductBinding;
import org.json.*;
import java.util.*;

public class AddProductActivity extends AppCompatActivity {
    private ActivityAddProductBinding binding;
    private ProductImageEditor imageEditor; private ProductFormSections sections;
    private BookMetadataEditor bookEditor;
    private final List<String> categoryIds=new ArrayList<>();
    private String productId="";
    private boolean busy;
    @Override protected void onCreate(Bundle state){super.onCreate(state);binding=ActivityAddProductBinding.inflate(getLayoutInflater());setContentView(binding.getRoot());
        sections=new ProductFormSections(binding,state);imageEditor=new ProductImageEditor(this,binding.images,binding.addImages,binding.imageHint,state);bookEditor=new BookMetadataEditor(this,binding.productMediaTitle,state);
        if(state!=null)productId=state.getString("created_product","");
        binding.back.setOnClickListener(v->finish());binding.preview.setVisibility(View.GONE);binding.offline.setVisibility(View.GONE);
        binding.submitReview.setVisibility(View.VISIBLE);binding.submitReview.setText("保存并送审");binding.save.setOnClickListener(v->save(false));binding.submitReview.setOnClickListener(v->save(true));
        binding.editorNotice.setText("保存草稿可稍后继续；保存并送审会先保存当前输入，再提交审核");binding.editorNotice.setVisibility(View.VISIBLE);loadCategories();
    }
    private boolean alive(){return !isFinishing()&&!isDestroyed();}
    private void loadCategories(){new ApiClient(this).get("catalog/categories",result->{if(!alive())return;List<String> names=new ArrayList<>();categoryIds.clear();JSONArray rows=result.optJSONArray("data");if(rows!=null)for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row!=null){categoryIds.add(row.optString("id"));names.add(row.optString("name"));}}binding.category.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));},this::error);}
    private void save(boolean review){
        if(busy)return;if(imageEditor.isBusy()){error("请等待图片上传完成");return;}
        String name=text(binding.name),summary=text(binding.summary),age=text(binding.age),price=text(binding.price),stock=text(binding.stock);
        if(name.isEmpty()||summary.isEmpty()||age.isEmpty()||price.isEmpty()||stock.isEmpty()||categoryIds.isEmpty()){error("请完整填写商品信息");return;}
        final JSONObject body;try{
            double amount=Double.parseDouble(price);int quantity=Integer.parseInt(stock);if(!Double.isFinite(amount)||amount<0||amount>Integer.MAX_VALUE/100.0||quantity<0)throw new IllegalArgumentException("请填写有效的价格和库存");
            body=new JSONObject().put("name",name).put("summary",summary).put("categoryId",categoryIds.get(Math.max(0,binding.category.getSelectedItemPosition()))).put("ageRange",age).put("priceCents",Math.round(amount*100)).put("stock",quantity);imageEditor.applyTo(body);bookEditor.applyTo(body);
        }catch(Exception error){error(error instanceof NumberFormatException?"价格或库存格式不正确":error.getMessage());return;}
        busy(true);binding.editorNotice.setText(review?"正在保存当前输入并提交审核…":"正在保存草稿…");
        java.util.function.Consumer<JSONObject> saved=result->{
            if(!alive())return;productId=result.optString("productId",result.optString("id",productId));
            if(!review){Toast.makeText(this,result.optBoolean("pending")?"商品草稿已保存在本地，待联网同步":"商品草稿已创建",Toast.LENGTH_SHORT).show();finish();return;}
            if(productId.isEmpty()){busy(false);error("草稿已保存，但未返回商品编号，请返回商品列表继续送审");return;}
            new ApiClient(this).post("merchant/products/"+productId+"/submit",new JSONObject(),r->{if(alive()){Toast.makeText(this,r.optBoolean("pending")?"当前内容和送审意向已保存，待联网确认":"商品已保存并提交审核",Toast.LENGTH_LONG).show();finish();}},message->{if(alive()){busy(false);error("草稿已保存，送审未完成："+message);}});
        };
        java.util.function.Consumer<String> failed=message->{if(alive()){busy(false);error(message);}};
        if(productId.isEmpty())new ApiClient(this).post("merchant/products",body,saved,failed);else new ApiClient(this).put("merchant/products/"+productId,body,saved,failed);
    }
    private void busy(boolean value){busy=value;for(View view:new View[]{binding.save,binding.submitReview,binding.name,binding.summary,binding.age,binding.price,binding.stock,binding.category})view.setEnabled(!value);imageEditor.setEnabled(!value);bookEditor.setEnabled(!value);}
    private void error(String value){if(!alive())return;binding.editorNotice.setText(value==null?"操作未完成，请重试":value);Toast.makeText(this,value,Toast.LENGTH_LONG).show();}
    @Override protected void onResume(){super.onResume();imageEditor.resume();}
    @Override protected void onSaveInstanceState(Bundle state){state.putString("created_product",productId);sections.save(state);imageEditor.saveState(state);bookEditor.save(state);super.onSaveInstanceState(state);}
    private String text(android.widget.TextView view){return String.valueOf(view.getText()).trim();}
}
