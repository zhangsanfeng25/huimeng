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
import com.huimeng.bookshop.databinding.FragmentMerchantProfileBinding;

public class MerchantProfileFragment extends Fragment implements LocalDataRefresh{
    private FragmentMerchantProfileBinding binding;
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,@Nullable ViewGroup container,@Nullable Bundle state){
        binding=FragmentMerchantProfileBinding.inflate(inflater,container,false);
        binding.avatar.setOnClickListener(v->open(EditAccountActivity.class));
        binding.publish.setOnClickListener(v->open(PublishPostActivity.class));
        binding.notifications.setOnClickListener(v->open(NotificationsActivity.class));
        binding.settings.setOnClickListener(v->open(SettingsActivity.class));
        ServiceEntries.menuBefore(binding.settings,"售后管理",com.huimeng.bookshop.R.drawable.ic_orders,()->ServiceActivity.open(requireContext(),"after-sales"));
        ServiceEntries.menuBefore(binding.settings,"买家消息",com.huimeng.bookshop.R.drawable.ic_community,()->ServiceActivity.open(requireContext(),"conversations"));
        ServiceEntries.menuBefore(binding.settings,"意见反馈",com.huimeng.bookshop.R.drawable.ic_review,()->ServiceActivity.open(requireContext(),"feedback"));
        binding.switchRole.setOnClickListener(v->open(RoleSwitchActivity.class));
        binding.editAccount.setOnClickListener(v->open(EditAccountActivity.class));
        binding.logout.setOnClickListener(v->((MainActivity)requireActivity()).logout());
        for(View entry:new View[]{binding.publish,binding.notifications,binding.settings,binding.switchRole,binding.editAccount})TapMotion.attach(entry);
        binding.getRoot().setOnRefreshListener(this::refreshProfile);renderProfile();return binding.getRoot();
    }
    private void open(Class<?> target){startActivity(new Intent(requireContext(),target));}
    @Override public void onResume(){super.onResume();renderProfile();}
    private void renderProfile(){if(binding!=null){SessionStore profile=new SessionStore(requireContext());binding.name.setText(profile.name());String url=profile.avatarUrl();DefaultAvatars.show(binding.avatar,url);}}
    private void refreshProfile(){new ApiClient(requireContext()).get("auth/me",result->{if(binding==null)return;org.json.JSONObject user=result.optJSONObject("user");if(user!=null){SessionStore profile=new SessionStore(requireContext());profile.updateProfile(user.optString("username",profile.username()),user.optString("name",profile.name()));profile.setAvatarUrl(user.optString("avatarUrl",profile.avatarUrl()));renderProfile();}binding.getRoot().setRefreshing(false);},error->{if(binding!=null){binding.getRoot().setRefreshing(false);android.widget.Toast.makeText(requireContext(),error,android.widget.Toast.LENGTH_SHORT).show();}});}
    @Override public void onDestroyView(){super.onDestroyView();binding=null;}
    @Override public void refreshLocalData(){if(binding!=null && isAdded()){refreshProfile();}}
}
