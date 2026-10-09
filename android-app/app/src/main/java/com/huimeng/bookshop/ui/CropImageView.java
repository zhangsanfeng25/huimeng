package com.huimeng.bookshop.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

/** 4:3 touch crop: drag to reposition, pinch to zoom. */
public final class CropImageView extends View{
    private Bitmap bitmap;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private float scale,offsetX,offsetY,lastX,lastY,lastDistance;
    public CropImageView(Context context){super(context);setBackgroundColor(Color.BLACK);}
    public void setBitmap(Bitmap value){bitmap=value;reset();invalidate();}
    private void reset(){if(bitmap==null||getWidth()==0||getHeight()==0)return;scale=Math.max((float)getWidth()/bitmap.getWidth(),(float)getHeight()/bitmap.getHeight());offsetX=(getWidth()-bitmap.getWidth()*scale)/2;offsetY=(getHeight()-bitmap.getHeight()*scale)/2;}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);reset();}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);if(bitmap!=null)canvas.drawBitmap(bitmap,null,new android.graphics.RectF(offsetX,offsetY,offsetX+bitmap.getWidth()*scale,offsetY+bitmap.getHeight()*scale),paint);}
    private void clamp(){if(bitmap==null)return;offsetX=Math.min(0,Math.max(getWidth()-bitmap.getWidth()*scale,offsetX));offsetY=Math.min(0,Math.max(getHeight()-bitmap.getHeight()*scale,offsetY));}
    @Override public boolean onTouchEvent(MotionEvent event){if(bitmap==null)return false;
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN){lastX=event.getX();lastY=event.getY();return true;}
        if(event.getActionMasked()==MotionEvent.ACTION_POINTER_DOWN&&event.getPointerCount()>1){lastDistance=distance(event);return true;}
        if(event.getActionMasked()==MotionEvent.ACTION_MOVE){
            if(event.getPointerCount()>1){float distance=distance(event);if(lastDistance>0){float cx=(event.getX(0)+event.getX(1))/2,cy=(event.getY(0)+event.getY(1))/2;
                float next=Math.max(Math.max((float)getWidth()/bitmap.getWidth(),(float)getHeight()/bitmap.getHeight()),Math.min(scale*distance/lastDistance,8f));
                offsetX=cx-(cx-offsetX)*next/scale;offsetY=cy-(cy-offsetY)*next/scale;scale=next;clamp();invalidate();}lastDistance=distance;
            }else{offsetX+=event.getX()-lastX;offsetY+=event.getY()-lastY;clamp();invalidate();}
            lastX=event.getX();lastY=event.getY();return true;
        }
        if(event.getActionMasked()==MotionEvent.ACTION_POINTER_UP){lastDistance=0;lastX=event.getX(0);lastY=event.getY(0);return true;}
        return true;
    }
    private float distance(MotionEvent e){return (float)Math.hypot(e.getX(0)-e.getX(1),e.getY(0)-e.getY(1));}
    public Bitmap crop(){return crop(1200,900);}
    public Bitmap crop(int targetWidth,int targetHeight){if(bitmap==null||getWidth()==0||getHeight()==0)throw new IllegalStateException("图片尚未加载");Bitmap out=Bitmap.createBitmap(targetWidth,targetHeight,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(out);float x=offsetX*targetWidth/getWidth(),y=offsetY*targetHeight/getHeight();float width=bitmap.getWidth()*scale*targetWidth/getWidth(),height=bitmap.getHeight()*scale*targetHeight/getHeight();canvas.drawBitmap(bitmap,null,new android.graphics.RectF(x,y,x+width,y+height),paint);return out;}
}
