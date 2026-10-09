package com.huimeng.bookshop.ui;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.huimeng.bookshop.databinding.ItemTextBinding;
import java.util.ArrayList;
import java.util.List;

public class TextAdapter extends RecyclerView.Adapter<TextAdapter.Holder>{
    public record Row(String primary,String secondary){}
    private final List<Row> rows=new ArrayList<>();
    public void submit(List<Row> next){rows.clear();rows.addAll(next);notifyDataSetChanged();}
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent,int viewType){return new Holder(ItemTextBinding.inflate(LayoutInflater.from(parent.getContext()),parent,false));}
    @Override public void onBindViewHolder(@NonNull Holder holder,int position){holder.bind(rows.get(position));}
    @Override public int getItemCount(){return rows.size();}
    static class Holder extends RecyclerView.ViewHolder{private final ItemTextBinding binding;Holder(ItemTextBinding binding){super(binding.getRoot());this.binding=binding;}void bind(Row row){binding.primary.setText(row.primary());binding.secondary.setText(row.secondary());}}
}
