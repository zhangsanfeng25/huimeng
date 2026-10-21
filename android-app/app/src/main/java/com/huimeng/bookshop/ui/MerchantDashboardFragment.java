package com.huimeng.bookshop.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.huimeng.bookshop.core.ApiClient;
import com.huimeng.bookshop.core.SessionStore;
import com.huimeng.bookshop.databinding.FragmentMerchantDashboardBinding;
import org.json.JSONArray;

public class MerchantDashboardFragment extends Fragment implements LocalDataRefresh{
    private FragmentMerchantDashboardBinding binding;
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,@Nullable ViewGroup container,@Nullable Bundle state){binding=FragmentMerchantDashboardBinding.inflate(inflater,container,false);binding.name.setText("你好，"+new SessionStore(requireContext()).name());
        binding.newProduct.setOnClickListener(v->startActivity(new Intent(requireContext(),AddProductActivity.class)));
        binding.publishNote.setOnClickListener(v->startActivity(new Intent(requireContext(),PublishPostActivity.class)));
        var service=ServiceEntries.before((View)binding.newProduct.getParent(),"待处理售后",()->ServiceActivity.open(requireContext(),"after-sales"));
        new com.huimeng.bookshop.core.ServiceClient(requireContext()).get("service/after-sales",r->{if(binding==null)return;JSONArray rows=r.optJSONArray("data");int count=0;if(rows!=null)for(int i=0;i<rows.length();i++)if("MERCHANT_REVIEW".equals(rows.optJSONObject(i).optString("status")))count++;service.setText("待处理售后 · "+count);},e->{});
        binding.statProducts.getRoot().setOnClickListener(v->((MainActivity)requireActivity()).selectTab(2));
        binding.statPending.getRoot().setOnClickListener(v->((MainActivity)requireActivity()).selectTab(2));
        binding.statOrders.getRoot().setOnClickListener(v->((MainActivity)requireActivity()).selectTab(3));
        binding.statShip.getRoot().setOnClickListener(v->((MainActivity)requireActivity()).selectTab(3));
        binding.getRoot().setOnRefreshListener(this::load);load();return binding.getRoot();}
    private int pending;
    private void completed(){if(--pending<=0&&binding!=null)binding.getRoot().setRefreshing(false);}
    private void load(){if(pending>0)return;pending=2;new ApiClient(requireContext()).get("merchant/products",result->{if(binding==null)return;JSONArray data=result.optJSONArray("data");int total=data==null?0:data.length(),pending=0;if(data!=null)for(int i=0;i<data.length();i++)if("PENDING_REVIEW".equals(data.optJSONObject(i).optString("status")))pending++;binding.statProducts.products.setText(String.valueOf(total));binding.statPending.pending.setText(String.valueOf(pending));completed();},error->completed());new ApiClient(requireContext()).get("merchant/orders",result->{if(binding==null)return;JSONArray data=result.optJSONArray("data");int total=data==null?0:data.length(),ship=0;if(data!=null)for(int i=0;i<data.length();i++)if("PAID".equals(data.optJSONObject(i).optString("status")))ship++;binding.statOrders.orders.setText(String.valueOf(total));binding.statShip.toShip.setText(String.valueOf(ship));completed();},error->completed());}
    @Override public void onDestroyView(){pending=0;super.onDestroyView();binding=null;}
    @Override public void refreshLocalData(){if(binding!=null && isAdded()){load();}}
}
