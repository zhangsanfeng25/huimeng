package com.huimeng.bookshop.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.huimeng.bookshop.core.ApiClient;
import com.huimeng.bookshop.core.SessionStore;
import com.huimeng.bookshop.databinding.FragmentSectionBinding;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SectionFragment extends Fragment {
    private FragmentSectionBinding binding;private TextAdapter adapter;
    public static SectionFragment newInstance(String role,int section){SectionFragment fragment=new SectionFragment();Bundle args=new Bundle();args.putString("role",role);args.putInt("section",section);fragment.setArguments(args);return fragment;}
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,@Nullable ViewGroup container,@Nullable Bundle state){binding=FragmentSectionBinding.inflate(inflater,container,false);adapter=new TextAdapter();binding.list.setLayoutManager(new LinearLayoutManager(requireContext()));binding.list.setAdapter(adapter);load();return binding.getRoot();}
    private void load(){String role=requireArguments().getString("role","USER");int section=requireArguments().getInt("section",1);String path,title,subtitle;if("MERCHANT".equals(role)){if(section==2){title="商品管理";subtitle="上架、编辑、预览与提交审核";path="merchant/products";}else if(section==3){title="订单管理";subtitle="查看已付款订单并模拟发货";path="merchant/orders";}else if(section==4){title="我的店铺";subtitle="商家资料、社区发布与角色切换";path="me/notifications";}else{title="商家工作台";subtitle="商品、订单与待处理事项";path="merchant/products";}}else{if(section==2){title="社区";subtitle="发现真实的亲子共读生活";path="community/posts";}else if(section==3){title="消息";subtitle="审核、订单与系统通知";path="me/notifications";}else if(section==4){title="我的";subtitle="订单、收藏、地址与设置";path="me/orders";}else{title="你好，"+new SessionStore(requireContext()).name();subtitle="愿今天也有一本好绘本陪伴你";path="catalog/products";}}
        binding.title.setText(title);binding.subtitle.setText(subtitle);new ApiClient(requireContext()).get(path,result->{binding.loading.setVisibility(View.GONE);adapter.submit(map(result.optJSONArray("data")));},error->{binding.loading.setVisibility(View.GONE);adapter.submit(Collections.singletonList(new TextAdapter.Row("暂时无法加载",error)));});}
    private List<TextAdapter.Row> map(JSONArray array){List<TextAdapter.Row> rows=new ArrayList<>();if(array==null||array.length()==0){rows.add(new TextAdapter.Row("暂无内容","完成相关操作后，数据会自动显示在这里"));return rows;}for(int i=0;i<array.length();i++){JSONObject item=array.optJSONObject(i);if(item==null)continue;String primary=item.optString("name",item.optString("title",item.optString("id","内容")));String secondary=item.optString("summary",item.optString("status",item.optString("shop",item.optString("content","点击查看详情"))));rows.add(new TextAdapter.Row(primary,secondary));}return rows;}
    @Override public void onDestroyView(){super.onDestroyView();binding=null;}
}
