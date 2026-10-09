package com.huimeng.bookshop.ui;

import android.os.Bundle;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
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

public class MerchantOrdersFragment extends Fragment{
    private FragmentMerchantListBinding binding;private ActionAdapter adapter;private final Handler handler=new Handler(Looper.getMainLooper());private boolean loading;
    private final Runnable refresh=new Runnable(){@Override public void run(){if(binding!=null&&isResumed()&&!isHidden()){load(false);handler.postDelayed(this,5000);}}};
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,@Nullable ViewGroup container,@Nullable Bundle state){binding=FragmentMerchantListBinding.inflate(inflater,container,false);binding.title.setText("订单管理");binding.subtitle.setText("物流状态自动同步");binding.add.setVisibility(View.GONE);adapter=new ActionAdapter(this::advance,this::openDetail);binding.list.setLayoutManager(new LinearLayoutManager(requireContext()));binding.list.setAdapter(adapter);binding.getRoot().setOnRefreshListener(()->load(false));load(true);return binding.getRoot();}
    private void openDetail(ActionAdapter.Row row){startActivity(new Intent(requireContext(),MerchantOrderDetailActivity.class).putExtra("order_id",row.id()));}
    @Override public void onResume(){super.onResume();handler.removeCallbacks(refresh);handler.postDelayed(refresh,5000);}
    @Override public void onPause(){handler.removeCallbacks(refresh);super.onPause();}
    private void load(boolean showLoading){if(binding==null||loading)return;loading=true;if(showLoading)binding.loading.setVisibility(View.VISIBLE);new ApiClient(requireContext()).get("merchant/orders",result->{loading=false;if(binding==null)return;binding.loading.setVisibility(View.GONE);binding.getRoot().setRefreshing(false);List<ActionAdapter.Row> rows=new ArrayList<>();JSONArray data=result.optJSONArray("data");if(data!=null)for(int i=0;i<data.length();i++){JSONObject item=data.optJSONObject(i);if(item==null)continue;String logistics=item.optString("logistics_status",item.optString("status")),action=action(logistics);rows.add(new ActionAdapter.Row(item.optString("id"),item.optString("productName","绘本订单"),"买家 "+item.optString("buyer")+" · "+zh(logistics)+" · "+String.format(Locale.CHINA,"¥%.2f",item.optInt("total_cents")/100.0),action,!action.isEmpty(),item.optString("coverUrl"),item.optString("productName","绘本订单")));}adapter.submit(rows);binding.empty.setVisibility(rows.isEmpty()?View.VISIBLE:View.GONE);},this::error);}
    private void advance(ActionAdapter.Row row){new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
        .setMessage("执行“"+row.action()+"”？这是演示物流操作，将同步更新买家订单状态。")
        .setNegativeButton("暂不",null).setPositiveButton("确认",(dialog,which)->new ApiClient(requireContext()).post("merchant/orders/"+row.id()+"/advance",new JSONObject(),result->{Toast.makeText(requireContext(),"订单已更新为："+zh(result.optString("toStatus")),Toast.LENGTH_LONG).show();load(false);},this::error)).show();}
    private String action(String value){return switch(value){case "PREPARING"->"完成出库准备";case "READY_TO_SHIP"->"确认发货";case "SHIPPED"->"更新为运输中";case "IN_TRANSIT"->"更新为派送中";default->"";};}
    private String zh(String value){return switch(value){case "PENDING_PAYMENT","WAITING_PAYMENT"->"待付款";case "PAID","PREPARING"->"准备出库";case "READY_TO_SHIP"->"待发货";case "SHIPPED"->"已发货";case "IN_TRANSIT"->"运输中";case "OUT_FOR_DELIVERY"->"派送中";case "COMPLETED"->"已完成";case "CANCELLED"->"已取消";default->value;};}
    private void error(String error){loading=false;if(binding==null)return;binding.loading.setVisibility(View.GONE);binding.getRoot().setRefreshing(false);if(isAdded()&&isResumed())Toast.makeText(requireContext(),error,Toast.LENGTH_SHORT).show();}
    @Override public void onDestroyView(){handler.removeCallbacks(refresh);super.onDestroyView();binding=null;}
}
