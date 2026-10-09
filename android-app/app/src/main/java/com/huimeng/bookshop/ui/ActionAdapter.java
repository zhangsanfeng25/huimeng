package com.huimeng.bookshop.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.huimeng.bookshop.core.ApiClient;
import com.huimeng.bookshop.databinding.ItemActionBinding;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ActionAdapter extends RecyclerView.Adapter<ActionAdapter.Holder>{
    public record Row(String id,String title,String subtitle,String action,boolean enabled,String coverUrl,String coverTitle){
        public Row(String id,String title,String subtitle,String action,boolean enabled){this(id,title,subtitle,action,enabled,"","");}
    }
    private final List<Row> items=new ArrayList<>();private final Consumer<Row> listener;private final Consumer<Row> details;
    public ActionAdapter(Consumer<Row> listener){this(listener,null);}
    public ActionAdapter(Consumer<Row> listener,Consumer<Row> details){this.listener=listener;this.details=details;}
    public void submit(List<Row> next){if(items.equals(next))return;items.clear();items.addAll(next);notifyDataSetChanged();}
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent,int type){return new Holder(ItemActionBinding.inflate(LayoutInflater.from(parent.getContext()),parent,false));}
    @Override public void onBindViewHolder(@NonNull Holder holder,int position){holder.bind(items.get(position),listener,details);}
    @Override public int getItemCount(){return items.size();}
    static class Holder extends RecyclerView.ViewHolder{
        private final ItemActionBinding binding;
        Holder(ItemActionBinding b){super(b.getRoot());binding=b;}
        void bind(Row row,Consumer<Row> listener,Consumer<Row> details){
            binding.title.setText(row.title());binding.subtitle.setText(row.subtitle());
            if(row.coverTitle().isEmpty()){binding.cover.setVisibility(View.GONE);Glide.with(binding.cover).clear(binding.cover);}
            else{
                binding.cover.setVisibility(View.VISIBLE);
                BookCoverDrawable fallback=new BookCoverDrawable(row.coverTitle());
                Glide.with(binding.cover).load(ApiClient.assetUrl(row.coverUrl()))
                    .placeholder(fallback).error(new BookCoverDrawable(row.coverTitle())).centerCrop().into(binding.cover);
            }
            binding.action.setText(row.action());binding.action.setVisibility(row.action().isEmpty()?View.GONE:View.VISIBLE);
            binding.action.setEnabled(row.enabled());binding.action.setOnClickListener(v->listener.accept(row));
            binding.getRoot().setOnClickListener(details==null?null:v->details.accept(row));
        }
    }
}
