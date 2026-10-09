package com.huimeng.bookshop.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import com.huimeng.bookshop.core.ApiClient;
import com.huimeng.bookshop.databinding.ActivityPublishPostBinding;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public class PublishPostActivity extends AppCompatActivity{
    private ActivityPublishPostBinding binding;
    private final List<String> cropped=new ArrayList<>();
    private final ArrayDeque<Uri> waiting=new ArrayDeque<>();
    private ActivityResultLauncher<Intent> picker,cropper;
    private boolean publishing;
    @Override protected void onCreate(Bundle state){super.onCreate(state);
        picker=registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),result->{
            if(result.getResultCode()!=Activity.RESULT_OK||result.getData()==null)return;
            Intent data=result.getData();if(data.getClipData()!=null){for(int i=0;i<data.getClipData().getItemCount();i++)waiting.add(data.getClipData().getItemAt(i).getUri());}
            else if(data.getData()!=null)waiting.add(data.getData());
            while(waiting.size()+cropped.size()>9)waiting.removeLast();nextCrop();
        });
        cropper=registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),result->{
            if(result.getResultCode()==Activity.RESULT_OK&&result.getData()!=null){String path=result.getData().getStringExtra("cropped_path");if(path!=null){cropped.add(path);renderImages();}}
            nextCrop();
        });
        binding=ActivityPublishPostBinding.inflate(getLayoutInflater());setContentView(binding.getRoot());
        if(state!=null){List<String> old=state.getStringArrayList("cropped");if(old!=null)for(String path:old)if(new File(path).isFile())cropped.add(path);}
        renderImages();binding.back.setOnClickListener(v->finish());binding.addImages.setOnClickListener(v->select());binding.publish.setOnClickListener(v->publish());
    }
    @Override protected void onSaveInstanceState(Bundle state){state.putStringArrayList("cropped",new ArrayList<>(cropped));super.onSaveInstanceState(state);}
    private void select(){if(cropped.size()>=9){toast("一个帖子最多 9 张图片");return;}
        Intent intent=new Intent(Intent.ACTION_GET_CONTENT);intent.setType("image/*");intent.addCategory(Intent.CATEGORY_OPENABLE);intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
        picker.launch(Intent.createChooser(intent,"选择阅读笔记图片"));
    }
    private void nextCrop(){Uri next=waiting.poll();if(next!=null){Intent intent=new Intent(this,CropImageActivity.class);intent.setData(next);intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);cropper.launch(intent);}}
    private void renderImages(){binding.images.removeAllViews();float density=getResources().getDisplayMetrics().density;int size=(int)(density*108),buttonSize=(int)(density*48);
        binding.emptyImages.setVisibility(cropped.isEmpty()?View.VISIBLE:View.GONE);
        int index=0;
        for(String path:new ArrayList<>(cropped)){
            FrameLayout cell=new FrameLayout(this);LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(size,size);params.setMargins(0,0,(int)(density*10),0);binding.images.addView(cell,params);
            ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setContentDescription("已选择图片 "+(++index));cell.addView(image,new FrameLayout.LayoutParams(-1,-1));Glide.with(this).load(new File(path)).into(image);
            ImageButton remove=new ImageButton(this);remove.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);remove.setBackgroundResource(com.huimeng.bookshop.R.drawable.bg_search);remove.setContentDescription("删除第 "+index+" 张图片");
            FrameLayout.LayoutParams overlay=new FrameLayout.LayoutParams(buttonSize,buttonSize,Gravity.END|Gravity.TOP);cell.addView(remove,overlay);
            remove.setOnClickListener(v->{if(publishing)return;cropped.remove(path);new File(path).delete();renderImages();});
        }
        binding.imageHint.setText("已选 "+cropped.size()+"/9 张 · 逐张裁剪后上传");binding.addImages.setVisibility(cropped.size()>=9?View.GONE:View.VISIBLE);
    }
    private void publish(){if(publishing)return;String title=String.valueOf(binding.titleInput.getText()).trim(),content=String.valueOf(binding.contentInput.getText()).trim();
        if(title.isEmpty()||content.isEmpty()){toast("请填写标题和分享内容");return;}publishing=true;binding.publish.setEnabled(false);binding.publish.setText("正在上传图片…");
        uploadNext(0,new JSONArray(),title,content);
    }
    private void uploadNext(int index,JSONArray urls,String title,String content){
        if(index==cropped.size()){try{JSONObject body=new JSONObject().put("title",title).put("content",content).put("images",urls);
            binding.publish.setText("正在发布…");new ApiClient(this).post("community/posts",body,result->{for(String path:cropped)new File(path).delete();toast("发布成功");finish();},this::fail);
        }catch(Exception error){fail("发布失败，请重试");}return;}
        try{byte[] bytes=Files.readAllBytes(new File(cropped.get(index)).toPath());if(bytes.length>3*1024*1024){fail("第 "+(index+1)+" 张图片超过 3 MB");return;}
            binding.publish.setText("正在上传 "+(index+1)+"/"+cropped.size());new ApiClient(this).uploadJpeg(bytes,result->{String url=result.optString("url");if(url.isEmpty()){fail("图片上传失败");return;}urls.put(url);uploadNext(index+1,urls,title,content);},this::fail);
        }catch(Exception error){fail("无法读取已裁剪的图片");}
    }
    private void fail(String message){publishing=false;binding.publish.setEnabled(true);binding.publish.setText("立即发布");toast(message);}
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_SHORT).show();}
}
