package com.huimeng.bookshop.ui;

import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.huimeng.bookshop.core.ApiClient;
import com.huimeng.bookshop.databinding.ActivityBrowseBinding;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class RatingsActivity extends AppCompatActivity {
    private ActivityBrowseBinding binding;private TextAdapter adapter;private final List<TextAdapter.Row> rows=new ArrayList<>();private String productId;private int page=1;private boolean busy,failedAppend;
    @Override protected void onCreate(Bundle state){super.onCreate(state);binding=ActivityBrowseBinding.inflate(getLayoutInflater());setContentView(binding.getRoot());productId=getIntent().getStringExtra("product_id");binding.title.setText("读者评价");binding.subtitle.setText("来自已购读者的阅读感受");binding.back.setOnClickListener(v->finish());adapter=new TextAdapter();binding.list.setLayoutManager(new LinearLayoutManager(this));binding.list.setAdapter(adapter);binding.more.setOnClickListener(v->load(true));binding.retry.setOnClickListener(v->load(failedAppend));load(false);}
    private void load(boolean append){if(busy)return;busy=true;int target=append?page+1:1;binding.loading.setVisibility(View.VISIBLE);binding.retry.setVisibility(View.GONE);binding.empty.setVisibility(View.GONE);binding.more.setEnabled(false);
        new ApiClient(this).get("catalog/products/"+productId+"/ratings?page="+target+"&pageSize=20",result->{if(isFinishing()||isDestroyed())return;busy=false;page=target;if(!append)rows.clear();JSONArray data=result.optJSONArray("data");if(data!=null)for(int i=0;i<data.length();i++){JSONObject row=data.optJSONObject(i);if(row!=null){int stars=Math.max(1,Math.min(5,row.optInt("stars")));rows.add(new TextAdapter.Row(row.optString("user")+" · "+"★★★★★".substring(0,stars)+"☆☆☆☆☆".substring(stars),row.optString("content")+"\n\n"+row.optString("createdAt").replace('T',' ').substring(0,Math.min(16,row.optString("createdAt").length()))));}}adapter.submit(new ArrayList<>(rows));binding.subtitle.setText("共 "+result.optInt("total")+" 条评价 · 按最新发布排序");binding.loading.setVisibility(View.GONE);binding.empty.setText("暂时没有评价，期待你的阅读感受");binding.empty.setVisibility(rows.isEmpty()?View.VISIBLE:View.GONE);binding.more.setEnabled(true);binding.more.setVisibility(result.optBoolean("hasMore")?View.VISIBLE:View.GONE);},error->{if(isFinishing()||isDestroyed())return;busy=false;failedAppend=append;binding.loading.setVisibility(View.GONE);binding.more.setEnabled(true);binding.empty.setText(error);binding.empty.setVisibility(View.VISIBLE);binding.retry.setVisibility(View.VISIBLE);});
    }
}
