package com.huimeng.bookshop.ui;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.huimeng.bookshop.databinding.ItemTextBinding;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.json.JSONArray;
import org.json.JSONObject;

/** The message navigation owns both conversation and notification lists. */
final class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.Holder> {
    private final List<JSONObject> rows=new ArrayList<>();
    private final Consumer<String> open;
    private final boolean merchant;
    ConversationAdapter(boolean merchant,Consumer<String> open){this.merchant=merchant;this.open=open;}
    void submit(JSONArray data){rows.clear();if(data!=null)for(int i=0;i<data.length();i++){JSONObject row=data.optJSONObject(i);if(row!=null)rows.add(row);}notifyDataSetChanged();}
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent,int type){return new Holder(ItemTextBinding.inflate(LayoutInflater.from(parent.getContext()),parent,false));}
    @Override public void onBindViewHolder(@NonNull Holder holder,int position){JSONObject row=rows.get(position);int unread=row.optInt("unread");holder.binding.primary.setText(row.optString(merchant?"buyer":"shop")+(unread>0?" · "+unread+" 条未读":""));holder.binding.secondary.setText(row.optString("last_message","暂无消息"));holder.itemView.setOnClickListener(v->open.accept(row.optString("id")));holder.itemView.setContentDescription(holder.binding.primary.getText()+"，"+holder.binding.secondary.getText());}
    @Override public int getItemCount(){return rows.size();}
    static final class Holder extends RecyclerView.ViewHolder{final ItemTextBinding binding;Holder(ItemTextBinding binding){super(binding.getRoot());this.binding=binding;itemView.setClickable(true);itemView.setFocusable(true);}}
}
