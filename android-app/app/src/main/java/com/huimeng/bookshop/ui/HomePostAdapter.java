package com.huimeng.bookshop.ui;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.huimeng.bookshop.R;
import com.huimeng.bookshop.core.ApiClient;
import com.huimeng.bookshop.databinding.ItemHomePostBinding;
import com.huimeng.bookshop.model.CommunityPost;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

final class HomePostAdapter extends RecyclerView.Adapter<HomePostAdapter.Holder> {
    private final List<CommunityPost> rows=new ArrayList<>();
    private final Consumer<CommunityPost> open;
    HomePostAdapter(Consumer<CommunityPost> open){this.open=open;}
    void submit(List<CommunityPost> values){rows.clear();rows.addAll(values);notifyDataSetChanged();}
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent,int type){return new Holder(ItemHomePostBinding.inflate(LayoutInflater.from(parent.getContext()),parent,false));}
    @Override public void onBindViewHolder(@NonNull Holder holder,int position){CommunityPost row=rows.get(position);holder.binding.title.setText(row.title());holder.binding.author.setText(row.authorName());Glide.with(holder.binding.cover).load(ApiClient.assetUrl(row.coverUrl())).placeholder(R.drawable.ic_community).error(R.drawable.ic_community).centerCrop().into(holder.binding.cover);holder.itemView.setOnClickListener(v->open.accept(row));}
    @Override public int getItemCount(){return rows.size();}
    static class Holder extends RecyclerView.ViewHolder{final ItemHomePostBinding binding;Holder(ItemHomePostBinding binding){super(binding.getRoot());this.binding=binding;}}
}
