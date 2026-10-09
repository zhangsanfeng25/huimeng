package com.huimeng.bookshop.ui;

import android.content.Context;
import android.net.Uri;
import androidx.lifecycle.ViewModel;
import com.android.volley.Request;
import com.huimeng.bookshop.core.ApiClient;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;

/** Retains an in-flight upload and remaining crop queue across activity recreation. */
public class ProductImageState extends ViewModel {
    public final List<String> urls=new ArrayList<>();public final ArrayDeque<Uri> waiting=new ArrayDeque<>();
    public boolean busy,initialized,restored;public String pendingPath="",error="";public Runnable listener;
    public ProductImageState(){}
    public void upload(Context context,String path){busy=true;pendingPath=path;notifyChange();Context app=context.getApplicationContext();
        new Thread(()->{try{byte[] bytes=Files.readAllBytes(new File(path).toPath());if(bytes.length>3*1024*1024)throw new IllegalArgumentException("图片超过 3 MB，请缩小后重试");new ApiClient(app).uploadJpeg("merchant/media/uploads",Request.Method.POST,bytes,response->{String url=response.optString("url");if(!url.isEmpty()&&!urls.contains(url))urls.add(url);initialized=true;busy=false;pendingPath="";new File(path).delete();notifyChange();},message->failed(message));}catch(Exception exception){new android.os.Handler(android.os.Looper.getMainLooper()).post(()->failed(exception.getMessage()==null?"无法读取图片":exception.getMessage()));}},"huimeng-product-upload").start();
    }
    private void failed(String message){busy=false;pendingPath="";waiting.clear();error=message;notifyChange();}
    private void notifyChange(){if(listener!=null)listener.run();}
    @Override protected void onCleared(){listener=null;super.onCleared();}
}
