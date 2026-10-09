package com.huimeng.bookshop.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.core.content.ContextCompat;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.huimeng.bookshop.R;

/** A bounded pull gesture: follow the finger, stop at the threshold, refresh on release. */
public final class PullRefreshLayout extends FrameLayout {
    private final FrameLayout indicator;
    private final CircularProgressIndicator progress;
    private final int size, travel, slop;
    private float startX, startY, distance;
    private boolean dragging, refreshing;
    private Runnable listener;

    public PullRefreshLayout(Context context) { this(context, null); }
    public PullRefreshLayout(Context context, AttributeSet attributes) {
        super(context, attributes);
        float density = getResources().getDisplayMetrics().density;
        size = Math.round(40 * density); travel = Math.round(84 * density);
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
        indicator = new FrameLayout(context);
        GradientDrawable circle = new GradientDrawable();circle.setShape(GradientDrawable.OVAL);
        circle.setColor(ContextCompat.getColor(context, R.color.surface_card));
        indicator.setBackground(circle);indicator.setElevation(5 * density);
        progress = new CircularProgressIndicator(context);
        progress.setIndicatorSize(Math.round(24 * density));progress.setTrackThickness(Math.round(3 * density));
        progress.setIndicatorColor(ContextCompat.getColor(context, R.color.cool_green));
        progress.setMax(100);progress.setIndeterminate(false);
        indicator.addView(progress, new LayoutParams(-2, -2, Gravity.CENTER));
        indicator.setContentDescription("下拉刷新");indicator.setVisibility(GONE);
        addView(indicator, new LayoutParams(size, size, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
    }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow();indicator.bringToFront(); }
    public void setOnRefreshListener(Runnable value) { listener = value; }
    public void setRefreshing(boolean value) {
        if(!value&&indicator.getVisibility()==GONE&&!dragging){refreshing=false;distance=0;return;}
        refreshing = value;
        indicator.animate().cancel();
        if(value) {
            indicator.setVisibility(VISIBLE);indicator.setAlpha(1f);
            indicator.setTranslationY(travel-size);progress.setIndeterminate(true);
            indicator.setContentDescription("正在刷新");
        } else {
            dragging=false;distance=0;
            indicator.animate().translationY(-size).alpha(0f).setDuration(180).withEndAction(() -> {
                if(!refreshing){indicator.setVisibility(GONE);progress.setIndeterminate(false);progress.setProgress(0);}
            }).start();
        }
    }
    private boolean childCanScrollUp(View child) {
        if(child == indicator || child.getVisibility() != VISIBLE)return false;
        if(child instanceof androidx.core.widget.NestedScrollView || child instanceof androidx.recyclerview.widget.RecyclerView || child instanceof android.widget.ScrollView)return child.canScrollVertically(-1);
        if(child.canScrollVertically(-1))return true;
        if(child instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)if(childCanScrollUp(group.getChildAt(i)))return true;
        return false;
    }
    private boolean canPull() {
        if(refreshing || listener==null)return false;
        for(int i=0;i<getChildCount();i++)if(childCanScrollUp(getChildAt(i)))return false;
        return true;
    }
    @Override public void requestDisallowInterceptTouchEvent(boolean disallow) {
        // A nested scroller requests exclusive touch ownership before a pull can start.
        // At its top we still inspect vertical drags; horizontal drags remain with the child.
        if(!disallow||!canPull())super.requestDisallowInterceptTouchEvent(disallow);
    }
    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        switch(event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> {startX=event.getX();startY=event.getY();dragging=false;distance=0;}
            case MotionEvent.ACTION_MOVE -> {
                float dy=event.getY()-startY, dx=Math.abs(event.getX()-startX);
                if(dy>slop && dy>dx && canPull()) {
                    dragging=true;showPull(dy*.5f);getParent().requestDisallowInterceptTouchEvent(true);return true;
                }
            }
            case MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL -> {if(!dragging)distance=0;}
            default -> {}
        }
        return dragging;
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN){startX=event.getX();startY=event.getY();return canPull()||super.onTouchEvent(event);}
        if(!dragging && event.getActionMasked()==MotionEvent.ACTION_MOVE && canPull()) {
            float dy=event.getY()-startY;
            if(dy>slop&&dy>Math.abs(event.getX()-startX))dragging=true;
        }
        if(!dragging)return super.onTouchEvent(event);
        switch(event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE -> {
                showPull((event.getY()-startY)*.5f);
            }
            case MotionEvent.ACTION_UP -> {
                boolean ready=distance>=travel;dragging=false;getParent().requestDisallowInterceptTouchEvent(false);
                if(ready){setRefreshing(true);listener.run();}else setRefreshing(false);
                performClick();
            }
            case MotionEvent.ACTION_CANCEL -> {dragging=false;setRefreshing(false);}
            default -> {}
        }
        return true;
    }
    private void showPull(float value){
        distance=Math.max(0,value);indicator.animate().cancel();indicator.setVisibility(VISIBLE);indicator.setAlpha(1f);
        indicator.setTranslationY(Math.min(travel,distance)-size);
        progress.setProgress(Math.min(100,Math.round(distance/travel*100)));
        indicator.setContentDescription(distance>=travel?"松开刷新":"继续下拉刷新");
    }
    @Override public boolean performClick(){super.performClick();return true;}
    @Override protected void onDetachedFromWindow(){indicator.animate().cancel();dragging=false;super.onDetachedFromWindow();}
}
