package com.huimeng.bookshop.ui;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.huimeng.bookshop.databinding.ItemCategoryBinding;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.Holder>{
    public record Category(String id,String name){}
    private final List<Category> items=new ArrayList<>();
    private final Consumer<Category> listener;
    private int selected=0;
    public CategoryAdapter(Consumer<Category> listener){this.listener=listener;}
    public void submit(List<Category> next){String active=items.isEmpty()?"":items.get(selected).id();items.clear();items.add(new Category("","精选"));for(Category item:next)if(!item.id().isEmpty())items.add(item);selected=0;for(int i=1;i<items.size();i++)if(items.get(i).id().equals(active))selected=i;notifyDataSetChanged();}
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent,int viewType){return new Holder(ItemCategoryBinding.inflate(LayoutInflater.from(parent.getContext()),parent,false));}
    @Override public void onBindViewHolder(@NonNull Holder holder,int position){holder.binding.name.setText(items.get(position).name());holder.binding.name.setSelected(position==selected);holder.binding.name.setOnClickListener(v->{int next=holder.getBindingAdapterPosition();if(next==RecyclerView.NO_POSITION)return;int old=selected;selected=next;notifyItemChanged(old);notifyItemChanged(selected);listener.accept(items.get(selected));});TapMotion.attach(holder.binding.name);}
    @Override public int getItemCount(){return items.size();}
    static class Holder extends RecyclerView.ViewHolder{final ItemCategoryBinding binding;Holder(ItemCategoryBinding binding){super(binding.getRoot());this.binding=binding;}}
}
