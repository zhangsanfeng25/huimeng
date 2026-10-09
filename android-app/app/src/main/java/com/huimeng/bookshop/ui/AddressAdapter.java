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
    private final List<JSONObject> items=new ArrayList<>();private final Consumer<JSONObject> edit,options;
    public AddressAdapter(Consumer<JSONObject> edit,Consumer<JSONObject> options){this.edit=edit;this.options=options;}
    public void submit(List<JSONObject> next){items.clear();items.addAll(next);notifyDataSetChanged();}
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent,int type){return new Holder(ItemAddressBinding.inflate(LayoutInflater.from(parent.getContext()),parent,false));}
    @Override public void onBindViewHolder(@NonNull Holder holder,int position){JSONObject row=items.get(position);
        holder.binding.recipient.setText(row.optString("recipient").trim());holder.binding.phone.setText(row.optString("phone").trim());
        holder.binding.defaultBadge.setVisibility(row.optInt("is_default")==1?android.view.View.VISIBLE:android.view.View.GONE);
        holder.binding.detail.setText((row.optString("region").trim()+" "+row.optString("detail").trim()).trim());
        holder.binding.edit.setOnClickListener(v->edit.accept(row));
        holder.binding.edit.setText("修改");holder.binding.edit.setContentDescription("修改"+row.optString("recipient")+"的收货地址");holder.binding.getRoot().setOnClickListener(v->options.accept(row));
    }
    @Override public int getItemCount(){return items.size();}
    static final class Holder extends RecyclerView.ViewHolder{final ItemAddressBinding binding;Holder(ItemAddressBinding binding){super(binding.getRoot());this.binding=binding;}}
}
