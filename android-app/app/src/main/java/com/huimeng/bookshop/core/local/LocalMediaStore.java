package com.huimeng.bookshop.core.local;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.util.UUID;
public final class LocalMediaStore {
    private final Context context;private final LocalDatabase db;
    public LocalMediaStore(Context context,LocalDatabase db){this.context=context.getApplicationContext();this.db=db;}
    public String saveJpeg(String space,byte[] bytes,String purpose)throws Exception{
        if(bytes.length<100||bytes.length>3*1024*1024||bytes[0]!=(byte)255||bytes[1]!=(byte)216||bytes[bytes.length-2]!=(byte)255||bytes[bytes.length-1]!=(byte)217)throw new IllegalArgumentException("请选择小于 3 MB 的 JPEG 图片");
        new LocalSpaceStore(db).info(space);String id=UUID.randomUUID().toString();StringBuilder hex=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        File folder=new File(context.getFilesDir(),"offline-media/"+space);if(!folder.isDirectory()&&!folder.mkdirs())throw new java.io.IOException("无法创建本地图片目录");
        File temp=File.createTempFile("image-",".tmp",folder),target=new File(folder,id+".jpg");
        try{try(FileOutputStream stream=new FileOutputStream(temp)){stream.write(bytes);stream.getFD().sync();}if(!temp.renameTo(target))throw new java.io.IOException("本地图片保存失败");
            ContentValues row=new ContentValues();row.put("space_id",space);row.put("id",id);row.put("path",target.getAbsolutePath());row.put("sha256",hex.toString());row.put("purpose",purpose);db.getWritableDatabase().insertOrThrow("local_media",null,row);return id;
        }catch(Exception e){if(target.exists())target.delete();throw e;}finally{if(temp.exists())temp.delete();}
    }
    public Uri resolveUri(String space,String id){try(Cursor c=db.getReadableDatabase().rawQuery("SELECT path FROM local_media WHERE space_id=? AND id=?",new String[]{space,id})){if(!c.moveToFirst())throw new IllegalArgumentException("本地图片不存在");return Uri.fromFile(new File(c.getString(0)));}}
}
