package com.huimeng.bookshop.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import com.huimeng.bookshop.R;
import com.huimeng.bookshop.core.ApiClient;
import com.huimeng.bookshop.databinding.ActivityUserProfileBinding;
import com.huimeng.bookshop.model.CommunityPost;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public final class UserProfileActivity extends AppCompatActivity {
    private ActivityUserProfileBinding binding;
    private AuthorPostsAdapter adapter;
    private String userId;
    private boolean loading;
    @Override protected void onCreate(Bundle state){super.onCreate(state);binding=ActivityUserProfileBinding.inflate(getLayoutInflater());setContentView(binding.getRoot());
        userId=getIntent().getStringExtra("user_id");if(userId==null||userId.isEmpty()){finish();return;}
        binding.back.setOnClickListener(v->finish());
        adapter=new AuthorPostsAdapter(post->startActivity(new Intent(this,CommunityDetailActivity.class).putExtra("post_id",post.id())));
        binding.posts.setLayoutManager(new GridLayoutManager(this,2));binding.posts.setAdapter(adapter);
        binding.getRoot().setOnRefreshListener(this::load);
    }
    @Override protected void onResume(){super.onResume();load();}
    private void load(){if(loading)return;loading=true;
        new ApiClient(this).get("community/users/"+Uri.encode(userId),result->{loading=false;if(isFinishing()||isDestroyed())return;
            binding.loading.setVisibility(View.GONE);binding.getRoot().setRefreshing(false);JSONObject data=result.optJSONObject("data");if(data==null)return;
            JSONObject user=data.optJSONObject("user");if(user!=null){binding.name.setText(user.optString("nickname"));binding.avatar.setContentDescription(user.optString("nickname")+"的头像");DefaultAvatars.show(binding.avatar,user.optString("avatarUrl"));}
            List<CommunityPost> posts=new ArrayList<>();JSONArray rows=data.optJSONArray("posts");if(rows!=null)for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row!=null)posts.add(CommunityPost.from(row));}
            adapter.submitList(posts);binding.stats.setText("笔记 "+data.optInt("postCount")+"  ·  获赞 "+data.optInt("likeCount"));binding.postsTitle.setText("阅读笔记 "+data.optInt("postCount"));
            binding.empty.setText("还没有公开的阅读笔记");binding.empty.setVisibility(posts.isEmpty()?View.VISIBLE:View.GONE);
        },error->{loading=false;if(isFinishing()||isDestroyed())return;binding.loading.setVisibility(View.GONE);binding.getRoot().setRefreshing(false);binding.empty.setText(error+"\n下拉重试");binding.empty.setVisibility(View.VISIBLE);});
    }
}
