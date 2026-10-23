package com.huimeng.bookshop.ui;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.test.InstrumentationTestCase;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** Exports the existing title-drawn demo artwork; no account data or uploaded photos. */
@SuppressWarnings("deprecation")
public class DefaultCoverExportTest extends InstrumentationTestCase {
    public void testExportTwentyDefaultCovers()throws Exception{
        var context=getInstrumentation().getTargetContext();assertTrue(context.getPackageName().endsWith(".qa"));
        JSONObject snapshot;try(var input=context.getAssets().open("bootstrap/public.json")){snapshot=new JSONObject(new String(input.readAllBytes(),StandardCharsets.UTF_8));}
        var products=snapshot.getJSONObject("resources").getJSONArray("products");
        File directory=new File(context.getExternalFilesDir(null),"default-covers");assertTrue(directory.isDirectory()||directory.mkdirs());
        for(int n=1;n<=20;n++){
            String id=String.format(java.util.Locale.ROOT,"P%04d",n),title=null;
            for(int i=0;i<products.length();i++)if(id.equals(products.getJSONObject(i).optString("id")))title=products.getJSONObject(i).getString("name");
            assertNotNull(id,title);BookCoverDrawable drawable=new BookCoverDrawable(title);drawable.setBounds(0,0,400,520);
            Bitmap bitmap=Bitmap.createBitmap(400,520,Bitmap.Config.ARGB_8888);getInstrumentation().runOnMainSync(()->drawable.draw(new Canvas(bitmap)));
            try(var out=new FileOutputStream(new File(directory,id.toLowerCase(java.util.Locale.ROOT)+".png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}finally{bitmap.recycle();}
        }
    }
}
