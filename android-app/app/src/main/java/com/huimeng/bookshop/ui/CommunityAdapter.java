package com.huimeng.bookshop.ui;

import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.huimeng.bookshop.R;
import com.huimeng.bookshop.core.ApiClient;
import com.huimeng.bookshop.databinding.ItemCommunityPostBinding;
import com.huimeng.bookshop.model.CommunityPost;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class CommunityAdapter extends ListAdapter<CommunityPost,CommunityAdapter.Holder> {
    private final Consumer<CommunityPost> listener;
    public CommunityAdapter(Consumer<CommunityPost> listener){super(new DiffUtil.ItemCallback<>() {
        @Override public boolean areItemsTheSame(@NonNull CommunityPost old,@NonNull CommunityPost next){return old.id().equals(next.id());}
        @Override public boolean areContentsTheSame(@NonNull CommunityPost old,@NonNull CommunityPost next){return old.equals(next);}
    });this.listener=listener;}
    public void submit(List<CommunityPost> next){submitList(new ArrayList<>(next));}
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent,int type){return new Holder(ItemCommunityPostBinding.inflate(LayoutInflater.from(parent.getContext()),parent,false));}
    @Override public void onBindViewHolder(@NonNull Holder holder,int position){holder.bind(getItem(position),listener);}
    static class Holder extends RecyclerView.ViewHolder {
        private final ItemCommunityPostBinding binding;
        Holder(ItemCommunityPostBinding binding){super(binding.getRoot());this.binding=binding;}
        void bind(CommunityPost item,Consumer<CommunityPost> listener){
            binding.title.setText(item.title());binding.content.setText(item.content());
            binding.author.setText(item.authorName()+("MERCHANT".equals(item.authorRole())?" · 商家":""));
            PostLikeDisplay.bind(binding.likes,item,false);binding.comments.setText(" · "+item.commentCount()+" 条评论");
            Glide.with(binding.cover).load(ApiClient.assetUrl(item.coverUrl())).placeholder(new ColorDrawable(0xFFE6F5EC)).error(R.drawable.ic_book_placeholder).centerCrop().into(binding.cover);
            DefaultAvatars.show(binding.avatar,item.avatarUrl());
            binding.avatar.setContentDescription("查看"+item.authorName()+"的主页");
            android.view.View.OnClickListener authorClick=v->{if(!item.authorId().isEmpty())v.getContext().startActivity(new Intent(v.getContext(),UserProfileActivity.class).putExtra("user_id",item.authorId()));};
            binding.avatar.setOnClickListener(authorClick);binding.author.setOnClickListener(authorClick);
            binding.getRoot().setOnClickListener(v->listener.accept(item));
        }
    }
}
