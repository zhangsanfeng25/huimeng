package com.huimeng.bookshop.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.huimeng.bookshop.core.ApiClient;
import com.huimeng.bookshop.databinding.FragmentCommunityBinding;
import com.huimeng.bookshop.model.CommunityPost;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class CommunityFragment extends Fragment{
    private FragmentCommunityBinding binding;private CommunityAdapter adapter;
    private final androidx.activity.result.ActivityResultLauncher<Intent> publisher=registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),result->{if(binding!=null)load(false);});
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,@Nullable ViewGroup container,@Nullable Bundle state){binding=FragmentCommunityBinding.inflate(inflater,container,false);adapter=new CommunityAdapter(item->startActivity(new Intent(requireContext(),CommunityDetailActivity.class).putExtra("post_id",item.id())));binding.list.setLayoutManager(new LinearLayoutManager(requireContext()));binding.list.setAdapter(adapter);binding.publish.setOnClickListener(v->publisher.launch(new Intent(requireContext(),PublishPostActivity.class)));return binding.getRoot();}
    @Override public void onViewCreated(@NonNull View view,@Nullable Bundle state){super.onViewCreated(view,state);binding.getRoot().setOnRefreshListener(()->load(false));load(true);}
    private void load(boolean showLoading){if(showLoading)binding.loading.setVisibility(View.VISIBLE);new ApiClient(requireContext()).get("community/posts",result->{if(binding==null)return;binding.loading.setVisibility(View.GONE);binding.getRoot().setRefreshing(false);List<CommunityPost> rows=new ArrayList<>();JSONArray data=result.optJSONArray("data");if(data!=null)for(int i=0;i<data.length();i++){JSONObject item=data.optJSONObject(i);if(item!=null)rows.add(CommunityPost.from(item));}adapter.submit(rows);binding.empty.setText("暂时没有社区内容");binding.empty.setVisibility(rows.isEmpty()?View.VISIBLE:View.GONE);},error->{if(binding==null)return;binding.loading.setVisibility(View.GONE);binding.getRoot().setRefreshing(false);if(showLoading){binding.empty.setText(error);binding.empty.setVisibility(View.VISIBLE);}});}
    @Override public void onDestroyView(){super.onDestroyView();binding=null;}
}
