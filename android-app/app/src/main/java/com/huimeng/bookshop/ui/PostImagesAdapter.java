package com.huimeng.bookshop.ui;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.huimeng.bookshop.R;
import com.huimeng.bookshop.core.ApiClient;
import com.huimeng.bookshop.databinding.ItemPostImageBinding;
import java.util.List;

public final class PostImagesAdapter extends RecyclerView.Adapter<PostImagesAdapter.Holder>{
    private final List<String> urls;
    public PostImagesAdapter(List<String> urls){this.urls=urls;}
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent,int type){return new Holder(ItemPostImageBinding.inflate(LayoutInflater.from(parent.getContext()),parent,false));}
    @Override public void onBindViewHolder(@NonNull Holder holder,int position){holder.binding.image.setContentDescription("阅读笔记图片 "+(position+1)+"/"+urls.size());Glide.with(holder.binding.image).load(ApiClient.assetUrl(urls.get(position))).placeholder(R.drawable.ic_book_placeholder).centerCrop().into(holder.binding.image);}
    @Override public int getItemCount(){return urls.size();}
    static final class Holder extends RecyclerView.ViewHolder{final ItemPostImageBinding binding;Holder(ItemPostImageBinding binding){super(binding.getRoot());this.binding=binding;}}
}
