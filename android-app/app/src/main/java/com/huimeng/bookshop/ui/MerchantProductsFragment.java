package com.huimeng.bookshop.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.huimeng.bookshop.core.ApiClient;
import com.huimeng.bookshop.databinding.FragmentMerchantListBinding;
import org.json.*;
import java.util.*;

public class MerchantProductsFragment extends Fragment implements LocalDataRefresh {
    private FragmentMerchantListBinding binding;
    private ActionAdapter adapter;
    private MerchantListControls controls;
    private JSONArray products=new JSONArray();
    private Bundle filters;
    private boolean reading;
    private int request;
    private final androidx.activity.result.ActivityResultLauncher<Intent> editor=registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),result->{if(binding!=null)load(false);});
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,@Nullable ViewGroup container,@Nullable Bundle state){
        binding=FragmentMerchantListBinding.inflate(inflater,container,false);
        binding.title.setText("商品管理");binding.subtitle.setText("查找商品、继续编辑或查看审核");
        binding.add.setText("＋ 新增");binding.add.setOnClickListener(v->editor.launch(new Intent(requireContext(),AddProductActivity.class)));
        adapter=new ActionAdapter(this::open,this::open);binding.list.setLayoutManager(new LinearLayoutManager(requireContext()));binding.list.setAdapter(adapter);
        controls=new MerchantListControls(binding,true,state==null?filters:state.getBundle("filters"),this::render);
        new ApiClient(requireContext()).get("catalog/categories",r->{if(controls!=null){controls.catalogCategories(r.optJSONArray("data"));render();}},message->{});
        binding.getRoot().setOnRefreshListener(()->load(true));load(true);return binding.getRoot();
    }
    public void applyStatusFilter(String status){if(controls!=null)controls.reset(status);}
    private void load(boolean explicit){
        if(reading||binding==null)return;reading=true;int current=++request;
        new ApiClient(requireContext()).get("merchant/products",result->{
            if(binding==null||current!=request)return;reading=false;binding.loading.setVisibility(View.GONE);binding.getRoot().setRefreshing(false);
            JSONArray data=result.optJSONArray("data");products=data==null?new JSONArray():data;controls.categories(products);render();
        },message->{if(binding==null||current!=request)return;reading=false;binding.loading.setVisibility(View.GONE);binding.getRoot().setRefreshing(false);
            if(explicit||adapter.getItemCount()==0){binding.empty.setText(message);binding.empty.setVisibility(adapter.getItemCount()==0?View.VISIBLE:View.GONE);Toast.makeText(requireContext(),message,Toast.LENGTH_SHORT).show();}
        });
    }
    private void render(){
        if(binding==null||controls==null)return;List<ActionAdapter.Row> rows=new ArrayList<>();
        for(int i=0;i<products.length();i++){
            JSONObject item=products.optJSONObject(i);if(item==null||!MerchantWork.productMatches(item,controls.query(),controls.status(),controls.category()))continue;
            String note=MerchantWork.productStatus(item)+" · "+String.format(Locale.CHINA,"¥%.2f",item.optInt("priceCents")/100.0)+" · 库存 "+item.optInt("stock");
            String category=controls.categoryName(item);if(!category.isEmpty())note+="\n"+category;
            String rejection=item.isNull("rejectionReason")?"":item.optString("rejectionReason");if(!rejection.isEmpty())note+="\n驳回原因："+rejection;
            rows.add(new ActionAdapter.Row(item.optString("id"),item.optString("name"),note,MerchantWork.productAction(item),true,item.optString("coverUrl"),item.optString("name")));
        }
        adapter.submit(rows);binding.resultCount.setText("显示 "+rows.size()+" / "+products.length()+" 件商品");
        binding.empty.setText(products.length()==0?"暂无商品，点击右上角新增商品":"没有符合条件的商品，可点击重置清除筛选");binding.empty.setVisibility(rows.isEmpty()?View.VISIBLE:View.GONE);
    }
    private void open(ActionAdapter.Row row){editor.launch(new Intent(requireContext(),ManageProductActivity.class).putExtra("product_id",row.id()));}
    @Override public void onSaveInstanceState(@NonNull Bundle state){if(controls!=null)state.putBundle("filters",controls.save());super.onSaveInstanceState(state);}
    @Override public void onDestroyView(){if(controls!=null)filters=controls.save();controls=null;request++;reading=false;binding=null;super.onDestroyView();}
    @Override public void refreshLocalData(){if(binding!=null&&isAdded())load(false);}
}
