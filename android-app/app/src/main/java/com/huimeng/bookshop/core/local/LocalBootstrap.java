package com.huimeng.bookshop.core.local;

import android.content.Context;
import java.nio.charset.StandardCharsets;
import org.json.*;

/** Read-only initial public content; account-specific records are never embedded in the APK. */
public final class LocalBootstrap {
    private static volatile JSONObject media=new JSONObject();
    private LocalBootstrap(){}
    public static void prepare(Context context,LocalDatabase db,String space)throws Exception{
        JSONObject snapshot;
        try(java.io.InputStream input=context.getAssets().open("bootstrap/public.json")){
            java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[8192];int read;while((read=input.read(buffer))!=-1)bytes.write(buffer,0,read);snapshot=new JSONObject(new String(bytes.toByteArray(),StandardCharsets.UTF_8));
        }catch(java.io.FileNotFoundException absent){return;}
        media=snapshot.optJSONObject("media")==null?new JSONObject():snapshot.getJSONObject("media");
        // Never overwrite a later network snapshot with the bundled historical baseline.
        if(!db.metadata("bootstrap:"+space).isEmpty())return;
        if(db.metadata("last-sync:"+space).isEmpty()&&db.metadata("data-import:"+space).isEmpty())new LocalDataImporter(db).apply(space,snapshot,"");
        db.metadata("bootstrap:"+space,snapshot.optString("revision"));
    }
    public static String asset(String original){String file=media.optString(original);return file.isEmpty()?"":"file:///android_asset/bootstrap/"+file;}
}
