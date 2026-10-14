package com.huimeng.bookshop.core.local;
import org.json.*;
public final class BuyerRepository extends BusinessRepository {
    public BuyerRepository(LocalDatabase db,String space){super(db,space);}
    public JSONObject handle(int method,String path,JSONObject body)throws Exception{
        if(body==null)body=new JSONObject();String[] p=path.split("/");
        if(path.equals("auth/me")&&method==0)return new JSONObject().put("user",profile());
        if(path.equals("auth/me")&&method==2){if(body.has("newPassword")||body.has("currentPassword"))throw new IllegalArgumentException("修改密码需先连接服务并验证网络账号");JSONObject value=profile();value.put("username",text(body,"username",3,32)).put("name",text(body,"nickname",1,30));JSONObject result=save("profile","self","profile.save",value,body);return result.put("user",value);}
        if(path.equals("auth/me/avatar")&&method==2){JSONObject value=profile();value.put("avatarUrl",body.getString("avatarUrl"));return save("profile","self","profile.avatar",value,body).put("avatarUrl",body.getString("avatarUrl"));}
        if(path.equals("auth/default-avatars"))return data(new JSONArray());
        if(path.equals("auth/switch-role")){String role=body.optString("role","USER");new LocalSpaceStore(db).role(space,role);return new JSONObject().put("activeRole",role);}
        if(path.startsWith("me/addresses")){
            if(method==0)return data(db.list(space,"addresses"));
            String id=p.length>2?p[2]:id();if(method==3){JSONObject removed=require("addresses",id);JSONObject result=remove("addresses",id,"address.delete");if(removed.optInt("is_default")==1||removed.optBoolean("isDefault")){JSONArray remaining=db.list(space,"addresses");if(remaining.length()>0){JSONObject first=remaining.getJSONObject(0);first.put("is_default",1).put("isDefault",true);save("addresses",first.getString("id"),"address.save",first,first);}}return result;}
            JSONObject value=new JSONObject(body.toString());for(String key:new String[]{"recipient","phone","region","detail"})text(body,key,key.equals("phone")?6:1,300);
            if(p.length>2)require("addresses",id);
            if(value.optBoolean("isDefault")||db.list(space,"addresses").length()==0){value.put("is_default",1).put("isDefault",true);body.put("isDefault",true);JSONArray others=db.list(space,"addresses");for(int i=0;i<others.length();i++){JSONObject row=others.getJSONObject(i);if(row.optInt("is_default")==1&&!id.equals(row.optString("id"))){row.put("is_default",0).put("isDefault",false);save("addresses",row.getString("id"),"address.save",row,row);}}}
            return save("addresses",id,"address.save",value,body.put("id",id));
        }
        if(path.startsWith("me/favorites")||path.startsWith("me/cart")){
            boolean cart=path.startsWith("me/cart");String resource=cart?"cart":"favorites";
            if(method==0)return data(db.list(space,resource));if(p.length<3)throw new IllegalArgumentException("请选择商品");String id=p[2];
            if(method==3){JSONObject old=db.entity(space,resource,id);if(old==null)old=new JSONObject().put("id",id);old.put("deleted",true);return save(resource,id,cart?"cart.remove":"favorite.set",old,new JSONObject().put("productId",id).put("enabled",false));}
            JSONObject product=require("products",id);JSONObject value=new JSONObject(product.toString());JSONObject payload=new JSONObject().put("productId",id).put("enabled",true);
            if(cart){int quantity=body.optInt("quantity",1);if(quantity<1||quantity>99)throw new IllegalArgumentException("商品数量须在 1 至 99 之间");value.put("quantity",quantity).put("selected",body.optBoolean("selected",true));payload.put("quantity",quantity).put("selected",value.optBoolean("selected"));}
            return save(resource,id,cart?"cart.set":"favorite.set",value,payload);
        }
        if(path.startsWith("me/notifications")){
            if(method==0)return data(db.list(space,"notifications"));
            if(p.length>3&&p[2].equals("bulk")){JSONArray ids=body.getJSONArray("ids");for(int i=0;i<ids.length();i++)notice(ids.getString(i),p[3].equals("delete"));return new JSONObject().put("ok",true).put("pending",true);}
            if(p.length>2)return notice(p[2],method==3);
        }
        return null;
    }
    private JSONObject notice(String id,boolean delete)throws Exception{JSONObject value=require("notifications",id);value.put(delete?"deleted":"is_read",delete?true:1);return save("notifications",id,delete?"notice.delete":"notice.read",value,new JSONObject().put("id",id));}
}
