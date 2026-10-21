package com.huimeng.bookshop.core.local;
import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import org.json.*;
import com.huimeng.bookshop.model.BookDetails;

/** One-time enrichment, never a re-import of historical accounts or business snapshots. */
public final class LocalBookUpgrade {
    private LocalBookUpgrade(){}
    public static int apply(Context context,LocalDatabase store,String space)throws Exception{
        String marker="book-details-v1:"+space;if(!store.metadata(marker).isEmpty())return 0;
        JSONArray books;try(java.io.InputStream input=context.getAssets().open("book-details/fictional-v1.json");java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream()){
            byte[] buffer=new byte[4096];int count;while((count=input.read(buffer))!=-1)bytes.write(buffer,0,count);books=new JSONArray(new String(bytes.toByteArray(),java.nio.charset.StandardCharsets.UTF_8));
        }
        android.database.sqlite.SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();int changed=0;
        try{
            for(int i=0;i<books.length();i++){JSONObject book=books.getJSONObject(i);String id=book.getString("id");
                try(Cursor row=db.rawQuery("SELECT data,confirmed FROM local_entities e WHERE e.space_id=? AND e.resource='products' AND e.id=? AND e.deleted=0 AND NOT EXISTS(SELECT 1 FROM sync_operations o WHERE o.space_id=e.space_id AND o.entity_id=e.id AND o.resource IN ('products','merchantProducts') AND o.state<>'ACKED')",new String[]{space,id})){
                    if(!row.moveToFirst())continue;JSONObject current=new JSONObject(row.getString(0));if(!eligible(current,book))continue;enrich(current,book);ContentValues values=new ContentValues();values.put("data",current.toString());
                    if(!row.isNull(1)){JSONObject confirmed=new JSONObject(row.getString(1));if(eligible(confirmed,book)){enrich(confirmed,book);values.put("confirmed",confirmed.toString());}}
                    db.update("local_entities",values,"space_id=? AND resource='products' AND id=?",new String[]{space,id});changed++;
                }
            }
            store.metadata(marker,"1");db.setTransactionSuccessful();return changed;
        }finally{db.endTransaction();}
    }
    private static boolean eligible(JSONObject current,JSONObject book){if(!book.optString("name").equals(current.optString("name")))return false;for(String key:BookDetails.KEYS){if(key.equals("tableOfContents")){if(BookDetails.chapters(current.opt(key)).length()>0)return false;}else if(!current.isNull(key)&&!current.optString(key).isEmpty())return false;}return true;}
    private static void enrich(JSONObject current,JSONObject book)throws Exception{for(String key:BookDetails.KEYS)current.put(key,book.get(key));String old=book.getString("name")+"，适合亲子共读的温暖绘本。";if(old.equals(current.optString("summary")))current.put("summary",book.getString("summary"));}
}
