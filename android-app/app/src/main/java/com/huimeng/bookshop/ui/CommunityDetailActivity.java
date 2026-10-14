package com.huimeng.bookshop.ui;

import android.animation.ObjectAnimator;
import android.content.Intent;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;
import com.huimeng.bookshop.core.ApiClient;
import com.huimeng.bookshop.databinding.ActivityCommunityDetailBinding;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class CommunityDetailActivity extends AppCompatActivity{
    private ActivityCommunityDetailBinding binding;private TextAdapter comments;private String postId;
    private boolean liked,likeBusy;private int likeCount;
    private int shownComments=-1;private String authorId="";
    private final Handler updates=new Handler(Looper.getMainLooper());
    private final Runnable refresh=new Runnable(){@Override public void run(){if(!isFinishing()&&!isDestroyed()){syncCounts();updates.postDelayed(this,5000);}}};
    @Override protected void onCreate(Bundle state){super.onCreate(state);binding=ActivityCommunityDetailBinding.inflate(getLayoutInflater());setContentView(binding.getRoot());postId=getIntent().getStringExtra("post_id");if(postId==null||postId.isEmpty()){finish();return;}
        binding.back.setOnClickListener(v->finish());comments=new TextAdapter();binding.comments.setLayoutManager(new LinearLayoutManager(this));binding.comments.setAdapter(comments);
        binding.photos.setLayoutManager(new LinearLayoutManager(this,RecyclerView.HORIZONTAL,false));PagerSnapHelper snap=new PagerSnapHelper();snap.attachToRecyclerView(binding.photos);
        binding.photos.addOnScrollListener(new RecyclerView.OnScrollListener(){@Override public void onScrollStateChanged(RecyclerView list,int state){if(state==RecyclerView.SCROLL_STATE_IDLE){View view=snap.findSnapView(list.getLayoutManager());if(view!=null)binding.photoIndicator.setText((list.getLayoutManager().getPosition(view)+1)+" / "+list.getAdapter().getItemCount());}}});
        android.view.View.OnClickListener authorClick=v->{if(!authorId.isEmpty())startActivity(new Intent(this,UserProfileActivity.class).putExtra("user_id",authorId));};binding.avatar.setOnClickListener(authorClick);binding.author.setOnClickListener(authorClick);
        binding.send.setOnClickListener(v->comment());binding.like.setOnClickListener(v->toggleLike());binding.commentToggle.setOnClickListener(v->toggleComposer());load();}
    private void toggleComposer(){boolean show=binding.commentComposer.getVisibility()!=View.VISIBLE;
        ObjectAnimator.ofFloat(binding.commentToggle,"scaleX",1f,0.88f,1f).setDuration(240).start();
        ObjectAnimator.ofFloat(binding.commentToggle,"scaleY",1f,0.88f,1f).setDuration(240).start();
        binding.commentComposer.setVisibility(show?View.VISIBLE:View.GONE);
        binding.commentToggle.setContentDescription(show?"收起评论输入框":"展开评论输入框");
        if(show){binding.commentComposer.setAlpha(0f);binding.commentComposer.animate().alpha(1f).setDuration(180).start();binding.commentInput.requestFocus();}
        else ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(binding.commentInput.getWindowToken(),0);
    }
    private void load(){new ApiClient(this).get("community/posts/"+postId,result->{if(isFinishing()||isDestroyed())return;binding.loading.setVisibility(View.GONE);JSONObject data=result.optJSONObject("data");if(data==null)return;
        binding.title.setText(data.optString("title"));binding.author.setText(data.optString("authorName"));authorId=data.optString("authorId",data.optString("author_user_id"));binding.avatar.setContentDescription("查看"+data.optString("authorName")+"的主页");DefaultAvatars.show(binding.avatar,data.optString("avatarUrl"));binding.content.setText(data.optString("content"));
        liked=data.optInt("liked")==1||data.optBoolean("liked");likeCount=data.optInt("likeCount");renderLike();
        List<String> images=new ArrayList<>();JSONArray list=data.optJSONArray("images_json");if(list==null)try{list=new JSONArray(data.optString("images_json","[]"));}catch(Exception ignored){}
        if(list!=null)for(int i=0;i<list.length();i++){String url=list.optString(i);if(!url.isEmpty())images.add(url);}
        String cover=data.optString("cover_url");if(images.isEmpty()&&!cover.isEmpty())images.add(cover);
        binding.photos.setVisibility(images.isEmpty()?View.GONE:View.VISIBLE);binding.photoIndicator.setVisibility(images.size()>1?View.VISIBLE:View.GONE);
        binding.photos.setAdapter(new PostImagesAdapter(images));if(!images.isEmpty())binding.photoIndicator.setText("1 / "+images.size());
        showComments(data.optJSONArray("comments"));
    },this::toast);}
    private void showComments(JSONArray array){List<TextAdapter.Row> rows=new ArrayList<>();if(array!=null)for(int i=0;i<array.length();i++){JSONObject item=array.optJSONObject(i);if(item!=null)rows.add(new TextAdapter.Row(item.optString("authorName")+" · "+com.huimeng.bookshop.core.CommentTime.format(item.optString("createdAt")),item.optString("content")));}
        shownComments=rows.size();binding.commentCount.setText(shownComments+" 条");binding.emptyComments.setVisibility(rows.isEmpty()?View.VISIBLE:View.GONE);comments.submit(rows);}
    private void syncCounts(){new ApiClient(this).get("community/posts/"+postId,result->{if(isFinishing()||isDestroyed())return;JSONObject data=result.optJSONObject("data");if(data==null)return;
        if(!likeBusy){liked=data.optInt("liked")==1||data.optBoolean("liked");likeCount=data.optInt("likeCount");renderLike();}
        JSONArray array=data.optJSONArray("comments");if(array!=null&&array.length()!=shownComments)showComments(array);
    },error->{});}
    @Override protected void onResume(){super.onResume();updates.removeCallbacks(refresh);updates.postDelayed(refresh,5000);}
    @Override protected void onPause(){updates.removeCallbacks(refresh);super.onPause();}
    private void renderLike(){binding.like.setText((liked?"♥ 已赞 ":"♡ 点赞 ")+likeCount);binding.like.setContentDescription(liked?"取消点赞":"点赞阅读笔记");binding.like.setTextColor(liked?0xffc94650:0xff514da4);}
    private void toggleLike(){if(likeBusy)return;likeBusy=true;boolean target=!liked;ApiClient api=new ApiClient(this);
        java.util.function.Consumer<JSONObject> success=result->{liked=result.optBoolean("liked");likeCount=result.optInt("likeCount");renderLike();likeBusy=false;
            ObjectAnimator.ofFloat(binding.like,"scaleX",1f,liked?1.25f:0.82f,1f).setDuration(320).start();
            ObjectAnimator.ofFloat(binding.like,"scaleY",1f,liked?1.25f:0.82f,1f).setDuration(320).start();};
        java.util.function.Consumer<String> failure=error->{likeBusy=false;toast(error);};
        if(target)api.put("community/posts/"+postId+"/like",new JSONObject(),success,failure);else api.delete("community/posts/"+postId+"/like",success,failure);
    }
    private void comment(){String value=String.valueOf(binding.commentInput.getText()).trim();if(value.isEmpty())return;binding.send.setEnabled(false);
        try{new ApiClient(this).post("community/posts/"+postId+"/comments",new JSONObject().put("content",value),result->{binding.commentInput.setText("");binding.send.setEnabled(true);toggleComposer();load();},error->{binding.send.setEnabled(true);toast(error);});}
        catch(Exception error){binding.send.setEnabled(true);toast("发送失败");}}
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_SHORT).show();}
}
