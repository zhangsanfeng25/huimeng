package com.huimeng.bookshop.core.local;
import org.json.*;
public final class OrderRepository extends BusinessRepository {
    public OrderRepository(LocalDatabase db,String space){super(db,space);}
    public JSONObject handle(int method,String path,JSONObject body)throws Exception{
        if(body==null)body=new JSONObject();String[] p=path.split("/");
        if(path.equals("checkout")&&method==1){
            String addressId=body.getString("addressId");JSONObject address=require("addresses",addressId);JSONArray items=body.getJSONArray("items"),details=new JSONArray(),payloadItems=new JSONArray();long total=0;boolean sample=false,real=false;
            if(items.length()==0)throw new IllegalArgumentException("请选择商品");
            for(int i=0;i<items.length();i++){JSONObject item=items.getJSONObject(i),product=require("products",item.getString("productId"));int quantity=item.getInt("quantity");if(quantity<1||quantity>99||quantity>product.optInt("stock"))throw new IllegalArgumentException("商品库存不足");sample|=product.optBoolean("sample");real|=!product.optBoolean("sample");long cents=product.optLong("priceCents");total+=cents*quantity;payloadItems.put(new JSONObject(item.toString()).put("expectedPriceCents",cents));details.put(new JSONObject().put("id",id()).put("productId",product.getString("id")).put("productName",product.optString("name")).put("coverUrl",product.optString("coverUrl")).put("unitPriceCents",cents).put("quantity",quantity).put("subtotalCents",cents*quantity));}
            if(sample&&real)throw new IllegalArgumentException("本地示例与网络商品请分开下单");String group=id(),order=id();JSONObject payload=new JSONObject(body.toString()).put("items",payloadItems).put("expectedTotalCents",total);
            JSONObject checkout=new JSONObject().put("id",group).put("status","PENDING").put("totalCents",total).put("sample",sample).put("pendingAction","create");
            save("checkouts",group,"checkout.create",checkout,payload);
            JSONObject row=new JSONObject().put("id",order).put("checkout_group_id",group).put("total_cents",total).put("status","PENDING_PAYMENT").put("confirmedState","LOCAL").put("pendingAction","create").put("sample",sample).put("productName",details.getJSONObject(0).optString("productName")).put("shop",sample?"本地示例书店":"待联网确认").put("items",details).put("itemCount",details.length()).put("address_snapshot_json",address.toString()).put("events",new JSONArray()).put("created_at",java.time.Instant.now().toString());
            db.saveConfirmed(space,"buyerOrders",order,row,0);return new JSONObject().put("checkoutGroupId",group).put("totalCents",total).put("orderIds",new JSONArray().put(order)).put("pending",true);
        }
        if(p[0].equals("checkout")&&p.length>1){JSONObject value=require("checkouts",p[1]);if(method==0)return data(value);JSONObject payload=new JSONObject(body.toString()).put("groupId",p[1]).put("expectedTotalCents",value.optLong("totalCents"));value.put("pendingAction","pay");return save("checkouts",p[1],"checkout.pay",value,payload);}
        if((path.startsWith("me/orders")||path.startsWith("merchant/orders"))){String resource=path.startsWith("merchant/")?"merchantOrders":"buyerOrders";if(method==0)return data(p.length>2?require(resource,p[2]):db.list(space,resource));JSONObject order=require(resource,p[2]);String action=p.length>3?p[3]:"";if(!java.util.Arrays.asList("cancel","confirm","ship","advance").contains(action))throw new IllegalArgumentException("不支持的订单操作");order.put("pendingAction",action);return save(resource,p[2],"order."+action,order,new JSONObject(body.toString()).put("orderId",p[2]));}
        if(path.equals("me/ratings")&&method==1){text(body,"content",1,1000);int stars=body.optInt("stars");if(stars<1||stars>5)throw new IllegalArgumentException("请选择 1–5 星评分");return save("ownRatings",id(),"rating.create",new JSONObject(body.toString()),body);}
        return null;
    }
}
