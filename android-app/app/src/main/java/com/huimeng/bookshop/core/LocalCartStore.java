package com.huimeng.bookshop.core;

import android.content.Context;
import android.content.SharedPreferences;
import com.huimeng.bookshop.model.Product;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Account-scoped mirror, refreshed from the server before cart operations. */
public final class LocalCartStore {
    public record Entry(Product product,int quantity,boolean selected){}
    private final SharedPreferences preferences;
    private final String key;

    private LocalCartStore(Context context,String accountId){
        preferences=context.getApplicationContext().getSharedPreferences("huimeng_cart",Context.MODE_PRIVATE);
        key="cart_"+accountId;
    }

    public static void resolve(Context context,Consumer<LocalCartStore> ready,Consumer<String> failure){
        SessionStore session=new SessionStore(context);
        if(!session.accountId().isEmpty()){refresh(context,new LocalCartStore(context,session.accountId()),ready,failure);return;}
        new ApiClient(context).get("auth/me",result->{JSONObject user=result.optJSONObject("user");String id=user==null?"":user.optString("id");
            if(id.isEmpty()){failure.accept("无法确认当前账号，请重新登录");return;}
            session.setAccountId(id);refresh(context,new LocalCartStore(context,id),ready,failure);
        },failure);
    }
    private static void refresh(Context context,LocalCartStore cart,Consumer<LocalCartStore> ready,Consumer<String> failure){new ApiClient(context).get("me/cart",result->{JSONArray data=result.optJSONArray("data");if(data==null){failure.accept("购物车数据暂不可用，请重试");return;}JSONArray values=new JSONArray();for(int i=0;i<data.length();i++){JSONObject row=data.optJSONObject(i);if(row==null)continue;Object selection=row.opt("selected");boolean selected=selection==null||Boolean.TRUE.equals(selection)||(selection instanceof Number&&((Number)selection).intValue()==1);try{values.put(cart.toJson(new Entry(Product.from(row),row.optInt("quantity",1),selected)));}catch(Exception ignored){}}cart.save(values);ready.accept(cart);},failure);}

    public List<Entry> list(){List<Entry> values=new ArrayList<>();JSONArray data=read();
        for(int i=0;i<data.length();i++){JSONObject row=data.optJSONObject(i);if(row!=null&&!row.optString("id").isEmpty())values.add(new Entry(Product.from(row),Math.max(1,row.optInt("quantity",1)),row.optBoolean("selected",true)));}
        return values;
    }
    public int quantity(String productId){for(Entry item:list())if(item.product().id().equals(productId))return item.quantity();return 0;}
    public void upsert(Product product,int quantity,boolean selected){JSONArray existing=read(),next=new JSONArray();
        for(int i=0;i<existing.length();i++){JSONObject row=existing.optJSONObject(i);if(row!=null&&!product.id().equals(row.optString("id")))next.put(row);}
        try{next.put(toJson(new Entry(product,Math.max(1,Math.min(99,quantity)),selected)));}catch(Exception ignored){}
        save(next);
    }
    public void remove(String productId){JSONArray existing=read(),next=new JSONArray();
        for(int i=0;i<existing.length();i++){JSONObject row=existing.optJSONObject(i);if(row!=null&&!productId.equals(row.optString("id")))next.put(row);}
        save(next);
    }
    public void clearSelected(){JSONArray next=new JSONArray();for(Entry item:list())if(!item.selected()){
        try{JSONObject row=toJson(item);next.put(row);}catch(Exception ignored){}
    }save(next);}
    private JSONObject toJson(Entry item)throws Exception{Product p=item.product();return new JSONObject().put("id",p.id()).put("name",p.name()).put("summary",p.summary())
        .put("category",p.category()).put("ageRange",p.ageRange()).put("shop",p.shop()).put("priceCents",p.priceCents())
        .put("coverUrl",p.coverUrl()).put("stock",p.stock()).put("arEnabled",p.arEnabled()).put("audioUrl",p.audioUrl())
        .put("quantity",item.quantity()).put("selected",item.selected());}
    private JSONArray read(){try{return new JSONArray(preferences.getString(key,"[]"));}catch(Exception error){return new JSONArray();}}
    private void save(JSONArray data){preferences.edit().putString(key,data.toString()).apply();}
}
