package com.huimeng.bookshop.ui;

import android.content.Context;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import java.util.ArrayList;
import java.util.List;

/** Local images, one page per swipe; active only while the home page is visible. */
public final class BannerCarouselView extends FrameLayout {
    private java.util.function.IntConsumer onBannerClick;
    public void setOnBannerClickListener(java.util.function.IntConsumer listener){onBannerClick=listener;}
    public record Banner(int resource,String description){}
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final RecyclerView pages;
    private final LinearLayout dots;
    private final LinearLayoutManager layout;
    private final PagerSnapHelper snap=new PagerSnapHelper();
    private final List<Banner> banners=new ArrayList<>();
    private boolean active,visible,touching;
    private int indicatedPage=-1;
    private final Runnable advance=this::advancePage;
    private void advancePage(){
        if(!canAdvance())return;
        pages.smoothScrollToPosition(currentAdapterPage()+1);
        schedule();
    }
    public BannerCarouselView(Context context){this(context,null);}
    public BannerCarouselView(Context context,AttributeSet attributes){super(context,attributes);
        pages=new RecyclerView(context);layout=new LinearLayoutManager(context,RecyclerView.HORIZONTAL,false);pages.setLayoutManager(layout);
        pages.setItemAnimator(null);pages.setOverScrollMode(OVER_SCROLL_NEVER);
        pages.setAdapter(new Pages());snap.attachToRecyclerView(pages);
        addView(pages,new LayoutParams(-1,-1));
        dots=new LinearLayout(context);dots.setGravity(Gravity.CENTER);dots.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        GradientDrawable dotBackground=new GradientDrawable();dotBackground.setColor(0x66000000);dotBackground.setCornerRadius(dp(12));dots.setBackground(dotBackground);dots.setPadding(dp(5),dp(4),dp(5),dp(4));
        LayoutParams dotParams=new LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);dotParams.bottomMargin=dp(9);addView(dots,dotParams);
        pages.setVisibility(GONE);dots.setVisibility(GONE);setClipToOutline(true);
        pages.addOnScrollListener(new RecyclerView.OnScrollListener(){
            @Override public void onScrolled(@NonNull RecyclerView recycler,int dx,int dy){updateDots();}
            @Override public void onScrollStateChanged(@NonNull RecyclerView recycler,int state){
                if(state==RecyclerView.SCROLL_STATE_IDLE){normalizeLoopPosition();updateDots();schedule();}else handler.removeCallbacks(advance);
            }
        });
        pages.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener(){
            @Override public boolean onInterceptTouchEvent(@NonNull RecyclerView recycler,@NonNull MotionEvent event){
                if(event.getActionMasked()==MotionEvent.ACTION_DOWN){touching=true;handler.removeCallbacks(advance);}
                if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL){touching=false;schedule();}
                return false;
            }
        });
    }
    public void setBanners(List<Banner> next){
        banners.clear();banners.addAll(next);handler.removeCallbacks(advance);
        for(int i=0;i<getChildCount();i++){View child=getChildAt(i);if(child!=pages&&child!=dots)child.setVisibility(banners.isEmpty()?VISIBLE:GONE);}
        pages.setVisibility(banners.isEmpty()?GONE:VISIBLE);dots.setVisibility(banners.size()>1?VISIBLE:GONE);
        pages.bringToFront();dots.bringToFront();pages.getAdapter().notifyDataSetChanged();pages.scrollToPosition(banners.size()>1?1:0);
        indicatedPage=-1;dots.removeAllViews();for(int i=0;i<banners.size();i++){View dot=new View(getContext());LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(dp(5),dp(5));params.setMargins(dp(3),0,dp(3),0);dot.setLayoutParams(params);dots.addView(dot);}
        updateDots();schedule();
    }
    public void setActive(boolean value){active=value;schedule();}
    public int currentPage(){View page=snap.findSnapView(layout);return page==null?0:logicalPage(layout.getPosition(page));}
    private int currentAdapterPage(){View page=snap.findSnapView(layout);return page==null?(banners.size()>1?1:0):layout.getPosition(page);}
    private int logicalPage(int position){return banners.size()>1?Math.floorMod(position-1,banners.size()):0;}
    private void normalizeLoopPosition(){
        if(banners.size()<2)return;
        int position=currentAdapterPage();
        // Identical edge copies make the repositioning invisible in both directions.
        if(position==0)layout.scrollToPositionWithOffset(banners.size(),0);
        else if(position==banners.size()+1)layout.scrollToPositionWithOffset(1,0);
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private boolean canAdvance(){return active&&visible&&isAttachedToWindow()&&!touching&&pages.getScrollState()==RecyclerView.SCROLL_STATE_IDLE&&banners.size()>1&&ValueAnimator.areAnimatorsEnabled()&&!((android.view.accessibility.AccessibilityManager)getContext().getSystemService(Context.ACCESSIBILITY_SERVICE)).isTouchExplorationEnabled();}
    private void schedule(){handler.removeCallbacks(advance);if(canAdvance())handler.postDelayed(advance,5000);}
    private void updateDots(){int current=currentPage();if(current==indicatedPage)return;indicatedPage=current;for(int i=0;i<dots.getChildCount();i++){GradientDrawable shape=new GradientDrawable();shape.setShape(GradientDrawable.OVAL);shape.setColor(i==current?Color.WHITE:0x66FFFFFF);dots.getChildAt(i).setBackground(shape);}}
    @Override public void onVisibilityAggregated(boolean value){super.onVisibilityAggregated(value);visible=value;schedule();}
    @Override protected void onDetachedFromWindow(){handler.removeCallbacks(advance);touching=false;super.onDetachedFromWindow();}
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        // Preserve the approved 1850:850 artwork without cropping on narrower phones.
        if(MeasureSpec.getMode(heightSpec)!=MeasureSpec.EXACTLY){
            int height=Math.round(MeasureSpec.getSize(widthSpec)*17f/37f);
            heightSpec=MeasureSpec.makeMeasureSpec(resolveSize(height,heightSpec),MeasureSpec.EXACTLY);
        }
        super.onMeasure(widthSpec,heightSpec);
    }
    @Override protected void onSizeChanged(int width,int height,int oldWidth,int oldHeight){super.onSizeChanged(width,height,oldWidth,oldHeight);if(width!=oldWidth)pages.getAdapter().notifyDataSetChanged();}
    private final class Pages extends RecyclerView.Adapter<Page>{
        @NonNull @Override public Page onCreateViewHolder(@NonNull ViewGroup parent,int type){ImageView image=new ImageView(parent.getContext());image.setLayoutParams(new RecyclerView.LayoutParams(-1,-1));image.setScaleType(ImageView.ScaleType.FIT_CENTER);return new Page(image);}
        @Override public void onBindViewHolder(@NonNull Page holder,int position){int logical=logicalPage(position);Banner banner=banners.get(logical);holder.image.setContentDescription(banner.description()+"，第"+(logical+1)+"张，共"+banners.size()+"张，可左右滑动");holder.image.setOnClickListener(v->{int index=holder.getBindingAdapterPosition();if(onBannerClick!=null&&index!=RecyclerView.NO_POSITION)onBannerClick.accept(logicalPage(index));});Glide.with(holder.image).load(banner.resource()).fitCenter().into(holder.image);}
        @Override public int getItemCount(){return banners.size()>1?banners.size()+2:banners.size();}
    }
    private static final class Page extends RecyclerView.ViewHolder{final ImageView image;Page(ImageView image){super(image);this.image=image;}}
}
