package com.huimeng.bookshop.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.huimeng.bookshop.core.ApiClient;
import com.huimeng.bookshop.databinding.FragmentMerchantListBinding;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MerchantProductsFragment extends Fragment{
    private FragmentMerchantListBinding binding;private ActionAdapter adapter;
    private final androidx.activity.result.ActivityResultLauncher<Intent> editor=registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),result->{if(binding!=null)load(false);});
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,@Nullable ViewGroup container,@Nullable Bundle state){binding=FragmentMerchantListBinding.inflate(inflater,container,false);binding.title.setText("商品管理");binding.subtitle.setText("创建绘本并提交管理员审核");binding.add.setText("＋ 新增商品");binding.add.setOnClickListener(v->editor.launch(new Intent(requireContext(),AddProductActivity.class)));adapter=new ActionAdapter(this::submit);binding.list.setLayoutManager(new LinearLayoutManager(requireContext()));binding.list.setAdapter(adapter);binding.getRoot().setOnRefreshListener(()->load(false));load();return binding.getRoot();}
    private void load(){load(true);}
    private void load(boolean showError){new ApiClient(requireContext()).get("merchant/products",result->{if(binding==null)return;binding.loading.setVisibility(View.GONE);binding.getRoot().setRefreshing(false);List<ActionAdapter.Row> rows=new ArrayList<>();JSONArray data=result.optJSONArray("data");if(data!=null)for(int i=0;i<data.length();i++){JSONObject item=data.optJSONObject(i);if(item==null)continue;String status=item.optString("status");rows.add(new ActionAdapter.Row(item.optString("id"),item.optString("name"),zh(status)+" · "+String.format(Locale.CHINA,"¥%.2f",item.optInt("priceCents")/100.0)+" · 库存 "+item.optInt("stock"),"管理",true,item.optString("coverUrl"),item.optString("name")));}adapter.submit(rows);binding.empty.setVisibility(rows.isEmpty()?View.VISIBLE:View.GONE);},message->{if(binding!=null){binding.loading.setVisibility(View.GONE);binding.getRoot().setRefreshing(false);if(showError||adapter.getItemCount()==0)error(message);}});}
    private void submit(ActionAdapter.Row row){editor.launch(new Intent(requireContext(),ManageProductActivity.class).putExtra("product_id",row.id()));}
    private String zh(String value){return switch(value){case "DRAFT"->"草稿";case "PENDING_REVIEW"->"审核中";case "ONLINE"->"已上架";case "OFFLINE"->"已下架";default->value;};}
    private void error(String error){binding.loading.setVisibility(View.GONE);binding.getRoot().setRefreshing(false);Toast.makeText(requireContext(),error,Toast.LENGTH_SHORT).show();}
    @Override public void onDestroyView(){super.onDestroyView();binding=null;}
}
