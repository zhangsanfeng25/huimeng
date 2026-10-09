package com.huimeng.bookshop.ui;

import android.graphics.drawable.Drawable;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import com.huimeng.bookshop.R;
import com.huimeng.bookshop.model.CommunityPost;

/** A count from other readers never implies that the current reader liked a post. */
final class PostLikeDisplay {
    private PostLikeDisplay(){}
    static void bind(TextView view,CommunityPost post,boolean comments){
        view.setText(String.valueOf(post.likeCount())+(comments?"  ·  "+post.commentCount()+" 条评论":""));
        Drawable heart=ContextCompat.getDrawable(view.getContext(),post.liked()?R.drawable.ic_heart_filled:R.drawable.ic_heart).mutate();
        heart.setTint(ContextCompat.getColor(view.getContext(),post.liked()?R.color.price_red:R.color.muted));
        int size=Math.round(14*view.getResources().getDisplayMetrics().density);heart.setBounds(0,0,size,size);
        view.setCompoundDrawablesRelative(heart,null,null,null);view.setCompoundDrawablePadding(Math.round(4*view.getResources().getDisplayMetrics().density));
        view.setContentDescription((post.liked()?"已点赞":"未点赞")+"，获赞 "+post.likeCount()+"，评论 "+post.commentCount());
    }
}
