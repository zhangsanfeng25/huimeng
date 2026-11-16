package com.huimeng.bookshop.ui;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.huimeng.bookshop.core.ApiClient;
import com.huimeng.bookshop.databinding.ItemSearchSuggestionBinding;
import com.huimeng.bookshop.model.Product;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

final class SearchSuggestionAdapter extends RecyclerView.Adapter<SearchSuggestionAdapter.Holder> {
    private final List<Product> rows=new ArrayList<>();
    private final Consumer<Product> select;
    SearchSuggestionAdapter(Consumer<Product> select){this.select=select;}
    void submit(List<Product> values){rows.clear();rows.addAll(values);notifyDataSetChanged();}
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent,int type){return new Holder(ItemSearchSuggestionBinding.inflate(LayoutInflater.from(parent.getContext()),parent,false));}
    @Override public void onBindViewHolder(@NonNull Holder holder,int position){
        Product product=rows.get(position);holder.binding.name.setText(product.name());
        Glide.with(holder.binding.cover).load(ApiClient.assetUrl(product.coverUrl())).placeholder(new BookCoverDrawable(product.name())).error(new BookCoverDrawable(product.name())).fitCenter().into(holder.binding.cover);
        holder.itemView.setOnClickListener(v->select.accept(product));
    }
    @Override public int getItemCount(){return rows.size();}
    static final class Holder extends RecyclerView.ViewHolder {
        final ItemSearchSuggestionBinding binding;
        Holder(ItemSearchSuggestionBinding binding){super(binding.getRoot());this.binding=binding;}
    }
}
