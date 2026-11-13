package com.huimeng.bookshop.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import com.huimeng.bookshop.databinding.FragmentMerchantListBinding;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

final class MerchantListControls {
    private final FragmentMerchantListBinding binding;
    private final String[] values;
    private final Runnable changed;
    private final List<String> categoryIds=new ArrayList<>();
    private final LinkedHashMap<String,String> categoryNames=new LinkedHashMap<>();
    private List<String> categoryLabels=new ArrayList<>();
    private JSONArray lastRows=new JSONArray();
    private String category="",selectedCategoryName="";
    private boolean updating;
    MerchantListControls(FragmentMerchantListBinding binding,boolean products,Bundle saved,Runnable changed){
        this.binding=binding;this.changed=changed;values=products?MerchantWork.PRODUCT_FILTERS:MerchantWork.ORDER_FILTERS;
        binding.searchQuery.setHint(products?"搜索商品名称或编号":"搜索订单号、买家或商品");
        binding.searchQuery.setContentDescription(products?"搜索商品":"搜索订单");
        binding.categoryFilter.setVisibility(products?View.VISIBLE:View.GONE);
        binding.statusFilter.setAdapter(new ArrayAdapter<>(binding.getRoot().getContext(),android.R.layout.simple_spinner_dropdown_item,products?MerchantWork.PRODUCT_LABELS:MerchantWork.ORDER_LABELS));
        binding.searchQuery.setText(saved==null?"":saved.getString("query",""));
        category=saved==null?"":saved.getString("category","");selectedCategoryName=saved==null?"":saved.getString("categoryName","");
        binding.statusFilter.setSelection(index(saved==null?"":saved.getString("status","")));
        binding.searchQuery.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){changed.run();}public void afterTextChanged(Editable e){}});
        binding.statusFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int n,long id){changed.run();}});
        binding.categoryFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int n,long id){if(!updating&&n<categoryIds.size()){category=categoryIds.get(n);selectedCategoryName=categoryLabels.get(n);changed.run();}}});
        binding.clearFilters.setOnClickListener(v->reset(""));
    }
    String query(){return binding.searchQuery.getText().toString();}
    String status(){return values[Math.max(0,Math.min(values.length-1,binding.statusFilter.getSelectedItemPosition()))];}
    String category(){return category;}
    private int index(String value){int index=java.util.Arrays.asList(values).indexOf(value);return Math.max(0,index);}
    void reset(String status){category="";binding.searchQuery.setText("");binding.categoryFilter.setSelection(0);binding.statusFilter.setSelection(index(status));changed.run();}
    Bundle save(){Bundle saved=new Bundle();saved.putString("query",query());saved.putString("status",status());saved.putString("category",category);saved.putString("categoryName",selectedCategoryName);return saved;}
    void catalogCategories(JSONArray rows){if(rows!=null)for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row!=null)categoryNames.put(row.optString("id"),row.optString("name"));}categories(lastRows);}
    String categoryName(JSONObject row){String id=row.optString("categoryId",row.optString("category_id"));String name=row.optString("category",row.optString("categoryName"));return name.isEmpty()?categoryNames.getOrDefault(id,""):name;}
    void categories(JSONArray rows){
        lastRows=rows;
        LinkedHashMap<String,String> options=new LinkedHashMap<>();options.put("","全部分类");
        options.putAll(categoryNames);
        for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row==null)continue;String id=row.optString("categoryId",row.optString("category_id"));String name=categoryName(row);if(!id.isEmpty())options.put(id,name.isEmpty()?options.getOrDefault(id,"其他分类"):name);}
        if(!category.isEmpty()&&!options.containsKey(category))options.put(category,selectedCategoryName.isEmpty()?"已选分类":selectedCategoryName);
        List<String> ids=new ArrayList<>(options.keySet());
        List<String> labels=new ArrayList<>(options.values());
        if(ids.equals(categoryIds)&&labels.equals(categoryLabels))return;
        categoryLabels=labels;
        updating=true;categoryIds.clear();categoryIds.addAll(ids);
        binding.categoryFilter.setAdapter(new ArrayAdapter<>(binding.getRoot().getContext(),android.R.layout.simple_spinner_dropdown_item,new ArrayList<>(options.values())));
        int selected=categoryIds.indexOf(category);if(selected<0){selected=0;category="";}binding.categoryFilter.setSelection(selected);updating=false;
    }
}
