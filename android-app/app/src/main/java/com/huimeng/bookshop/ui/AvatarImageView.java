package com.huimeng.bookshop.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.content.res.ColorStateList;
import androidx.core.content.ContextCompat;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.shape.RelativeCornerSize;
import com.huimeng.bookshop.R;

/** True circular mask and antialiased outline; separate from the brand icon shape. */
public final class AvatarImageView extends ShapeableImageView {
    public AvatarImageView(Context context) { this(context, null); }
    public AvatarImageView(Context context, AttributeSet attrs) { this(context, attrs, 0); }
    public AvatarImageView(Context context, AttributeSet attrs, int style) {
        super(context, attrs, style);
        setShapeAppearanceModel(getShapeAppearanceModel().toBuilder().setAllCornerSizes(new RelativeCornerSize(.5f)).build());
        setScaleType(ScaleType.CENTER_CROP);
        setBackground(null);
        int inset = Math.round(getResources().getDisplayMetrics().density * 2);
        setPadding(inset, inset, inset, inset);
        setStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.line_soft)));
        setStrokeWidth(getResources().getDisplayMetrics().density);
    }
}
