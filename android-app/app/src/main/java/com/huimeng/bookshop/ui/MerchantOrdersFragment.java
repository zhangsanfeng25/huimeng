package com.huimeng.bookshop.ui;

import android.os.*;
import android.content.Intent;
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

public class MerchantOrdersFragment extends Fragment implements LocalDataRefresh {
    private FragmentMerchantListBinding binding;
    private ActionAdapter adapter;
    private MerchantListControls controls;
    private JSONArray orders=new JSONArray();
    private Bundle filters;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean loading;
    private int request;
    private final Runnable refresh=new Runnable(){@Override public void run(){if(binding!=null&&isResumed()&&!isHidden()){load(false);handler.postDelayed(this,5000);}}};
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,@Nullable ViewGroup container,@Nullable Bundle state){
        binding=FragmentMerchantListBinding.inflate(inflater,container,false);binding.title.setText("订单管理");binding.subtitle.setText("查找订单与待办，状态变更同步买家");binding.add.setVisibility(View.GONE);
        adapter=new ActionAdapter(this::advance,this::openDetail);binding.list.setLayoutManager(new LinearLayoutManager(requireContext()));binding.list.setAdapter(adapter);
        controls=new MerchantListControls(binding,false,state==null?filters:state.getBundle("filters"),this::render);
        binding.getRoot().setOnRefreshListener(()->load(false));load(true);return binding.getRoot();
    }
    public void applyStatusFilter(String status){if(controls!=null)controls.reset(status);}
    private void openDetail(ActionAdapter.Row row){startActivity(new Intent(requireContext(),MerchantOrderDetailActivity.class).putExtra("order_id",row.id()));}
    @Override public void onResume(){super.onResume();handler.removeCallbacks(refresh);load(false);handler.postDelayed(refresh,5000);}
    @Override public void onPause(){handler.removeCallbacks(refresh);super.onPause();}
    private void load(boolean showLoading){
        if(binding==null||loading)return;loading=true;int current=++request;if(showLoading)binding.loading.setVisibility(View.VISIBLE);
        new ApiClient(requireContext()).get("merchant/orders",result->{
            if(binding==null||current!=request)return;loading=false;binding.loading.setVisibility(View.GONE);binding.getRoot().setRefreshing(false);JSONArray data=result.optJSONArray("data");orders=data==null?new JSONArray():data;render();
        },message->{if(binding==null||current!=request)return;loading=false;binding.loading.setVisibility(View.GONE);binding.getRoot().setRefreshing(false);
            if(adapter.getItemCount()==0){binding.empty.setText(message);binding.empty.setVisibility(View.VISIBLE);}if(isResumed())Toast.makeText(requireContext(),message,Toast.LENGTH_SHORT).show();
        });
    }
    private void render(){
        if(binding==null||controls==null)return;List<ActionAdapter.Row> rows=new ArrayList<>();
        for(int i=0;i<orders.length();i++){
            JSONObject item=orders.optJSONObject(i);if(item==null||!MerchantWork.orderMatches(item,controls.query(),controls.status()))continue;
            String logistics=item.optString("logistics_status",item.optString("status")),action=action(logistics);
            if(AfterSaleState.blocked(item)||item.has("pendingAction"))action="";
            String id=OrderPresentation.platformId(item);if(id.isEmpty())id=item.optString("id");
            String note="买家 "+item.optString("buyer")+" · "+String.format(Locale.CHINA,"¥%.2f",item.optInt("total_cents")/100.0)+"\n"+AfterSaleState.describe(item,zh(logistics))+" · "+id;
            if(item.has("pendingAction"))note+="\n操作待联网确认";
            rows.add(new ActionAdapter.Row(item.optString("id"),item.optString("productName","绘本订单"),note,action,!action.isEmpty(),item.optString("coverUrl"),item.optString("productName","绘本订单")));
        }
        adapter.submit(rows);binding.resultCount.setText("显示 "+rows.size()+" / "+orders.length()+" 笔订单");binding.empty.setText(orders.length()==0?"暂无订单，新订单会显示在这里":"没有符合条件的订单，可点击重置清除筛选");binding.empty.setVisibility(rows.isEmpty()?View.VISIBLE:View.GONE);
    }
    private void advance(ActionAdapter.Row row){
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext()).setMessage("执行“"+row.action()+"”？这是演示物流操作，将同步更新买家订单状态。").setNegativeButton("暂不",null)
            .setPositiveButton("确认",(dialog,which)->new ApiClient(requireContext()).post("merchant/orders/"+row.id()+"/advance",new JSONObject(),result->{if(!isAdded()||binding==null)return;Toast.makeText(requireContext(),result.optBoolean("pending")?"操作已保存，待联网确认":"订单已更新为："+zh(result.optString("toStatus")),Toast.LENGTH_LONG).show();load(false);},message->{if(isAdded())Toast.makeText(requireContext(),message,Toast.LENGTH_SHORT).show();})).show();
    }
    private String action(String value){return switch(value){case "PREPARING"->"完成出库准备";case "READY_TO_SHIP"->"确认发货";case "SHIPPED"->"更新为运输中";case "IN_TRANSIT"->"更新为派送中";default->"";};}
    private String zh(String value){return switch(value){case "PENDING_PAYMENT","WAITING_PAYMENT"->"待付款";case "PAID","PREPARING"->"准备出库";case "READY_TO_SHIP"->"待发货";case "SHIPPED"->"已发货";case "IN_TRANSIT"->"运输中";case "OUT_FOR_DELIVERY"->"派送中";case "COMPLETED"->"已完成";case "CANCELLED"->"已取消";default->value;};}
    @Override public void onSaveInstanceState(@NonNull Bundle state){if(controls!=null)state.putBundle("filters",controls.save());super.onSaveInstanceState(state);}
    @Override public void onDestroyView(){if(controls!=null)filters=controls.save();controls=null;handler.removeCallbacks(refresh);request++;loading=false;binding=null;super.onDestroyView();}
    @Override public void refreshLocalData(){if(binding!=null&&isAdded())load(false);}
}
