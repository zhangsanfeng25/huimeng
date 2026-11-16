package com.huimeng.bookshop.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import com.huimeng.bookshop.core.ApiClient;
import com.huimeng.bookshop.databinding.ActivityDataListBinding;
import com.huimeng.bookshop.model.Product;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class DataListActivity extends AppCompatActivity{
    private ActivityDataListBinding binding;private ProductAdapter adapter;
    @Override protected void onCreate(Bundle state){super.onCreate(state);
        binding=ActivityDataListBinding.inflate(getLayoutInflater());setContentView(binding.getRoot());
        binding.back.setOnClickListener(v->finish());binding.title.setText(getIntent().getStringExtra("title"));binding.subtitle.setText(getIntent().getStringExtra("subtitle"));
        adapter=new ProductAdapter(item->startActivity(new Intent(this,ProductDetailActivity.class).putExtra("product_id",item.id())));
        binding.list.setLayoutManager(new GridLayoutManager(this,ProductAdapter.columns(getResources().getConfiguration())));binding.list.setAdapter(adapter);
    }
    @Override protected void onResume(){super.onResume();load();}
    private void load(){binding.loading.setVisibility(View.VISIBLE);binding.empty.setVisibility(View.GONE);binding.emptyAction.setVisibility(View.GONE);new ApiClient(this).get("me/favorites",result->{if(isFinishing()||isDestroyed())return;
        binding.loading.setVisibility(View.GONE);List<Product> rows=new ArrayList<>();JSONArray data=result.optJSONArray("data");
        if(data!=null)for(int i=0;i<data.length();i++){JSONObject item=data.optJSONObject(i);if(item!=null)rows.add(Product.from(item));}
        adapter.submit(rows);
        if(rows.isEmpty())showState("暂无收藏的绘本","浏览绘本",()->startActivity(new Intent(this,SearchActivity.class)));
    },error->{if(isFinishing()||isDestroyed())return;binding.loading.setVisibility(View.GONE);showState(error,"重试加载",this::load);});}
    private void showState(String message,String action,Runnable onAction){
        binding.empty.setText(message);
        binding.empty.setVisibility(View.VISIBLE);
        binding.emptyAction.setText(action);
        binding.emptyAction.setOnClickListener(v->onAction.run());
        binding.emptyAction.setVisibility(View.VISIBLE);
    }
}
