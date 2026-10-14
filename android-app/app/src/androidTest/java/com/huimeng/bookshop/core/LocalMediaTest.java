package com.huimeng.bookshop.core;
import android.test.AndroidTestCase;
import com.huimeng.bookshop.core.local.*;
@SuppressWarnings("deprecation")
public class LocalMediaTest extends AndroidTestCase {
 public void testPrivateCopySurvivesSourceDeletion()throws Exception{String file="media-"+java.util.UUID.randomUUID()+".db";LocalDatabase db=new LocalDatabase(getContext(),file);String space=new LocalSpaceStore(db).ensureActiveSpace();java.io.File output=null;try{byte[] bytes=new byte[150];bytes[0]=(byte)255;bytes[1]=(byte)216;bytes[148]=(byte)255;bytes[149]=(byte)217;LocalMediaStore media=new LocalMediaStore(getContext(),db);String id=media.saveJpeg(space,bytes,"COMMUNITY");output=new java.io.File(media.resolveUri(space,id).getPath());assertTrue(output.exists());assertEquals(150,output.length());try{media.resolveUri("other",id);fail("Must isolate media");}catch(IllegalArgumentException expected){}}finally{db.close();getContext().deleteDatabase(file);if(output!=null){output.delete();output.getParentFile().delete();}}}
}
