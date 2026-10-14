package com.huimeng.bookshop.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;

import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

public final class PaymentCheckView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path tick=new Path();
    private float progress;
    private boolean played;
    public PaymentCheckView(Context context,AttributeSet attrs){super(context,attrs);
        tick.moveTo(.24f,.52f);tick.lineTo(.43f,.70f);tick.lineTo(.78f,.32f);
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);float d=Math.min(getWidth(),getHeight());
        canvas.save();canvas.translate((getWidth()-d)/2,(getHeight()-d)/2);canvas.scale(d,d);
        paint.setStyle(Paint.Style.FILL);paint.setColor(0xFFE6F7EC);canvas.drawCircle(.5f,.5f,.47f,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(.065f);paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(0xFF219653);canvas.drawArc(.11f,.11f,.89f,.89f,-90,360*progress,false,paint);
        if(progress>.37f){Path part=new Path();PathMeasure measure=new PathMeasure(tick,false);
            float portion=Math.min(1f,(progress-.37f)/.63f);measure.getSegment(0,measure.getLength()*portion,part,true);
            paint.setStrokeWidth(.09f);paint.setStrokeJoin(Paint.Join.ROUND);canvas.drawPath(part,paint);}
        canvas.restore();
    }
    public void playOnce(){if(played)return;played=true;ValueAnimator animator=ValueAnimator.ofFloat(0f,1f);
        animator.setDuration(850);animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(value->{progress=(float)value.getAnimatedValue();invalidate();});animator.start();}
    public void showCompleted(){played=true;progress=1f;invalidate();}
}
