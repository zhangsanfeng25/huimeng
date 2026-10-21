package com.huimeng.bookshop.ui;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import com.google.android.material.button.MaterialButton;

/** Adds compact, accessible entries next to their existing business context. */
public final class ServiceEntries {
    private ServiceEntries(){}
    public static android.widget.TextView menuBefore(View anchor,String label,int icon,Runnable click){
        android.content.Context c=anchor.getContext();android.widget.TextView row=new android.widget.TextView(c,null,0,com.huimeng.bookshop.R.style.ProfileMenu);
        row.setText(label);row.setCompoundDrawablesWithIntrinsicBounds(icon,0,com.huimeng.bookshop.R.drawable.ic_chevron,0);row.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(c.getColor(com.huimeng.bookshop.R.color.cool_green_dark)));row.setCompoundDrawablePadding(Math.round(12*c.getResources().getDisplayMetrics().density));
        android.util.TypedValue value=new android.util.TypedValue();c.getTheme().resolveAttribute(android.R.attr.selectableItemBackground,value,true);row.setBackgroundResource(value.resourceId);row.setClickable(true);row.setFocusable(true);row.setOnClickListener(v->click.run());
        ViewGroup parent=(ViewGroup)anchor.getParent();parent.addView(row,parent.indexOfChild(anchor),new LinearLayout.LayoutParams(-1,Math.round(58*c.getResources().getDisplayMetrics().density)));return row;
    }
    public static MaterialButton compactBefore(View anchor,String label,Runnable click){MaterialButton b=before(anchor,label,click);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.gravity=android.view.Gravity.END;b.setLayoutParams(lp);b.setIconResource(com.huimeng.bookshop.R.drawable.ic_community);b.setIconSize(Math.round(18*anchor.getResources().getDisplayMetrics().density));return b;}
    public static MaterialButton messageTabs(View anchor,Runnable chat){
        var c=anchor.getContext();LinearLayout row=new LinearLayout(c);row.setOrientation(LinearLayout.HORIZONTAL);ViewGroup parent=(ViewGroup)anchor.getParent();LinearLayout.LayoutParams outer=new LinearLayout.LayoutParams(-1,-2);outer.topMargin=Math.round(12*c.getResources().getDisplayMetrics().density);parent.addView(row,parent.indexOfChild(anchor),outer);
        MaterialButton link=new MaterialButton(c,null,com.google.android.material.R.attr.materialButtonOutlinedStyle);link.setText("商家聊天");link.setTextSize(14);link.setIconResource(com.huimeng.bookshop.R.drawable.ic_community);link.setOnClickListener(v->chat.run());row.addView(link,new LinearLayout.LayoutParams(0,-2,1));
        android.widget.TextView selected=new android.widget.TextView(c);selected.setText("系统通知");selected.setSelected(true);selected.setTextSize(14);selected.setTypeface(null,android.graphics.Typeface.BOLD);selected.setGravity(android.view.Gravity.CENTER);selected.setTextColor(c.getColor(com.huimeng.bookshop.R.color.cool_green_dark));selected.setBackgroundResource(com.huimeng.bookshop.R.drawable.bg_search);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,Math.round(48*c.getResources().getDisplayMetrics().density),1);lp.leftMargin=Math.round(8*c.getResources().getDisplayMetrics().density);row.addView(selected,lp);return link;
    }
    public static MaterialButton[] orderActions(View timeline,String contact,Runnable contactAction,String sale,Runnable saleAction){
        var c=timeline.getContext();LinearLayout row=new LinearLayout(c);row.setOrientation(LinearLayout.HORIZONTAL);ViewGroup parent=(ViewGroup)timeline.getParent();LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(-1,-2);layout.topMargin=Math.round(16*c.getResources().getDisplayMetrics().density);parent.addView(row,Math.max(0,parent.indexOfChild(timeline)-1),layout);
        MaterialButton[] buttons=new MaterialButton[2];for(int i=0;i<2;i++){MaterialButton b=new MaterialButton(c,null,com.google.android.material.R.attr.materialButtonOutlinedStyle);b.setText(i==0?contact:sale);b.setTextColor(c.getColor(com.huimeng.bookshop.R.color.cool_green_dark));b.setTextSize(14);b.setMinWidth(0);b.setMinHeight(Math.round(48*c.getResources().getDisplayMetrics().density));b.setCornerRadius(Math.round(14*c.getResources().getDisplayMetrics().density));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);if(i==1)lp.leftMargin=Math.round(8*c.getResources().getDisplayMetrics().density);row.addView(b,lp);Runnable action=i==0?contactAction:saleAction;b.setOnClickListener(v->action.run());buttons[i]=b;}buttons[1].setVisibility(View.GONE);return buttons;
    }
    public static MaterialButton before(View anchor,String label,Runnable click){
        ViewGroup parent=(ViewGroup)anchor.getParent();MaterialButton button=new MaterialButton(anchor.getContext(),null,com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(label);button.setTextSize(14);button.setCornerRadius(Math.round(14*anchor.getResources().getDisplayMetrics().density));button.setMinHeight(Math.round(48*anchor.getResources().getDisplayMetrics().density));button.setOnClickListener(v->click.run());
        parent.addView(button,parent.indexOfChild(anchor),new LinearLayout.LayoutParams(-1,-2));return button;
    }
    public static void account(android.app.Activity activity,Runnable then){
        if(!new com.huimeng.bookshop.core.SessionStore(activity).token().isEmpty()){then.run();return;}
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(activity).setTitle("当前操作仅会保存在手机")
            .setMessage("当前网络账号未登录。仅在本地保存的订单不会显示在管理员端。登录原账号后才能上传并确认订单与付款；也可以继续保存离线意向。")
            .setNegativeButton("继续保存本地",(d,w)->then.run())
            .setPositiveButton("登录原账号并同步",(d,w)->activity.startActivity(new android.content.Intent(activity,LoginActivity.class).putExtra("login_notice","登录原账号后继续同步本地订单"))).show();
    }
}
