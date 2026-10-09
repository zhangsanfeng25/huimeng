package com.huimeng.bookshop.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.HorizontalScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.huimeng.bookshop.R;
import com.huimeng.bookshop.core.ApiClient;
import com.huimeng.bookshop.core.SessionStore;
import com.huimeng.bookshop.databinding.ActivityBrowseBinding;
import com.huimeng.bookshop.model.Product;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class SearchActivity extends AppCompatActivity {
    private ActivityBrowseBinding binding;private ProductAdapter adapter;private TextInputEditText input;private ChipGroup history,sorts;
    private final List<Product> rows=new ArrayList<>();private String query="",category="",categoryName="",age="",theme="",sort="comprehensive";private int page=1,requestId;private boolean busy,failedAppend,readyForMore;
    @Override protected void onCreate(Bundle state){super.onCreate(state);binding=ActivityBrowseBinding.inflate(getLayoutInflater());setContentView(binding.getRoot());binding.title.setText("搜索绘本");binding.back.setOnClickListener(v->finish());
        query=restoredString(state,"query","");category=restoredString(state,"category","");categoryName=restoredString(state,"category_name","");if(state!=null)sort=state.getString("sort","comprehensive");
        age=restoredString(state,"age","");theme=restoredString(state,"theme","");
        binding.title.setText(restoredString(state,"browse_title","搜索绘本"));
        TextInputLayout field=new TextInputLayout(this);field.setHint("搜索绘本名称或内容");input=new TextInputEditText(this);input.setSingleLine(true);input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);field.addView(input,new LinearLayout.LayoutParams(-1,-2));binding.controls.addView(field);input.setText(state==null?query:state.getString("input",query));
        input.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_SEARCH){search();return true;}return false;});
        LinearLayout actions=new LinearLayout(this);MaterialButton go=button("搜索"),categories=button("分类");actions.addView(go,new LinearLayout.LayoutParams(0,dp(48),1));actions.addView(categories,new LinearLayout.LayoutParams(0,dp(48),1));binding.controls.addView(actions);go.setOnClickListener(v->search());categories.setOnClickListener(v->startActivity(new Intent(this,CategoriesActivity.class)));
        LinearLayout heading=new LinearLayout(this);heading.setGravity(android.view.Gravity.CENTER_VERTICAL);TextView title=new TextView(this);title.setText("搜索历史");title.setTextColor(ContextCompat.getColor(this,R.color.muted));heading.addView(title,new LinearLayout.LayoutParams(0,-2,1));MaterialButton clear=button("清空");heading.addView(clear);binding.controls.addView(heading);clear.setOnClickListener(v->{getSharedPreferences("huimeng_search",Context.MODE_PRIVATE).edit().remove(historyKey()).apply();renderHistory();});
        history=new ChipGroup(this);history.setSingleLine(true);HorizontalScrollView historyScroll=new HorizontalScrollView(this);historyScroll.setHorizontalScrollBarEnabled(false);historyScroll.addView(history);binding.controls.addView(historyScroll);renderHistory();
        sorts=new ChipGroup(this);sorts.setSingleLine(true);sorts.setSingleSelection(true);for(String label:new String[]{"综合","最新","价格 ↑↓"}){Chip chip=new Chip(this);chip.setText(label);chip.setCheckable(true);chip.setChecked((label.equals("综合")&&sort.equals("comprehensive"))||(label.equals("最新")&&sort.equals("newest"))||(label.startsWith("价格")&&sort.startsWith("price")));chip.setOnClickListener(v->{sort=label.equals("综合")?"comprehensive":label.equals("最新")?"newest":sort.equals("price_asc")?"price_desc":"price_asc";if(label.startsWith("价格"))chip.setText(sort.equals("price_asc")?"价格 ↑":"价格 ↓");load(false);});sorts.addView(chip);}binding.controls.addView(sorts);
        if(!category.isEmpty()||!age.isEmpty()||!theme.isEmpty()){MaterialButton reset=button(filterLabel()+" × 清除筛选");binding.controls.addView(reset);reset.setOnClickListener(v->{category="";categoryName="";age="";theme="";binding.title.setText("全部绘本");binding.controls.removeView(reset);load(false);});}
        adapter=new ProductAdapter(item->startActivity(new Intent(this,ProductDetailActivity.class).putExtra("product_id",item.id())));binding.list.setLayoutManager(new GridLayoutManager(this,2));binding.list.setAdapter(adapter);binding.more.setOnClickListener(v->load(true));binding.retry.setOnClickListener(v->load(failedAppend));load(false);
    }
    private String restoredString(Bundle state,String key,String fallback){
        String value=state==null?getIntent().getStringExtra(key):state.getString(key);
        return value==null?fallback:value;
    }
    private MaterialButton button(String label){MaterialButton v=new MaterialButton(this,null,com.google.android.material.R.attr.borderlessButtonStyle);v.setText(label);v.setMinHeight(dp(48));return v;}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private String historyKey(){return "history_"+new SessionStore(this).accountId();}
    private List<String> previous(){List<String> values=new ArrayList<>();try{JSONArray data=new JSONArray(getSharedPreferences("huimeng_search",0).getString(historyKey(),"[]"));for(int i=0;i<data.length();i++)values.add(data.optString(i));}catch(Exception ignored){}return values;}
    private void renderHistory(){history.removeAllViews();List<String> values=previous();if(values.isEmpty()){TextView empty=new TextView(this);empty.setText("暂无搜索历史");empty.setTextColor(ContextCompat.getColor(this,R.color.muted));history.addView(empty);}for(String value:values){Chip chip=new Chip(this);chip.setText(value);chip.setOnClickListener(v->{input.setText(value);search();});history.addView(chip);}}
    private void search(){query=String.valueOf(input.getText()).trim();if(query.length()>100){input.setError("关键词最多 100 字");return;}if(!query.isEmpty()){List<String> values=previous();values.remove(query);values.add(0,query);getSharedPreferences("huimeng_search",0).edit().putString(historyKey(),new JSONArray(values.subList(0,Math.min(8,values.size()))).toString()).apply();renderHistory();}input.clearFocus();((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(),0);load(false);}
    private void load(boolean append){if(append&&(busy||!readyForMore))return;if(!append){readyForMore=false;binding.more.setVisibility(View.GONE);}busy=true;int id=++requestId,target=append?page+1:1;binding.loading.setVisibility(View.VISIBLE);binding.empty.setVisibility(View.GONE);binding.retry.setVisibility(View.GONE);binding.more.setEnabled(false);
        new ApiClient(this).get("catalog/products?q="+Uri.encode(query)+"&category="+Uri.encode(category)+"&age="+Uri.encode(age)+"&theme="+Uri.encode(theme)+"&sort="+sort+"&page="+target+"&pageSize=12",result->{if(isFinishing()||isDestroyed()||id!=requestId)return;busy=false;readyForMore=true;page=target;if(!append)rows.clear();JSONArray data=result.optJSONArray("data");if(data!=null)for(int i=0;i<data.length();i++){JSONObject item=data.optJSONObject(i);if(item!=null)rows.add(Product.from(item));}adapter.submit(new ArrayList<>(rows));String label=filterLabel();binding.subtitle.setText((label.isEmpty()?"":label+" · ")+"找到 "+result.optInt("total")+" 本绘本");binding.loading.setVisibility(View.GONE);binding.empty.setText("没有找到相关绘本，试试其他关键词或分类");binding.empty.setVisibility(rows.isEmpty()?View.VISIBLE:View.GONE);binding.more.setEnabled(true);binding.more.setVisibility(result.optBoolean("hasMore")?View.VISIBLE:View.GONE);},error->{if(isFinishing()||isDestroyed()||id!=requestId)return;busy=false;failedAppend=append;if(!append){rows.clear();adapter.submit(new ArrayList<>());page=1;binding.more.setVisibility(View.GONE);}binding.loading.setVisibility(View.GONE);binding.more.setEnabled(true);binding.empty.setText(error);binding.empty.setVisibility(View.VISIBLE);binding.retry.setVisibility(View.VISIBLE);});
    }
    private String filterLabel(){List<String> labels=new ArrayList<>();if(!categoryName.isEmpty())labels.add(categoryName);if(!age.isEmpty())labels.add(age.equals("9-plus")?"9岁以上":age.replace('-','–')+"岁");if(!theme.isEmpty())labels.add(theme.equals("bedtime")?"睡前故事":theme.equals("emotion")?"情绪成长":"科普启蒙");return android.text.TextUtils.join(" · ",labels);}
    @Override protected void onSaveInstanceState(Bundle state){state.putString("query",query);state.putString("input",String.valueOf(input.getText()));state.putString("category",category);state.putString("category_name",categoryName);state.putString("age",age);state.putString("theme",theme);state.putString("browse_title",binding.title.getText().toString());state.putString("sort",sort);super.onSaveInstanceState(state);}
}
