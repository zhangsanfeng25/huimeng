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
import com.huimeng.bookshop.databinding.FragmentProfileBinding;

public class ProfileFragment extends Fragment implements LocalDataRefresh {
    private FragmentProfileBinding binding;
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,@Nullable ViewGroup container,@Nullable Bundle state){
        binding=FragmentProfileBinding.inflate(inflater,container,false);
        binding.name.setText(new SessionStore(requireContext()).name());
        binding.avatar.setOnClickListener(v->open(EditAccountActivity.class));
        binding.name.setMinHeight(Math.round(48*getResources().getDisplayMetrics().density));binding.name.setOnClickListener(v->open(EditAccountActivity.class));
        binding.cart.setOnClickListener(v->open(CartActivity.class));
        binding.orders.setOnClickListener(v->orders(null));
        binding.pendingPayment.setOnClickListener(v->orders("PENDING_PAYMENT"));
        binding.pendingShipment.setOnClickListener(v->orders("PAID"));
        binding.pendingReceipt.setOnClickListener(v->orders("SHIPPED"));
        binding.pendingReview.setOnClickListener(v->orders("COMPLETED"));
        binding.favorites.setOnClickListener(v->account(()->startActivity(new Intent(requireContext(),DataListActivity.class)
            .putExtra("title","我的收藏").putExtra("subtitle","收藏的绘本都会保存在这里").putExtra("path","me/favorites"))));
        binding.addresses.setOnClickListener(v->open(AddressesActivity.class));
        binding.settings.setOnClickListener(v->open(SettingsActivity.class));
        ServiceEntries.menuBefore(binding.settings,"退款 / 售后",com.huimeng.bookshop.R.drawable.ic_orders,()->account(()->ServiceActivity.open(requireContext(),"after-sales")));
        ServiceEntries.menuBefore(binding.settings,"意见反馈",com.huimeng.bookshop.R.drawable.ic_review,()->account(()->ServiceActivity.open(requireContext(),"feedback")));
        binding.merchantApplication.setOnClickListener(v->open(MerchantApplicationActivity.class));
        binding.editAccount.setOnClickListener(v->open(EditAccountActivity.class));
        binding.logout.setOnClickListener(v->((MainActivity)requireActivity()).logout());
        for(View entry:new View[]{binding.pendingPayment,binding.pendingShipment,binding.pendingReceipt,binding.pendingReview,
                binding.cart,binding.favorites,binding.addresses,binding.settings,binding.merchantApplication,binding.editAccount})TapMotion.attach(entry);
        binding.getRoot().setOnRefreshListener(this::refreshProfile);renderProfile();return binding.getRoot();
    }
    private void account(Runnable action){if(new SessionStore(requireContext()).authenticated())action.run();else startActivity(new Intent(requireContext(),LoginActivity.class));}
    private void open(Class<?> activity){if(activity==SettingsActivity.class)startActivity(new Intent(requireContext(),activity));else account(()->startActivity(new Intent(requireContext(),activity)));}
    private void orders(String status){account(()->{Intent intent=new Intent(requireContext(),OrdersActivity.class);if(status!=null)intent.putExtra("status",status);startActivity(intent);});}
    @Override public void onResume(){super.onResume();renderProfile();}
    private void renderProfile(){if(binding!=null){SessionStore profile=new SessionStore(requireContext());boolean logged=profile.authenticated();binding.name.setText(logged?profile.name():"登录");binding.name.setContentDescription(logged?"编辑账户":"登录账户");binding.logout.setVisibility(logged?View.VISIBLE:View.GONE);binding.editAccount.setText(logged?"编辑账户":"登录账户");DefaultAvatars.show(binding.avatar,logged?profile.avatarUrl():"");}}
    private void refreshProfile(){if(!new SessionStore(requireContext()).authenticated()){renderProfile();if(binding!=null)binding.getRoot().setRefreshing(false);return;}new ApiClient(requireContext()).get("auth/me",result->{if(binding==null)return;org.json.JSONObject user=result.optJSONObject("user");if(user!=null){SessionStore profile=new SessionStore(requireContext());profile.updateProfile(user.optString("username",profile.username()),user.optString("name",profile.name()));profile.setAvatarUrl(user.optString("avatarUrl",profile.avatarUrl()));renderProfile();}binding.getRoot().setRefreshing(false);},error->{if(binding!=null){binding.getRoot().setRefreshing(false);android.widget.Toast.makeText(requireContext(),error,android.widget.Toast.LENGTH_SHORT).show();}});}
    @Override public void onDestroyView(){super.onDestroyView();binding=null;}
    @Override public void refreshLocalData(){if(binding!=null && isAdded()){refreshProfile();}}
}
