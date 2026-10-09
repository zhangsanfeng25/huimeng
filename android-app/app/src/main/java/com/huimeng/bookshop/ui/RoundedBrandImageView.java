package com.huimeng.bookshop.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatImageView;

/** Shared brand treatment, including default avatars loaded through Glide. */
public class RoundedBrandImageView extends AppCompatImageView {
    private final Path corners = new Path();
    private final RectF imageBounds = new RectF();
    public RoundedBrandImageView(Context context) { super(context); }
    public RoundedBrandImageView(Context context, AttributeSet attrs) { super(context, attrs); }
    public RoundedBrandImageView(Context context, AttributeSet attrs, int style) { super(context, attrs, style); }

    @Override protected void onDraw(Canvas canvas) {
        imageBounds.set(getPaddingLeft(), getPaddingTop(), getWidth() - getPaddingRight(), getHeight() - getPaddingBottom());
        float radius = Math.min(imageBounds.width(), imageBounds.height()) * .24f;
        corners.reset();
        corners.addRoundRect(imageBounds, radius, radius, Path.Direction.CW);
        int saved = canvas.save();
        canvas.clipPath(corners);
        super.onDraw(canvas);
        canvas.restoreToCount(saved);
    }
}
