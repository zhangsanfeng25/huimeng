package com.huimeng.bookshop.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.content.Intent;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.huimeng.bookshop.core.ApiClient;
import com.huimeng.bookshop.core.LocalCartStore;
import com.huimeng.bookshop.databinding.ActivityCartBinding;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CartActivity extends AppCompatActivity implements CartAdapter.Listener{
    private ActivityCartBinding binding;private CartAdapter adapter;private LocalCartStore cart;private final List<CartAdapter.CartLine> lines=new ArrayList<>();
    @Override protected void onCreate(Bundle state){super.onCreate(state);binding=ActivityCartBinding.inflate(getLayoutInflater());setContentView(binding.getRoot());binding.back.setOnClickListener(v->finish());adapter=new CartAdapter(this);binding.list.setLayoutManager(new LinearLayoutManager(this));binding.list.setAdapter(adapter);binding.checkout.setOnClickListener(v->startActivity(new Intent(this,CheckoutActivity.class)));}
    @Override protected void onResume(){super.onResume();if(binding!=null)load();}
    private void load(){
        binding.loading.setVisibility(View.VISIBLE);
        binding.emptyState.setVisibility(View.GONE);
        binding.checkout.setEnabled(false);
        LocalCartStore.resolve(this,store->{
            if(isFinishing()||isDestroyed())return;
            cart=store;
            binding.loading.setVisibility(View.GONE);
            lines.clear();
            for(LocalCartStore.Entry item:store.list())lines.add(new CartAdapter.CartLine(item.product(),item.quantity(),item.selected()));
            render();
        },error->{
            if(isFinishing()||isDestroyed())return;
            binding.loading.setVisibility(View.GONE);
            binding.cartSummary.setVisibility(View.GONE);
            showEmptyState(error,"重试加载",this::load);
        });
    }
    private void showEmptyState(String message,String action,Runnable open){
        binding.empty.setText(message);
        binding.emptyAction.setText(action);
        binding.emptyAction.setOnClickListener(v->open.run());
        binding.emptyState.setVisibility(View.VISIBLE);
    }
    private void render(){
        adapter.submit(lines);
        binding.emptyState.setVisibility(View.GONE);
        binding.cartSummary.setVisibility(lines.isEmpty()?View.GONE:View.VISIBLE);
        if(lines.isEmpty())showEmptyState("购物车还是空的","浏览绘本",()->startActivity(new Intent(this,SearchActivity.class)));
        long total=0;int count=0;boolean valid=true;
        for(CartAdapter.CartLine line:lines)if(line.selected()){total+=(long)line.product().priceCents()*line.quantity();count+=line.quantity();if(line.quantity()>line.product().stock())valid=false;}
        binding.total.setText(String.format(Locale.CHINA,"¥%.2f",total/100.0));
        binding.checkout.setText(valid?"去结算 ("+count+")":"请取消勾选失效商品");
        binding.checkout.setEnabled(count>0&&valid);
    }
    @Override public void change(CartAdapter.CartLine line,int quantity){update(line,quantity,line.selected());}
    @Override public void select(CartAdapter.CartLine line,boolean selected){update(line,line.quantity(),selected);}
    private void update(CartAdapter.CartLine line,int quantity,boolean selected){if(cart==null)return;try{JSONObject body=new JSONObject().put("quantity",quantity).put("selected",selected);new ApiClient(this).put("me/cart/"+line.product().id(),body,result->{cart.upsert(line.product(),quantity,selected);load();},this::toast);}catch(Exception error){toast("购物车更新失败");}}
    @Override public void remove(CartAdapter.CartLine line){if(cart==null)return;new MaterialAlertDialogBuilder(this).setMessage("从购物车删除《"+line.product().name()+"》？").setNegativeButton("保留",null).setPositiveButton("删除",(dialog,which)->new ApiClient(this).delete("me/cart/"+line.product().id(),result->{cart.remove(line.product().id());load();},this::toast)).show();}
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_SHORT).show();}
}
