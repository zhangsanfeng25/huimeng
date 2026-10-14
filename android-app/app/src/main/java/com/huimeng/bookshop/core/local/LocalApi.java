package com.huimeng.bookshop.core.local;
import org.json.*;
/** Compatibility adapter for existing screen contracts; business rules stay in repositories. */
public final class LocalApi {
    private final LocalDatabase db;private final String space;
    public LocalApi(LocalDatabase db,String space){this.db=db;this.space=space;}
    public JSONObject handle(int method,String path,JSONObject body)throws Exception{
        if(method!=0)db.getWritableDatabase().beginTransaction();try{
            JSONObject result=new CatalogRepository(db,space).handle(method,path,body);
            if(result==null)result=new BuyerRepository(db,space).handle(method,path,body);
            if(result==null)result=new CommunityRepository(db,space).handle(method,path,body);
            if(result==null)result=new MerchantRepository(db,space).handle(method,path,body);
            if(result==null)result=new OrderRepository(db,space).handle(method,path,body);
            if(result==null)throw new IllegalArgumentException("此操作需连接服务后完成");
            if(method!=0)db.getWritableDatabase().setTransactionSuccessful();return result;
        }finally{if(method!=0)db.getWritableDatabase().endTransaction();}
    }
}
