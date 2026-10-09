package com.huimeng.bookshop.ui;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public final class CropImageActivity extends AppCompatActivity{
    private CropImageView crop;
    @Override protected void onCreate(Bundle state){super.onCreate(state);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(18,24,18,24);root.setBackgroundColor(0xff1c2639);
        TextView tip=new TextView(this);tip.setText("裁剪图片 · 拖动调整位置，双指缩放");tip.setTextColor(-1);tip.setTextSize(17);tip.setGravity(Gravity.CENTER);root.addView(tip,new LinearLayout.LayoutParams(-1,70));
        boolean avatar=getIntent().getBooleanExtra("square_crop",false);
        crop=new CropImageView(this);int width=getResources().getDisplayMetrics().widthPixels-36;root.addView(crop,new LinearLayout.LayoutParams(-1,avatar?width:width*3/4));
        MaterialButton done=new MaterialButton(this);done.setText("使用这张图片");root.addView(done,new LinearLayout.LayoutParams(-1,64));setContentView(root);
        try{Uri uri=getIntent().getData();if(uri==null)throw new IllegalArgumentException();BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
            try(InputStream input=getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(input,null,options);}int sample=1;while(Math.max(options.outWidth,options.outHeight)/sample>2048)sample*=2;
            options.inJustDecodeBounds=false;options.inSampleSize=sample;Bitmap image;
            try(InputStream input=getContentResolver().openInputStream(uri)){image=BitmapFactory.decodeStream(input,null,options);}if(image==null)throw new IllegalArgumentException();
            try(InputStream input=getContentResolver().openInputStream(uri)){ExifInterface exif=new ExifInterface(input);int orientation=exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);Matrix matrix=new Matrix();
                if(orientation==ExifInterface.ORIENTATION_ROTATE_90)matrix.postRotate(90);else if(orientation==ExifInterface.ORIENTATION_ROTATE_180)matrix.postRotate(180);else if(orientation==ExifInterface.ORIENTATION_ROTATE_270)matrix.postRotate(270);
                else if(orientation==ExifInterface.ORIENTATION_FLIP_HORIZONTAL)matrix.postScale(-1,1);
                if(!matrix.isIdentity()){Bitmap rotated=Bitmap.createBitmap(image,0,0,image.getWidth(),image.getHeight(),matrix,true);image.recycle();image=rotated;}
            }catch(Exception ignored){}crop.setBitmap(image);
        }catch(Exception error){Toast.makeText(this,"无法读取图片，请重新选择",Toast.LENGTH_SHORT).show();finish();return;}
        done.setOnClickListener(v->{try{Bitmap image=avatar?crop.crop(600,600):crop.crop();File file=File.createTempFile("image-crop-",".jpg",getCacheDir());try(FileOutputStream stream=new FileOutputStream(file)){image.compress(Bitmap.CompressFormat.JPEG,82,stream);}image.recycle();setResult(Activity.RESULT_OK,new android.content.Intent().putExtra("cropped_path",file.getAbsolutePath()));finish();}catch(Exception error){Toast.makeText(this,"裁剪失败，请重试",Toast.LENGTH_SHORT).show();}});
    }
}
