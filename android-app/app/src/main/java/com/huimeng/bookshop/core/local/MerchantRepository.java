package com.huimeng.bookshop.core.local;
import org.json.*;
public final class MerchantRepository extends BusinessRepository {
    public MerchantRepository(LocalDatabase db,String space){super(db,space);}
    public JSONObject handle(int method,String path,JSONObject body)throws Exception{
        if(body==null)body=new JSONObject();String[] p=path.split("/");
        if(path.equals("me/merchant-application")){if(method==0)return data(db.entity(space,"merchantApplication","self"));text(body,"shopName",2,60);JSONObject application=new JSONObject(body.toString()).put("status","LOCAL_DRAFT").put("businessStatus","PENDING");return save("merchantApplication","self","merchant.apply",application,body).put("data",application);}
        if(!path.startsWith("merchant/products"))return null;
        if(method==0)return data(p.length>2?require("merchantProducts",p[2]):db.list(space,"merchantProducts"));
        String id=p.length>2?p[2]:id();
        if(p.length>3){JSONObject product=require("merchantProducts",id);String action=p[3];product.put("pendingAction",action);return save("merchantProducts",id,action.equals("submit")?"product.submit":"product.offline",product,new JSONObject().put("id",id));}
        com.huimeng.bookshop.model.BookDetails.validate(body);text(body,"name",1,100);text(body,"summary",1,5000);text(body,"ageRange",1,40);if(body.optInt("priceCents",-1)<0||body.optInt("stock",-1)<0)throw new IllegalArgumentException("价格和库存不能小于零");
        JSONObject old=p.length>2?require("merchantProducts",id):null;JSONObject value=com.huimeng.bookshop.model.BookDetails.inherit(old,body).put("status",old==null?"DRAFT":old.optString("status","DRAFT")).put("reviewStatus",old==null?"DRAFT":old.optString("reviewStatus","DRAFT")).put("pendingAction","save");
        return save("merchantProducts",id,"product.save",value,new JSONObject(body.toString()).put("id",id)).put("productId",id);
    }
}
