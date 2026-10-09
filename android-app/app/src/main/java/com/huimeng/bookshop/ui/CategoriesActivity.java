package com.huimeng.bookshop.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.core.content.ContextCompat;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.huimeng.bookshop.R;
import com.huimeng.bookshop.core.ApiClient;
import com.huimeng.bookshop.databinding.ActivityBrowseBinding;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CategoriesActivity extends AppCompatActivity {
    private ActivityBrowseBinding binding;private final Map<String,List<JSONObject>> groups=new LinkedHashMap<>();
    @Override protected void onCreate(Bundle state){super.onCreate(state);binding=ActivityBrowseBinding.inflate(getLayoutInflater());setContentView(binding.getRoot());binding.title.setText("绘本分类");binding.subtitle.setText("按阅读主题与成长方向，找到适合孩子的绘本");binding.back.setOnClickListener(v->finish());binding.list.setLayoutManager(new LinearLayoutManager(this));binding.retry.setOnClickListener(v->load());load();}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private void load(){binding.loading.setVisibility(View.VISIBLE);binding.retry.setVisibility(View.GONE);binding.empty.setVisibility(View.GONE);new ApiClient(this).get("catalog/categories",result->{if(isFinishing()||isDestroyed())return;groups.clear();JSONArray data=result.optJSONArray("data");if(data!=null)for(int i=0;i<data.length();i++){JSONObject row=data.optJSONObject(i);if(row!=null)groups.computeIfAbsent(row.optString("parent","其他分类"),key->new ArrayList<>()).add(row);}binding.loading.setVisibility(View.GONE);binding.empty.setText("暂无分类");binding.empty.setVisibility(groups.isEmpty()?View.VISIBLE:View.GONE);binding.list.setAdapter(new CategoryGroups());},error->{if(isFinishing()||isDestroyed())return;binding.loading.setVisibility(View.GONE);binding.empty.setText(error);binding.empty.setVisibility(View.VISIBLE);binding.retry.setVisibility(View.VISIBLE);});}
    private final class CategoryGroups extends RecyclerView.Adapter<CategoryGroups.Holder>{
        private final List<String> parents=new ArrayList<>(groups.keySet());
        class Holder extends RecyclerView.ViewHolder{final LinearLayout content;Holder(MaterialCardView card,LinearLayout content){super(card);this.content=content;}}
        @Override public Holder onCreateViewHolder(ViewGroup parent,int type){MaterialCardView card=new MaterialCardView(CategoriesActivity.this);card.setCardBackgroundColor(ContextCompat.getColor(CategoriesActivity.this,R.color.surface_card));card.setRadius(dp(20));card.setCardElevation(0);card.setStrokeWidth(dp(1));card.setStrokeColor(ContextCompat.getColor(CategoriesActivity.this,R.color.line_soft));RecyclerView.LayoutParams params=new RecyclerView.LayoutParams(-1,-2);params.setMargins(0,0,0,dp(14));card.setLayoutParams(params);LinearLayout content=new LinearLayout(CategoriesActivity.this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(16),dp(16),dp(16),dp(16));card.addView(content);return new Holder(card,content);}
        @Override public void onBindViewHolder(Holder holder,int position){holder.content.removeAllViews();String parent=parents.get(position);TextView title=new TextView(CategoriesActivity.this);title.setText(parent);title.setTextSize(18);title.setTextColor(ContextCompat.getColor(CategoriesActivity.this,R.color.ink));title.setTypeface(null,android.graphics.Typeface.BOLD);holder.content.addView(title);ChipGroup chips=new ChipGroup(CategoriesActivity.this);for(JSONObject row:groups.get(parent)){Chip chip=new Chip(CategoriesActivity.this);chip.setText(row.optString("name"));chip.setMinHeight(dp(48));chip.setOnClickListener(v->startActivity(new Intent(CategoriesActivity.this,SearchActivity.class).putExtra("category",row.optString("id")).putExtra("category_name",row.optString("name"))));chips.addView(chip);}holder.content.addView(chips);}
        @Override public int getItemCount(){return parents.size();}
    }
}
