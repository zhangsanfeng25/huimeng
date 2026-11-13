package com.huimeng.bookshop.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.huimeng.bookshop.R;

/** Existing community and publishing flow, reached from merchant content operations. */
public final class MerchantCommunityActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle state){super.onCreate(state);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(getColor(R.color.milk_white));
        LinearLayout bar=new LinearLayout(this);bar.setGravity(android.view.Gravity.CENTER_VERTICAL);int padding=dp(18);bar.setPadding(padding,dp(8),padding,0);
        ImageButton back=new ImageButton(this);back.setImageResource(R.drawable.ic_back);back.setBackgroundResource(R.drawable.bg_search);back.setContentDescription("返回");back.setOnClickListener(v->finish());bar.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));
        TextView title=new TextView(this);title.setText("内容运营");title.setTextColor(getColor(R.color.ink));title.setTextSize(21);title.setPadding(dp(14),0,0,0);bar.addView(title);root.addView(bar);
        FrameLayout content=new FrameLayout(this);content.setId(R.id.merchantCommunityContainer);root.addView(content,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
        if(state==null)getSupportFragmentManager().beginTransaction().replace(content.getId(),new CommunityFragment()).commit();
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
