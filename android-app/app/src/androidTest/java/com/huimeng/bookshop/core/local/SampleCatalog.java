package com.huimeng.bookshop.core.local;
import org.json.JSONObject;
public final class SampleCatalog {
    private SampleCatalog(){}
    public static void seed(LocalDatabase db,String space){
        if(!db.metadata("sample:"+space).isEmpty())return;
        android.database.sqlite.SQLiteDatabase sql=db.getWritableDatabase();sql.beginTransaction();try{
            db.saveConfirmed(space,"categories","sample-category",new JSONObject().put("id","sample-category").put("name","本地示例").put("parent","本地示例").put("sample",true),0);
            db.saveConfirmed(space,"products","sample-book",new JSONObject().put("id","sample-book").put("name","森林里的好朋友（本地示例）").put("summary","用于体验本地浏览与下单，不会上传，也不代表真实库存。").put("category","本地示例").put("categoryId","sample-category").put("ageRange","3–6岁").put("shop","本地示例书店").put("priceCents",2800).put("stock",99).put("status","ONLINE").put("sample",true),0);
            db.metadata("sample:"+space,"1");sql.setTransactionSuccessful();
        }catch(Exception e){throw new IllegalStateException(e);}finally{sql.endTransaction();}
    }
}
