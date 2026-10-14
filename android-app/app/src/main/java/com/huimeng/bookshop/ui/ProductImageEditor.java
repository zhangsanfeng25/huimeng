package com.huimeng.bookshop.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.huimeng.bookshop.R;
import com.huimeng.bookshop.core.ApiClient;

import org.json.JSONArray;
import org.json.JSONObject;


import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;

/** Shared image selection, crop, upload and cover editing for merchant forms. */
public final class ProductImageEditor {
    private final AppCompatActivity activity;private final LinearLayout container;private final MaterialButton add;private final TextView hint;
    private final List<String> urls;private final ArrayDeque<Uri> waiting;private final ProductImageState model;
    private final ActivityResultLauncher<Intent> picker,cropper;private boolean enabled=true;
    public ProductImageEditor(AppCompatActivity activity,LinearLayout container,MaterialButton add,TextView hint,Bundle state){this.activity=activity;this.container=container;this.add=add;this.hint=hint;
        model=new androidx.lifecycle.ViewModelProvider(activity).get(ProductImageState.class);urls=model.urls;waiting=model.waiting;if(!model.restored&&state!=null){ArrayList<String> old=state.getStringArrayList("product_images");if(old!=null)urls.addAll(old);model.initialized=state.getBoolean("product_images_initialized");ArrayList<String> queued=state.getStringArrayList("product_image_queue");if(queued!=null)for(String uri:queued)waiting.add(Uri.parse(uri));model.pendingPath=state.getString("product_image_pending","");}model.restored=true;
        picker=activity.registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),result->{if(result.getResultCode()!=Activity.RESULT_OK||result.getData()==null)return;Intent data=result.getData();if(data.getClipData()!=null)for(int i=0;i<data.getClipData().getItemCount();i++)waiting.add(data.getClipData().getItemAt(i).getUri());else if(data.getData()!=null)waiting.add(data.getData());while(waiting.size()+urls.size()>9)waiting.removeLast();nextCrop();});
        cropper=activity.registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),result->{if(result.getResultCode()!=Activity.RESULT_OK||result.getData()==null){model.busy=false;nextCrop();return;}String path=result.getData().getStringExtra("cropped_path");if(path==null){model.busy=false;nextCrop();return;}model.upload(activity.getApplicationContext(),path);});
        add.setOnClickListener(v->{if(model.busy||!enabled)return;if(urls.size()>=9){toast("最多选择 9 张商品图片");return;}Intent intent=new Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);picker.launch(Intent.createChooser(intent,"选择商品封面和详情图片"));});model.listener=()->{if(!alive())return;render();if(!model.error.isEmpty()){toast(model.error);model.error="";}if(!model.busy&&activity.getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))nextCrop();};if(!model.pendingPath.isEmpty()&&!model.busy)model.upload(activity.getApplicationContext(),model.pendingPath);render();
    }
    private boolean alive(){return !activity.isFinishing()&&!activity.isDestroyed();}
    private int dp(int v){return Math.round(v*activity.getResources().getDisplayMetrics().density);}
    private void toast(String text){Toast.makeText(activity,text,Toast.LENGTH_SHORT).show();}
    private void fail(String error){if(!alive())return;model.busy=false;waiting.clear();toast(error);render();}
    private void nextCrop(){if(!alive())return;Uri uri=waiting.poll();if(uri==null){model.busy=false;render();return;}model.busy=true;render();cropper.launch(new Intent(activity,CropImageActivity.class).setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));}
    public boolean isBusy(){return model.busy;} public void resume(){render();if(!model.busy)nextCrop();}
    public void setEnabled(boolean value){enabled=value;render();}
    public void saveState(Bundle state){state.putStringArrayList("product_images",new ArrayList<>(urls));state.putBoolean("product_images_initialized",model.initialized);state.putString("product_image_pending",model.pendingPath);ArrayList<String> queued=new ArrayList<>();for(Uri uri:waiting)queued.add(uri.toString());state.putStringArrayList("product_image_queue",queued);}
    public void setData(JSONObject data){if(model.initialized)return;urls.clear();String cover=data.optString("coverUrl");if(!cover.isEmpty())urls.add(cover);JSONArray images=data.optJSONArray("detailImages");if(images==null)try{images=new JSONArray(data.optString("detailImages","[]"));}catch(Exception ignored){}if(images!=null)for(int i=0;i<images.length();i++){String url=images.optString(i);if(!url.isEmpty()&&!urls.contains(url))urls.add(url);}model.initialized=true;render();}
    public JSONObject applyTo(JSONObject body) throws Exception {return body.put("coverUrl",urls.isEmpty()?"":urls.get(0)).put("detailImages",new JSONArray(urls));}
    private void preview(String url){ImageView image=new ImageView(activity);image.setScaleType(ImageView.ScaleType.FIT_CENTER);Glide.with(activity).load(ApiClient.assetUrl(url)).error(R.drawable.ic_book_placeholder).into(image);android.app.Dialog dialog=new android.app.Dialog(activity,android.R.style.Theme_Black_NoTitleBar_Fullscreen);dialog.setContentView(image);image.setContentDescription("商品图片预览，点击关闭");image.setOnClickListener(v->dialog.dismiss());dialog.show();}
    private void render(){container.removeAllViews();add.setEnabled(enabled&&!model.busy&&urls.size()<9);hint.setText(model.busy?"正在裁剪或上传图片，请稍候…":"已选 "+urls.size()+"/9 张 · 第一张为封面 · 点击图片预览");int index=0;
        for(String url:new ArrayList<>(urls)){final int position=index++;LinearLayout cell=new LinearLayout(activity);cell.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(dp(132),-2);params.setMargins(0,0,dp(10),0);container.addView(cell,params);
            FrameLayout frame=new FrameLayout(activity);cell.addView(frame,new LinearLayout.LayoutParams(-1,dp(132)));ImageView image=new ImageView(activity);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setContentDescription("商品图片 "+(position+1)+(position==0?"，封面":""));frame.addView(image,new FrameLayout.LayoutParams(-1,-1));Glide.with(activity).load(ApiClient.assetUrl(url)).placeholder(R.drawable.ic_book_placeholder).error(R.drawable.ic_book_placeholder).into(image);image.setOnClickListener(v->preview(url));
            ImageButton remove=new ImageButton(activity);remove.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);remove.setBackgroundResource(R.drawable.bg_search);remove.setContentDescription("移除第 "+(position+1)+" 张商品图片");remove.setEnabled(enabled&&!model.busy);frame.addView(remove,new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.TOP|Gravity.END));remove.setOnClickListener(v->{urls.remove(url);model.initialized=true;render();});
            MaterialButton cover=new MaterialButton(activity,null,com.google.android.material.R.attr.borderlessButtonStyle);cover.setText(position==0?"当前封面":"设为封面");cover.setTextColor(ContextCompat.getColor(activity,R.color.cool_green_dark));cover.setEnabled(enabled&&!model.busy&&position!=0);cell.addView(cover,new LinearLayout.LayoutParams(-1,dp(48)));cover.setOnClickListener(v->{urls.remove(url);urls.add(0,url);model.initialized=true;render();});
        }
    }
}
