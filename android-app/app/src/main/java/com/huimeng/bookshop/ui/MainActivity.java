package com.huimeng.bookshop.ui;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.Lifecycle;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.huimeng.bookshop.R;
import com.huimeng.bookshop.core.ApiClient;
import com.huimeng.bookshop.core.SessionStore;
import com.huimeng.bookshop.core.WallpaperStore;
import com.huimeng.bookshop.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding; private SessionStore session;
    private int selectedTab=1;
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);session=new SessionStore(this);
        if(!session.authenticated()&&!session.accountId().isEmpty()){session.clear();startActivity(new Intent(this,WelcomeActivity.class));finish();return;}
        if(!session.hasLocalSpace()&&session.token().isEmpty()){startActivity(new Intent(this,WelcomeActivity.class));finish();return;}
        binding=ActivityMainBinding.inflate(getLayoutInflater());setContentView(binding.getRoot());
        com.huimeng.bookshop.core.sync.SyncStatusStore.observe(this,status->refreshVisibleLocalData());
        buildNavigation(binding.bottomNav,session.role());
        binding.bottomNav.setBackgroundTintList(null);
        binding.bottomNav.setItemActiveIndicatorColor(android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(this,R.color.nav_indicator)));
        int initial=state==null?(getIntent().getBooleanExtra("show_guest_profile",false)?4:1):state.getInt("selected_tab",1);
        binding.bottomNav.setSelectedItemId(tabId(initial));
        binding.bottomNav.setOnItemSelectedListener(item->{show(tabPosition(item.getItemId()));return true;});
        binding.bottomNav.setOnItemReselectedListener(item->{});
        show(initial);
    }
    private void buildNavigation(BottomNavigationView nav,String role){nav.getMenu().clear();if("MERCHANT".equals(role)){nav.getMenu().add(0,R.id.nav_first,0,"概览").setIcon(R.drawable.ic_home);nav.getMenu().add(0,R.id.nav_second,1,"商品").setIcon(R.drawable.ic_products);nav.getMenu().add(0,R.id.nav_third,2,"订单").setIcon(R.drawable.ic_orders);nav.getMenu().add(0,R.id.nav_fourth,3,"社区").setIcon(R.drawable.ic_community);nav.getMenu().add(0,R.id.nav_fifth,4,"我的").setIcon(R.drawable.ic_user);}else{nav.getMenu().add(0,R.id.nav_first,0,"首页").setIcon(R.drawable.ic_home);nav.getMenu().add(0,R.id.nav_second,1,"社区").setIcon(R.drawable.ic_community);nav.getMenu().add(0,R.id.nav_third,2,"消息").setIcon(R.drawable.ic_message);nav.getMenu().add(0,R.id.nav_fourth,3,"我的").setIcon(R.drawable.ic_user);}}
    private static final int[] TAB_IDS={R.id.nav_first,R.id.nav_second,R.id.nav_third,R.id.nav_fourth,R.id.nav_fifth};
    private int tabPosition(int id){for(int i=0;i<TAB_IDS.length;i++)if(TAB_IDS[i]==id)return i+1;return 1;}
    private int tabId(int position){return TAB_IDS[Math.max(0,Math.min(TAB_IDS.length-1,position-1))];}
    private Fragment createTab(int item){
        androidx.fragment.app.Fragment fragment;
        if("MERCHANT".equals(session.role())&&item==1)fragment=new MerchantDashboardFragment();
        else if("MERCHANT".equals(session.role())&&item==2)fragment=new MerchantProductsFragment();
        else if("MERCHANT".equals(session.role())&&item==3)fragment=new MerchantOrdersFragment();
        else if("MERCHANT".equals(session.role())&&item==4)fragment=new CommunityFragment();
        else if("MERCHANT".equals(session.role())&&item==5)fragment=new MerchantProfileFragment();
        else if("USER".equals(session.role())&&item==1)fragment=new HomeFragment();
        else if("USER".equals(session.role())&&item==2)fragment=new CommunityFragment();
        else if("USER".equals(session.role())&&item==3)fragment=new NotificationFragment();
        else if("USER".equals(session.role())&&item==4)fragment=new ProfileFragment();
        else fragment=SectionFragment.newInstance(session.role(),item);
        return fragment;
    }
    private void show(int item){
        if(getSupportFragmentManager().isStateSaved())return;selectedTab=item;
        FragmentTransaction transaction=getSupportFragmentManager().beginTransaction().setReorderingAllowed(true);
        boolean added=false;
        int count="MERCHANT".equals(session.role())?5:4;
        for(int position=1;position<=count;position++) {
            String tag="tab:"+session.role()+":"+position;
            Fragment fragment=getSupportFragmentManager().findFragmentByTag(tag);
            if(fragment==null){fragment=createTab(position);transaction.add(binding.content.getId(),fragment,tag);added=true;}
            if(position==item){transaction.show(fragment).setMaxLifecycle(fragment,Lifecycle.State.RESUMED).setPrimaryNavigationFragment(fragment);}
            else transaction.hide(fragment).setMaxLifecycle(fragment,Lifecycle.State.STARTED);
        }
        if(added)transaction.runOnCommit(()->new WallpaperStore(this).apply(this));
        transaction.commitNow();
        refreshVisibleLocalData();
    }
    @Override protected void onSaveInstanceState(Bundle state){state.putInt("selected_tab",selectedTab);super.onSaveInstanceState(state);}
    public void selectTab(int item){if(binding!=null)binding.bottomNav.setSelectedItemId(tabId(item));}
    @Override protected void onResume(){super.onResume();refreshVisibleLocalData();}
    private void refreshVisibleLocalData(){if(binding==null||session==null||!session.hasLocalSpace())return;Fragment fragment=getSupportFragmentManager().getPrimaryNavigationFragment();if(fragment instanceof LocalDataRefresh)((LocalDataRefresh)fragment).refreshLocalData();}
    public void logout(){if(!session.token().isEmpty())new ApiClient(this).post("auth/logout",new org.json.JSONObject(),result->{},error->{});
        com.huimeng.bookshop.core.sync.SyncCoordinator.get(this).stop();session.clear();
        startActivity(new Intent(this,WelcomeActivity.class).putExtra("show_guest_profile",true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK|Intent.FLAG_ACTIVITY_NEW_TASK));finish();}
}
