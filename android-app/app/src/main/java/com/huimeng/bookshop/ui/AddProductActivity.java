package com.huimeng.bookshop.ui;

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

public class AddProductActivity extends AppCompatActivity{
    private ActivityAddProductBinding binding;private ProductImageEditor imageEditor;private final List<String> categoryIds=new ArrayList<>();
    @Override protected void onCreate(Bundle state){super.onCreate(state);binding=ActivityAddProductBinding.inflate(getLayoutInflater());setContentView(binding.getRoot());imageEditor=new ProductImageEditor(this,binding.images,binding.addImages,binding.imageHint,state);binding.back.setOnClickListener(v->finish());binding.preview.setVisibility(View.GONE);binding.offline.setVisibility(View.GONE);binding.submitReview.setVisibility(View.GONE);binding.save.setOnClickListener(v->save());loadCategories();}
    private void loadCategories(){new ApiClient(this).get("catalog/categories",result->{List<String> names=new ArrayList<>();JSONArray data=result.optJSONArray("data");if(data!=null)for(int i=0;i<data.length();i++){JSONObject item=data.optJSONObject(i);if(item!=null){categoryIds.add(item.optString("id"));names.add(item.optString("name"));}}binding.category.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));},this::toast);}
    private void save(){if(imageEditor.isBusy()){toast("请等待图片上传完成");return;}String name=text(binding.name),summary=text(binding.summary),age=text(binding.age),price=text(binding.price),stock=text(binding.stock);if(name.isEmpty()||summary.isEmpty()||age.isEmpty()||price.isEmpty()||stock.isEmpty()||categoryIds.isEmpty()){toast("请完整填写商品信息");return;}binding.save.setEnabled(false);try{int cents=(int)Math.round(Double.parseDouble(price)*100),stockValue=Integer.parseInt(stock),index=binding.category.getSelectedItemPosition();JSONObject body=new JSONObject().put("name",name).put("summary",summary).put("categoryId",categoryIds.get(Math.max(0,index))).put("ageRange",age).put("priceCents",cents).put("stock",stockValue);imageEditor.applyTo(body);new ApiClient(this).post("merchant/products",body,result->{toast("商品草稿已创建");finish();},error->{binding.save.setEnabled(true);toast(error);});}catch(Exception error){binding.save.setEnabled(true);toast("价格或库存格式不正确");}}
    @Override protected void onResume(){super.onResume();imageEditor.resume();}
    @Override protected void onSaveInstanceState(Bundle state){imageEditor.saveState(state);super.onSaveInstanceState(state);}
    private String text(android.widget.TextView view){return String.valueOf(view.getText()).trim();}
    private void toast(String value){Toast.makeText(this,value,Toast.LENGTH_SHORT).show();}
}
