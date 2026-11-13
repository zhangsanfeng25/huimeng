package com.huimeng.bookshop.ui;

import android.os.Bundle;
import android.view.View;
import com.huimeng.bookshop.databinding.ActivityAddProductBinding;

/** Optional media folds independently of the permanently visible primary actions. */
final class ProductFormSections {
    private final ActivityAddProductBinding binding;
    ProductFormSections(ActivityAddProductBinding binding,Bundle state){
        this.binding=binding;
        expanded(state!=null&&state.getBoolean("media_expanded"));
        binding.productMediaTitle.setOnClickListener(v->expanded(binding.mediaFields.getVisibility()!=View.VISIBLE));
    }
    private void expanded(boolean value){
        binding.mediaFields.setVisibility(value?View.VISIBLE:View.GONE);
        binding.productMediaTitle.setText(value?"封面与详情图片（选填） ⌃":"封面与详情图片（选填） ⌄");
        binding.productMediaTitle.setContentDescription(value?"收起封面与详情图片":"展开封面与详情图片");
    }
    void save(Bundle state){state.putBoolean("media_expanded",binding.mediaFields.getVisibility()==View.VISIBLE);}
}
