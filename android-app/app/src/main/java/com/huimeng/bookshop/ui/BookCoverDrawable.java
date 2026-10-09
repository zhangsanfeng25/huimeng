package com.huimeng.bookshop.ui;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

/** A readable local cover for demo products whose uploaded cover is unavailable. */
public final class BookCoverDrawable extends Drawable {
    private static final int[] COLORS={0xFF6C65A9,0xFF4E8D9A,0xFFAB765A,0xFF708B62,0xFF9A6A92};
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF coverBounds=new RectF(0,0,200,260),frameBounds=new RectF(24,24,176,236);
    private final Typeface titleTypeface=Typeface.create("sans-serif-medium",Typeface.BOLD);
    private final int mainColor;
    private final String firstLine,secondLine;
    private int alpha=255;
    public BookCoverDrawable(String title){
        String normalized=title==null||title.isEmpty()?"绘梦绘本":title;
        mainColor=COLORS[Math.floorMod(normalized.hashCode(),COLORS.length)];
        String value=normalized.length()>12?normalized.substring(0,12)+"…":normalized;
        if(value.length()>6){firstLine=value.substring(0,6);secondLine=value.substring(6);}
        else{firstLine=value;secondLine=null;}
    }
    @Override public void draw(Canvas canvas){
        Rect r=getBounds();if(r.isEmpty())return;
        canvas.save();canvas.translate(r.left,r.top);canvas.scale(r.width()/200f,r.height()/260f);
        paint.setColor(mainColor);paint.setAlpha(alpha);canvas.drawRoundRect(coverBounds,12,12,paint);
        paint.setColor(0x44FFFFFF);canvas.drawCircle(160,47,59,paint);canvas.drawCircle(27,230,63,paint);
        paint.setColor(0xFFFFF7DF);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(3);
        canvas.drawRoundRect(frameBounds,10,10,paint);paint.setStyle(Paint.Style.FILL);
        // A moon, small stars and open book form one consistent storybook motif.
        canvas.drawCircle(100,83,24,paint);paint.setColor(mainColor);canvas.drawCircle(111,76,22,paint);
        paint.setColor(0xFFFFF7DF);canvas.drawCircle(49,73,2.5f,paint);canvas.drawCircle(150,112,2.5f,paint);
        paint.setStrokeWidth(3);paint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(48,177,99,188,paint);canvas.drawLine(99,188,152,177,paint);
        canvas.drawLine(48,177,48,206,paint);canvas.drawLine(152,177,152,206,paint);
        canvas.drawLine(48,206,99,218,paint);canvas.drawLine(99,218,152,206,paint);paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(titleTypeface);paint.setTextSize(18);
        if(secondLine!=null){canvas.drawText(firstLine,100,139,paint);canvas.drawText(secondLine,100,161,paint);}
        else canvas.drawText(firstLine,100,153,paint);
        canvas.restore();
    }
    @Override public void setAlpha(int value){alpha=value;invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
