package com.huimeng.bookshop.ui;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.huimeng.bookshop.databinding.ItemAddressBinding;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class AddressAdapter extends RecyclerView.Adapter<AddressAdapter.Holder>{
    private final List<JSONObject> items=new ArrayList<>();private final Consumer<JSONObject> edit;
    public AddressAdapter(Consumer<JSONObject> edit){this.edit=edit;}
    public void submit(List<JSONObject> next){items.clear();items.addAll(next);notifyDataSetChanged();}
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent,int type){return new Holder(ItemAddressBinding.inflate(LayoutInflater.from(parent.getContext()),parent,false));}
    @Override public void onBindViewHolder(@NonNull Holder holder,int position){JSONObject row=items.get(position);
        holder.binding.recipient.setText(row.optString("recipient")+"  "+row.optString("phone")+(row.optInt("is_default")==1?"  ·  默认":""));
        holder.binding.detail.setText(row.optString("region")+row.optString("detail"));
        holder.binding.edit.setOnClickListener(v->edit.accept(row));
        holder.binding.edit.setText("操作");holder.binding.getRoot().setOnClickListener(v->edit.accept(row));
    }
    @Override public int getItemCount(){return items.size();}
    static final class Holder extends RecyclerView.ViewHolder{final ItemAddressBinding binding;Holder(ItemAddressBinding binding){super(binding.getRoot());this.binding=binding;}}
}
