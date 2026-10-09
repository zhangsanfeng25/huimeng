package com.huimeng.bookshop.core;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;

public final class AppearanceStore{
    public static final String SYSTEM="SYSTEM",LIGHT="LIGHT",DARK="DARK";
    private final SharedPreferences preferences;
    public AppearanceStore(Context context){preferences=context.getSharedPreferences("huimeng_appearance",Context.MODE_PRIVATE);}
    public String theme(){return preferences.getString("theme",SYSTEM);}
    public void setTheme(String value){preferences.edit().putString("theme",value).apply();apply();}
    public void apply(){int mode=switch(theme()){case LIGHT->AppCompatDelegate.MODE_NIGHT_NO;case DARK->AppCompatDelegate.MODE_NIGHT_YES;default->AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;};AppCompatDelegate.setDefaultNightMode(mode);}
}
