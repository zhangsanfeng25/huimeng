package com.huimeng.bookshop.ui;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.graphics.ColorUtils;
import androidx.lifecycle.Observer;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.huimeng.bookshop.R;
import com.huimeng.bookshop.core.AiConversation;
import com.huimeng.bookshop.core.ApiClient;
import com.huimeng.bookshop.core.SessionStore;
import com.huimeng.bookshop.databinding.SheetAiAssistantBinding;
import java.util.Locale;
import org.json.JSONObject;

final class AiAssistant {
    private final MainActivity activity;
    private final View bounds;
    private final ViewGroup root;
    private final MaterialButton ball;
    private final SharedPreferences placement;
    private final boolean merchant;
    private final AiConversation chat;
    private final Observer<Integer> updates = ignored -> render();
    private BottomSheetDialog dialog;
    private SheetAiAssistantBinding binding;
    private boolean fullScreen;
    private boolean right;
    private float position;
    private float startX, startY, touchX, touchY;
    private boolean dragging;
    private float previousMaxY = -1;

    AiAssistant(MainActivity activity, ViewGroup root, View bounds, SessionStore session) {
        this.activity = activity;
        this.root = root;
        this.bounds = bounds;
        merchant = "MERCHANT".equals(session.role());
        chat = AiConversation.current();
        placement = activity.getSharedPreferences("huimeng_ai_placement_" + session.role(), 0);
        right = placement.getBoolean("right", true);
        position = placement.getFloat("height", .70f);
        ball = new MaterialButton(activity);
        ball.setText("AI");
        ball.setContentDescription("打开绘梦 AI 助手");
        ball.setTextColor(activity.getColor(android.R.color.white));
        ball.setBackgroundTintList(ColorStateList.valueOf(activity.getColor(R.color.cool_green_dark)));
        ball.setCornerRadius(dp(27));
        ball.setInsetTop(0);
        ball.setInsetBottom(0);
        ball.setPadding(0,0,0,0);
        ball.setElevation(dp(8));
        root.addView(ball,new androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams(dp(54),dp(54)));
        ball.setVisibility(View.INVISIBLE);
        ball.setOnClickListener(view -> open());
        ball.setOnTouchListener(this::touchBall);
        root.addOnLayoutChangeListener((view,l,t,r,b,oldL,oldT,oldR,oldB)->{
            if (!dragging && (ball.getVisibility() != View.VISIBLE || previousMaxY != maxY() || r-l != oldR-oldL)) {
                previousMaxY = maxY();
                place();
            }
        });
        root.post(this::place);
        chat.updates().observe(activity, updates);
    }

    private float maxX() { return Math.max(dp(8),root.getWidth()-dp(8)-ball.getWidth()); }
    private float maxY() { return Math.max(dp(8),bounds.getBottom()-dp(8)-ball.getHeight()); }
    private static float clamp(float value,float min,float max) { return Math.min(max,Math.max(min,value)); }
    private void place() {
        if (ball.getWidth()==0 || bounds.getHeight()==0) return;
        ball.setX(right?maxX():dp(8));
        ball.setY(dp(8)+(maxY()-dp(8))*position);
        ball.setVisibility(View.VISIBLE);
    }

