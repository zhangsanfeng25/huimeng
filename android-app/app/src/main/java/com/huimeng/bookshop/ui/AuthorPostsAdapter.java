package com.huimeng.bookshop.ui;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.huimeng.bookshop.R;
import com.huimeng.bookshop.core.ApiClient;
import com.huimeng.bookshop.databinding.ItemAuthorPostBinding;
import com.huimeng.bookshop.model.CommunityPost;
import java.util.function.Consumer;

final class AuthorPostsAdapter extends ListAdapter<CommunityPost,AuthorPostsAdapter.Holder> {
    private final Consumer<CommunityPost> open;
    AuthorPostsAdapter(Consumer<CommunityPost> open){super(new DiffUtil.ItemCallback<>(){
        @Override public boolean areItemsTheSame(@NonNull CommunityPost old,@NonNull CommunityPost next){return old.id().equals(next.id());}
        @Override public boolean areContentsTheSame(@NonNull CommunityPost old,@NonNull CommunityPost next){return old.equals(next);}
    });this.open=open;}
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent,int type){return new Holder(ItemAuthorPostBinding.inflate(LayoutInflater.from(parent.getContext()),parent,false));}
    @Override public void onBindViewHolder(@NonNull Holder holder,int position){CommunityPost post=getItem(position);holder.binding.title.setText(post.title());PostLikeDisplay.bind(holder.binding.meta,post,true);Glide.with(holder.binding.cover).load(ApiClient.assetUrl(post.coverUrl())).placeholder(R.drawable.ic_book_placeholder).error(R.drawable.ic_book_placeholder).centerCrop().into(holder.binding.cover);holder.itemView.setOnClickListener(v->open.accept(post));}
    static final class Holder extends RecyclerView.ViewHolder{final ItemAuthorPostBinding binding;Holder(ItemAuthorPostBinding binding){super(binding.getRoot());this.binding=binding;}}
}
