package com.huimeng.bookshop.ui;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.huimeng.bookshop.core.ApiClient;
import com.huimeng.bookshop.databinding.ItemCartBinding;
import com.huimeng.bookshop.model.Product;
import java.util.ArrayList;
import java.util.List;

public class CartAdapter extends RecyclerView.Adapter<CartAdapter.Holder>{
    public record CartLine(Product product,int quantity,boolean selected){}
    public interface Listener{void change(CartLine line,int quantity);void select(CartLine line,boolean selected);void remove(CartLine line);}
    private final List<CartLine> items=new ArrayList<>();private final Listener listener;
    public CartAdapter(Listener listener){this.listener=listener;}
    public void submit(List<CartLine> next){items.clear();items.addAll(next);notifyDataSetChanged();}
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent,int type){return new Holder(ItemCartBinding.inflate(LayoutInflater.from(parent.getContext()),parent,false));}
    @Override public void onBindViewHolder(@NonNull Holder holder,int position){holder.bind(items.get(position),listener);}
    @Override public int getItemCount(){return items.size();}
    static class Holder extends RecyclerView.ViewHolder{private final ItemCartBinding binding;Holder(ItemCartBinding binding){super(binding.getRoot());this.binding=binding;}
        void bind(CartLine line,Listener listener){Product item=line.product();boolean available=item.stock()>0&&line.quantity()<=item.stock();binding.name.setText(item.name());binding.price.setText(item.price()+(item.stock()==0?" · 暂不可购买":available?"":" · 库存不足"));binding.quantity.setText(String.valueOf(line.quantity()));binding.selected.setOnCheckedChangeListener(null);binding.selected.setChecked(line.selected());binding.selected.setEnabled(available||line.selected());binding.selected.setContentDescription("选择《"+item.name()+"》");binding.selected.setOnCheckedChangeListener((button,checked)->listener.select(line,checked));Glide.with(binding.cover).load(ApiClient.assetUrl(item.coverUrl())).placeholder(new BookCoverDrawable(item.name())).error(new BookCoverDrawable(item.name())).fitCenter().into(binding.cover);binding.minus.setEnabled(item.stock()>0||line.quantity()==1);binding.minus.setOnClickListener(v->{if(line.quantity()>1)listener.change(line,Math.min(line.quantity()-1,item.stock()));else listener.remove(line);});binding.plus.setEnabled(line.quantity()<Math.min(99,item.stock()));binding.plus.setOnClickListener(v->listener.change(line,line.quantity()+1));binding.remove.setOnClickListener(v->listener.remove(line));}
    }
}
