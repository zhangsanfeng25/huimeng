package com.huimeng.bookshop.core;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import androidx.core.content.ContextCompat;
import com.huimeng.bookshop.R;
import com.huimeng.bookshop.ui.WelcomeActivity;
import com.huimeng.bookshop.ui.LoginActivity;

public final class WallpaperStore{
    public static final String DEFAULT="DEFAULT",MINT="MINT",TWILIGHT="TWILIGHT",SUNRISE="SUNRISE",OCEAN="OCEAN",GARDEN="GARDEN";
    private final SharedPreferences preferences;
    public WallpaperStore(Context context){preferences=context.getSharedPreferences("huimeng_appearance",Context.MODE_PRIVATE);}
    public String wallpaper(){return preferences.getString("wallpaper",DEFAULT);}
    public void setWallpaper(String value){preferences.edit().putString("wallpaper",value).apply();}
    public void apply(Activity activity){
        if (activity instanceof WelcomeActivity || activity instanceof LoginActivity) return;
        String choice=wallpaper();
        boolean selected=!DEFAULT.equals(choice);
        if(selected){
            int resource=switch(choice){
                case MINT->R.drawable.wallpaper_mint_small;
                case SUNRISE->R.drawable.wallpaper_sunrise_small;
                case OCEAN->R.drawable.wallpaper_ocean_small;
                case GARDEN->R.drawable.wallpaper_garden_small;
                default->R.drawable.wallpaper_twilight_small;
            };
            Drawable picture=ContextCompat.getDrawable(activity,resource);
            if(picture instanceof BitmapDrawable bitmap)bitmap.setGravity(Gravity.FILL);
            boolean dark=(activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
            Drawable shade=new ColorDrawable(dark?0xAA0D1425:0x99FFFFFF);
            activity.getWindow().setBackgroundDrawable(new LayerDrawable(new Drawable[]{picture,shade}));
        }else{
            activity.getWindow().setBackgroundDrawable(new ColorDrawable(ContextCompat.getColor(activity,R.color.page_background)));
        }
        View content=activity.findViewById(android.R.id.content);
        if(content!=null)updatePageSurfaces(content,ContextCompat.getColor(activity,R.color.page_background),selected);
    }
    private void updatePageSurfaces(View view,int pageColor,boolean selected){
        Drawable background=view.getBackground();
        boolean marked=Boolean.TRUE.equals(view.getTag(R.id.wallpaper_surface_tag));
        if(marked || (background instanceof ColorDrawable && ((ColorDrawable)background).getColor()==pageColor)){
            view.setTag(R.id.wallpaper_surface_tag,Boolean.TRUE);
            view.setBackgroundColor(selected?Color.TRANSPARENT:pageColor);
        }
        if(view instanceof ViewGroup group){
            for(int i=0;i<group.getChildCount();i++)updatePageSurfaces(group.getChildAt(i),pageColor,selected);
        }
    }
}
