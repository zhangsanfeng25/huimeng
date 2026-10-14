package com.huimeng.bookshop.ui;

import android.view.View;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;

import com.huimeng.bookshop.core.ApiClient;
import com.huimeng.bookshop.databinding.ItemProductBinding;
import com.huimeng.bookshop.model.Product;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.HashSet;
import java.util.Set;

public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.Holder>{
    private final List<Product> items=new ArrayList<>();
    private final Consumer<Product> listener;
    private final Consumer<Product> add;
    private final Set<String> adding=new HashSet<>();
    public ProductAdapter(Consumer<Product> listener){this(listener,null);}
    public ProductAdapter(Consumer<Product> listener,Consumer<Product> add){this.listener=listener;this.add=add;}
    public void setAdding(String id,boolean value){if(value)adding.add(id);else adding.remove(id);for(int i=0;i<items.size();i++)if(items.get(i).id().equals(id)){notifyItemChanged(i);break;}}
    public void submit(List<Product> next){if(items.equals(next))return;items.clear();items.addAll(next);notifyDataSetChanged();}
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent,int viewType){return new Holder(ItemProductBinding.inflate(LayoutInflater.from(parent.getContext()),parent,false));}
    @Override public void onBindViewHolder(@NonNull Holder holder,int position){holder.bind(items.get(position),listener,add,adding.contains(items.get(position).id()));}
    @Override public int getItemCount(){return items.size();}
    static class Holder extends RecyclerView.ViewHolder{
        private final ItemProductBinding binding;
        Holder(ItemProductBinding binding){super(binding.getRoot());this.binding=binding;}
        void bind(Product item,Consumer<Product> listener,Consumer<Product> add,boolean busy){
            binding.name.setText(item.name());binding.price.setText(item.price());
            binding.meta.setText(item.ageRange()+" · "+item.category());
            Glide.with(binding.cover).load(ApiClient.assetUrl(item.coverUrl())).placeholder(new BookCoverDrawable(item.name())).error(new BookCoverDrawable(item.name())).fitCenter().into(binding.cover);
            binding.getRoot().setOnClickListener(v->listener.accept(item));
            binding.quickAdd.setVisibility(add==null?View.GONE:View.VISIBLE);
            binding.quickAdd.setEnabled(item.stock()>0&&!busy);
            binding.quickAdd.setAlpha(item.stock()>0&&!busy?1f:.4f);
            binding.quickAdd.setContentDescription(item.stock()>0?"将《"+item.name()+"》加入购物车":"《"+item.name()+"》已售罄");
            binding.quickAdd.setOnClickListener(v->{if(add!=null)add.accept(item);});
            binding.cover.setContentDescription(item.name()+"封面");
        }
    }
}
