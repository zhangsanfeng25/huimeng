package com.huimeng.bookshop.ui;

import android.view.MotionEvent;
import android.view.View;

/** Small touch feedback without intercepting normal click or accessibility events. */
public final class TapMotion {
    private TapMotion(){}
    public static void attach(View view){view.setOnTouchListener((v,event)->{
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN)v.animate().scaleX(.94f).scaleY(.94f).setDuration(90).start();
        else if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL)
            v.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
        return false;
    });}
}
