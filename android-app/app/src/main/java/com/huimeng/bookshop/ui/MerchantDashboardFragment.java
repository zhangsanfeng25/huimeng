package com.huimeng.bookshop.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.huimeng.bookshop.core.ApiClient;
import com.huimeng.bookshop.core.ServiceClient;
import com.huimeng.bookshop.core.SessionStore;
import com.huimeng.bookshop.databinding.FragmentMerchantDashboardBinding;
import org.json.*;

public class MerchantDashboardFragment extends Fragment implements LocalDataRefresh {
    private FragmentMerchantDashboardBinding binding;
    private int pending,generation,productsTotal=-1,ordersTotal=-1,reviewTotal=-1,chats=-1,notices=-1;
    private boolean failed,cached;
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,@Nullable ViewGroup container,@Nullable Bundle state){
        binding=FragmentMerchantDashboardBinding.inflate(inflater,container,false);binding.name.setText("你好，"+new SessionStore(requireContext()).name()+" · 先处理今天的待办");
        binding.newProduct.setOnClickListener(v->startActivity(new Intent(requireContext(),AddProductActivity.class)));
        binding.todoShip.setOnClickListener(v->((MainActivity)requireActivity()).showMerchantOrders("TO_SHIP"));
        binding.todoProducts.setOnClickListener(v->((MainActivity)requireActivity()).showMerchantProducts("ACTION"));
        binding.todoMessages.setOnClickListener(v->((MainActivity)requireActivity()).selectTab(4));
        binding.todoSale.setOnClickListener(v->startActivity(new Intent(requireContext(),ServiceActivity.class).putExtra("kind","after-sales").putExtra("filter","待我处理")));
        binding.allProducts.setOnClickListener(v->((MainActivity)requireActivity()).showMerchantProducts(""));
        binding.allOrders.setOnClickListener(v->((MainActivity)requireActivity()).showMerchantOrders(""));
        binding.getRoot().setOnRefreshListener(this::load);load();return binding.getRoot();
    }
    @Override public void onResume(){super.onResume();load();}
    private boolean current(int request){return binding!=null&&generation==request;}
    private void load(){
        if(binding==null||pending>0)return;pending=5;failed=false;cached=false;final int request=++generation;
        ApiClient api=new ApiClient(requireContext());ServiceClient services=new ServiceClient(requireContext());
        java.util.function.Consumer<String> error=message->{if(current(request)){failed=true;complete();}};
        api.get("merchant/products",r->{if(!current(request))return;JSONArray rows=r.optJSONArray("data");int total=0,action=0,review=0;
            if(rows!=null)for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row==null)continue;total++;if(MerchantWork.productNeedsAction(row))action++;if(MerchantWork.reviewPending(row))review++;}
            productsTotal=total;reviewTotal=review;binding.todoProducts.setText(action+"\n商品待修改 / 提交");complete();
        },error);
        api.get("merchant/orders",r->{if(!current(request))return;JSONArray rows=r.optJSONArray("data");int total=0,ship=0;
            if(rows!=null)for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row==null)continue;total++;if(MerchantWork.readyToShip(row))ship++;}
            ordersTotal=total;binding.todoShip.setText(ship+"\n待发货");complete();
        },error);
        services.get("service/after-sales",r->{if(!current(request))return;JSONArray rows=r.optJSONArray("data");int count=0;
            if(rows!=null)for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row!=null&&MerchantWork.saleNeedsAction(row.optString("status")))count++;}
            cached|=r.optBoolean("cached");binding.todoSale.setText(count+"\n待处理售后");complete();
        },error);
        services.get("service/conversations",r->{if(!current(request))return;chats=unread(r.optJSONArray("data"),false);cached|=r.optBoolean("cached");messages();complete();},error);
        api.get("me/notifications",r->{if(!current(request))return;notices=unread(r.optJSONArray("data"),true);messages();complete();},error);
    }
    private int unread(JSONArray rows,boolean system){int count=0;if(rows!=null)for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row!=null)count+=system?(row.optInt("is_read")==0?1:0):Math.max(0,row.optInt("unread"));}return count;}
    private void messages(){if(chats>=0&&notices>=0){int count=chats+notices;binding.todoMessages.setText(count+"\n未读消息");if(getActivity() instanceof MainActivity main)main.setMerchantMessageUnread(chats,notices);}}
    private void complete(){
        binding.summary.setText("商品 "+number(productsTotal)+" 件    ·    订单 "+number(ordersTotal)+" 笔\n审核中 / 待同步 "+number(reviewTotal)+" 件");
        if(--pending<=0){binding.getRoot().setRefreshing(false);binding.dataHint.setText(failed?"部分数据未更新，下拉重试":cached?"部分待办显示已保存记录，联网后更新":"待办已更新 · 点击数字直达对应记录");}
    }
    private String number(int count){return count<0?"—":String.valueOf(count);}
    @Override public void onDestroyView(){generation++;pending=0;binding=null;super.onDestroyView();}
    @Override public void refreshLocalData(){load();}
}
