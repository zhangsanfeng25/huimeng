package com.huimeng.bookshop.ui;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.huimeng.bookshop.R;
import org.json.JSONArray;

/** Arguments carry immutable display data, so FragmentManager can recreate an open sheet. */
public class BookDetailsSheet extends BottomSheetDialogFragment {
    private ScrollView scroll;private int savedScroll;
    public static void open(androidx.fragment.app.FragmentActivity page,String title,String content,boolean directory){
        var manager=page.getSupportFragmentManager();if(manager.isStateSaved()||manager.findFragmentByTag("book-details-sheet")!=null)return;
        BookDetailsSheet sheet=new BookDetailsSheet();Bundle args=new Bundle();args.putString("title",title);args.putString("content",content);args.putBoolean("directory",directory);sheet.setArguments(args);sheet.show(manager,"book-details-sheet");
    }
    @Override public void onCreate(Bundle state){super.onCreate(state);savedScroll=state==null?0:state.getInt("scroll");}
    @NonNull @Override public Dialog onCreateDialog(Bundle state){
        BottomSheetDialog dialog=new BottomSheetDialog(requireContext(),getTheme());LinearLayout root=new LinearLayout(requireContext());root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(22),dp(12),dp(22),dp(20));
        View handle=new View(requireContext());android.graphics.drawable.GradientDrawable pill=new android.graphics.drawable.GradientDrawable();pill.setColor(color(R.color.line_soft));pill.setCornerRadius(dp(3));handle.setBackground(pill);LinearLayout.LayoutParams grip=new LinearLayout.LayoutParams(dp(40),dp(5));grip.gravity=android.view.Gravity.CENTER_HORIZONTAL;grip.bottomMargin=dp(8);root.addView(handle,grip);
        LinearLayout heading=new LinearLayout(requireContext());heading.setGravity(android.view.Gravity.CENTER_VERTICAL);TextView title=text(requireArguments().getString("title"),23,true);heading.addView(title,new LinearLayout.LayoutParams(0,dp(56),1));title.setGravity(android.view.Gravity.CENTER_VERTICAL);
        ImageButton close=new ImageButton(requireContext());close.setImageResource(R.drawable.ic_close);close.setContentDescription("关闭"+requireArguments().getString("title"));close.setBackgroundColor(android.graphics.Color.TRANSPARENT);close.setColorFilter(color(R.color.muted));heading.addView(close,new LinearLayout.LayoutParams(dp(48),dp(48)));close.setOnClickListener(view->dismiss());root.addView(heading);
        scroll=new ScrollView(requireContext());scroll.setFillViewport(false);LinearLayout body=new LinearLayout(requireContext());body.setOrientation(LinearLayout.VERTICAL);body.setPadding(0,dp(8),0,dp(8));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,-2));String content=requireArguments().getString("content","");
        if(requireArguments().getBoolean("directory")){
            JSONArray rows=com.huimeng.bookshop.model.BookDetails.chapters(content);
            for(int i=0;i<rows.length();i++){
                LinearLayout line=new LinearLayout(requireContext());line.setGravity(android.view.Gravity.CENTER_VERTICAL);line.setMinimumHeight(dp(58));TextView number=text(String.valueOf(i+1),15,false);number.setTextColor(color(R.color.muted));line.addView(number,new LinearLayout.LayoutParams(dp(30),-2));TextView entry=text(rows.optString(i),17,false);entry.setPadding(0,dp(12),dp(8),dp(12));line.addView(entry,new LinearLayout.LayoutParams(-2,-2));
                View leaders=new View(requireContext());android.graphics.drawable.GradientDrawable dotted=new android.graphics.drawable.GradientDrawable();dotted.setShape(android.graphics.drawable.GradientDrawable.LINE);dotted.setStroke(dp(1),color(R.color.line_soft),dp(2),dp(3));leaders.setBackground(dotted);line.addView(leaders,new LinearLayout.LayoutParams(0,dp(1),1));body.addView(line);
            }
            if(rows.length()==0)body.addView(text("暂未填写目录",16,false));
        }else{TextView copy=text(content,17,false);copy.setLineSpacing(dp(6),1);copy.setTextIsSelectable(true);body.addView(copy);}
        dialog.setContentView(root);dialog.setCanceledOnTouchOutside(true);ViewCompat.setOnApplyWindowInsetsListener(root,(view,insets)->{int bottom=insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;view.setPadding(dp(22),dp(12),dp(22),Math.max(dp(20),bottom+dp(12)));return insets;});return dialog;
    }
    @Override public void onStart(){super.onStart();BottomSheetDialog dialog=(BottomSheetDialog)requireDialog();FrameLayout sheet=dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);if(sheet!=null){MaterialShapeDrawable background=new MaterialShapeDrawable(ShapeAppearanceModel.builder().setTopLeftCornerSize(dp(28)).setTopRightCornerSize(dp(28)).build());background.setFillColor(android.content.res.ColorStateList.valueOf(color(R.color.surface_card)));sheet.setBackground(background);ViewCompat.setBackgroundTintList(sheet,android.content.res.ColorStateList.valueOf(color(R.color.surface_card)));int maximum=(int)(getResources().getDisplayMetrics().heightPixels*.85f);dialog.getBehavior().setMaxHeight(maximum);dialog.getBehavior().setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);sheet.post(()->{scroll.getLayoutParams().height=Math.min(scroll.getChildAt(0).getHeight(),Math.max(dp(100),maximum-dp(130)));scroll.requestLayout();scroll.post(()->scroll.scrollTo(0,savedScroll));});}if(dialog.getWindow()!=null)dialog.getWindow().setDimAmount(.42f);}
    @Override public void onSaveInstanceState(@NonNull Bundle state){state.putInt("scroll",scroll==null?savedScroll:scroll.getScrollY());super.onSaveInstanceState(state);}
    private TextView text(String value,int sp,boolean bold){TextView view=new TextView(requireContext());view.setText(value);view.setTextColor(color(R.color.ink));view.setTextSize(sp);if(bold)view.setTypeface(null,android.graphics.Typeface.BOLD);return view;}
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}private int color(int id){return ContextCompat.getColor(requireContext(),id);}
}
