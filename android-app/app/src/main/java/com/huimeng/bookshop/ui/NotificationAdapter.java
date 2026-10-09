package com.huimeng.bookshop.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.huimeng.bookshop.databinding.ItemNotificationBinding;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.Holder>{
    public record Notice(String id,String title,String content,String createdAt,boolean read){}
    private final List<Notice> items=new ArrayList<>();
    private final Set<String> selected=new LinkedHashSet<>();
    private final Consumer<Notice> open;private final Runnable selectionChanged;
    private boolean selecting;
    public NotificationAdapter(Consumer<Notice> open,Runnable selectionChanged){this.open=open;this.selectionChanged=selectionChanged;}
    public void submit(List<Notice> next){
        if(items.equals(next))return;items.clear();items.addAll(next);
        Set<String> currentIds=new LinkedHashSet<>();
        for(Notice notice:next)currentIds.add(notice.id());
        selected.retainAll(currentIds);
        notifyDataSetChanged();selectionChanged.run();
    }
    public boolean selecting(){return selecting;}
    public int selectedCount(){return selected.size();}
    public List<String> selectedIds(){return new ArrayList<>(selected);}
    public void exitSelection(){selecting=false;selected.clear();notifyDataSetChanged();selectionChanged.run();}
    public void selectAll(){for(Notice item:items)selected.add(item.id());selecting=!items.isEmpty();notifyDataSetChanged();selectionChanged.run();}
    private void toggle(Notice item){selecting=true;if(!selected.add(item.id()))selected.remove(item.id());notifyDataSetChanged();selectionChanged.run();}
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent,int type){return new Holder(ItemNotificationBinding.inflate(LayoutInflater.from(parent.getContext()),parent,false));}
    @Override public void onBindViewHolder(@NonNull Holder holder,int position){holder.bind(items.get(position));}
    @Override public int getItemCount(){return items.size();}
    class Holder extends RecyclerView.ViewHolder{
        private final ItemNotificationBinding binding;
        Holder(ItemNotificationBinding b){super(b.getRoot());binding=b;}
        void bind(Notice item){
            binding.title.setText(item.title());binding.content.setText(item.content());binding.time.setText(item.createdAt().replace('T',' '));
            binding.dot.setVisibility(item.read()?View.INVISIBLE:View.VISIBLE);
            binding.selection.setVisibility(selecting?View.VISIBLE:View.GONE);
            binding.selection.setOnCheckedChangeListener(null);binding.selection.setChecked(selected.contains(item.id()));
            binding.selection.setOnClickListener(v->toggle(item));
            binding.arrow.setVisibility(selecting?View.GONE:View.VISIBLE);
            binding.getRoot().setAlpha(item.read()?0.75f:1f);
            binding.getRoot().setOnLongClickListener(v->{if(!selecting||!selected.contains(item.id()))toggle(item);return true;});
            binding.getRoot().setOnClickListener(v->{if(selecting)toggle(item);else open.accept(item);});
        }
    }
}