    private boolean touchBall(View view, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> {
                ball.animate().cancel();
                touchX=event.getRawX();
                touchY=event.getRawY();
                startX=ball.getX();
                startY=ball.getY();
                dragging=false;
                if(ValueAnimator.areAnimatorsEnabled())ball.animate().scaleX(.93f).scaleY(.93f).setDuration(85).start();
            }
            case MotionEvent.ACTION_MOVE -> {
                float x=event.getRawX()-touchX, y=event.getRawY()-touchY;
                if (Math.hypot(x,y)>ViewConfiguration.get(activity).getScaledTouchSlop()) dragging=true;
                if (dragging) {
                    ball.setX(clamp(startX+x,dp(8),maxX()));
                    ball.setY(clamp(startY+y,dp(8),maxY()));
                }
            }
            case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if(dragging){
                    right=ball.getX()+ball.getWidth()/2f>=root.getWidth()/2f;
                    position=clamp((ball.getY()-dp(8))/Math.max(1,maxY()-dp(8)),0,1);
                    placement.edit().putBoolean("right",right).putFloat("height",position).apply();
                }
                float targetX=right?maxX():dp(8);
                if(ValueAnimator.areAnimatorsEnabled())ball.animate().x(targetX).scaleX(1f).scaleY(1f).setDuration(165).start();
                else ball.setX(targetX);
                if(!dragging&&event.getActionMasked()==MotionEvent.ACTION_UP)view.performClick();
                dragging=false;
            }
            default -> { }
        }
        return true;
    }

    private void open() {
        if (dialog!=null || chat!=AiConversation.current()) return;
        dialog=new BottomSheetDialog(activity);
        dialog.setDismissWithAnimation(ValueAnimator.areAnimatorsEnabled());
        binding=SheetAiAssistantBinding.inflate(activity.getLayoutInflater());
        binding.historyScroll.setBackground(surface(activity.getColor(R.color.surface_soft)));
        binding.message.setBackground(surface(activity.getColor(R.color.surface_card)));
        dialog.setContentView(binding.getRoot());
        if(dialog.getWindow()!=null)dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        binding.close.setOnClickListener(view->close());
        binding.expand.setOnClickListener(view->{fullScreen=!fullScreen;sizeSheet();});
        binding.send.setOnClickListener(view->send(binding.message.getText().toString()));
        TapMotion.attach(binding.close);
        TapMotion.attach(binding.expand);
        TapMotion.attach(binding.send);
        binding.message.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text,int start,int count,int after) {}
            @Override public void onTextChanged(CharSequence text,int start,int before,int count) {
                chat.draft(text.toString());
                binding.send.setEnabled(!chat.waiting()&&!text.toString().trim().isEmpty());
            }
            @Override public void afterTextChanged(Editable text) {}
        });
        dialog.setOnDismissListener(ignored->{dialog=null;binding=null;});
        dialog.show();
        View sheet=dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if(sheet!=null&&sheet.getParent() instanceof View parent){
            parent.addOnLayoutChangeListener((view,left,top,right,bottom,oldLeft,oldTop,oldRight,oldBottom)->{
                if(bottom-top!=oldBottom-oldTop)sizeSheet();
            });
        }
        sizeSheet();
        render();
    }

    private void sizeSheet() {
        if (dialog==null || binding==null) return;
        View sheet=dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet==null) return;
        int available=root.getHeight();
        if(sheet.getParent() instanceof View parent&&parent.getHeight()>0)available=Math.min(available,parent.getHeight());
        ViewGroup.LayoutParams params=sheet.getLayoutParams();
        int height=fullScreen?available:Math.min(available,Math.max(dp(350),Math.round(available*.58f)));
        if(params.height!=height){params.height=height;sheet.setLayoutParams(params);}
        BottomSheetBehavior<?> behavior=dialog.getBehavior();
        behavior.setSkipCollapsed(true);
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        binding.expand.setText(fullScreen?"半屏":"全屏");
    }

    private void send(String text) {
        chat.send(new ApiClient(activity),text.trim());
    }

    private void render() {
        if(binding==null)return;
        binding.mode.setVisibility(chat.demo()?View.VISIBLE:View.GONE);
        if(!chat.draft().contentEquals(binding.message.getText())){
            binding.message.setText(chat.draft());
            binding.message.setSelection(chat.draft().length());
        }
        binding.message.setEnabled(!chat.waiting());
        binding.send.setEnabled(!chat.waiting()&&!chat.draft().trim().isEmpty());
        binding.loading.setVisibility(chat.waiting()?View.VISIBLE:View.GONE);
        binding.status.setText(chat.error());
        binding.status.setVisibility(chat.error().isEmpty()?View.GONE:View.VISIBLE);
        var history=binding.history;
        history.removeAllViews();
        if(chat.turns().isEmpty()){
            TextView welcome=text(merchant?"直接提问，例如「我店里有几件绘本缺货？」"
                :"直接提问，例如「推荐一本适合 5 岁孩子的绘本」",14,R.color.muted);
            welcome.setPadding(0,dp(4),0,dp(4));
            history.addView(welcome);
        }
        View latestQuestion=null;
        for(AiConversation.Turn turn:chat.turns()){
            View bubble=addTurn(turn);
            if(turn.user())latestQuestion=bubble;
        }
        if(latestQuestion!=null){
            var scroll=binding.historyScroll;
            View question=latestQuestion;
            scroll.post(()->scroll.scrollTo(0,history.getTop()+question.getTop()-dp(8)));
        }
    }

    private View addTurn(AiConversation.Turn turn) {
        LinearLayout bubble=new LinearLayout(activity);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setPadding(dp(12),dp(9),dp(12),dp(9));
        int cardColor=activity.getColor(R.color.surface_card);
        int bubbleColor=turn.user()
            ?ColorUtils.blendARGB(cardColor,activity.getColor(R.color.cool_green),.12f)
            :cardColor;
        bubble.setBackground(surface(bubbleColor));
        TextView message=text(turn.text(),15,R.color.ink);
        message.setLineSpacing(dp(3),1);
        bubble.addView(message);
        if(turn.failed()){
            TextView failed=text("发送失败",12,R.color.muted);
            failed.setPadding(0,dp(4),0,0);
            bubble.addView(failed);
        }
        boolean hasProducts=turn.products()!=null&&turn.products().length()>0;
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(hasProducts?-1:-2,-2);
        params.gravity=turn.user()?Gravity.END:Gravity.START;
        params.setMarginStart(turn.user()?dp(32):0);
        params.setMarginEnd(turn.user()?0:dp(32));
        params.topMargin=dp(4);
        params.bottomMargin=dp(4);
        binding.history.addView(bubble,params);
        if(hasProducts)for(int i=0;i<turn.products().length();i++){
            JSONObject product=turn.products().optJSONObject(i);
            if(product!=null&&!product.optString("id").isEmpty())addProduct(bubble,product);
        }
        return bubble;
    }

    private void addProduct(LinearLayout bubble,JSONObject product) {
        String name=product.optString("name");
        String price=String.format(Locale.CHINA,"¥%.2f",product.optInt("priceCents")/100d);
        View divider=new View(activity);
        divider.setBackgroundColor(activity.getColor(R.color.line_soft));
        LinearLayout.LayoutParams separator=new LinearLayout.LayoutParams(-1,dp(1));
        separator.topMargin=dp(8);
        bubble.addView(divider,separator);
        LinearLayout card=new LinearLayout(activity);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(0,dp(8),0,0);
        bubble.addView(card,new LinearLayout.LayoutParams(-1,-2));
        ImageView image=new ImageView(activity);
        image.setBackground(surface(activity.getColor(R.color.surface_soft)));
        image.setClipToOutline(true);
        card.addView(image,new LinearLayout.LayoutParams(dp(38),dp(52)));
        Glide.with(activity).load(ApiClient.assetUrl(product.optString("coverUrl")))
            .placeholder(new BookCoverDrawable(name)).error(new BookCoverDrawable(name)).centerCrop().into(image);
        LinearLayout info=new LinearLayout(activity);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPaddingRelative(dp(10),0,0,0);
        card.addView(info,new LinearLayout.LayoutParams(0,-2,1));
        TextView title=text(name+" · "+price,14,R.color.ink);
        title.setTypeface(null,android.graphics.Typeface.BOLD);
        info.addView(title);
        info.addView(text(product.optString("reason"),13,R.color.muted));
        card.setFocusable(true);
        card.setContentDescription("查看绘本："+name+"，"+String.format(Locale.CHINA,"%.2f 元",product.optInt("priceCents")/100d)
            +"，"+product.optString("reason"));
        card.setOnClickListener(view->{
            close();
            activity.startActivity(new Intent(activity,ProductDetailActivity.class)
                .putExtra("product_id",product.optString("id")).putExtra("from_ai",true));
        });
        TapMotion.attach(card);
    }

    void close() { if(dialog!=null)dialog.dismiss(); }
    void destroy() {
        chat.updates().removeObserver(updates);
        if(dialog!=null){dialog.setDismissWithAnimation(false);dialog.dismiss();}
    }

    private TextView text(String value,int size,int color) {
        TextView text=new TextView(activity);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(activity.getColor(color));
        return text;
    }

    private GradientDrawable surface(int color) {
        GradientDrawable background=new GradientDrawable();
        background.setCornerRadius(dp(12));
        background.setColor(color);
        background.setStroke(dp(1),activity.getColor(R.color.line_soft));
        return background;
    }

    private int dp(int value) { return Math.round(value*activity.getResources().getDisplayMetrics().density); }
}
