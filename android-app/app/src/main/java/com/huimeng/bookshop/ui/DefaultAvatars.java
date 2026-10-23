package com.huimeng.bookshop.ui;

import android.widget.ImageView;
import com.bumptech.glide.Glide;
import com.huimeng.bookshop.R;
import com.huimeng.bookshop.core.ApiClient;

public final class DefaultAvatars {
    public record Option(String id, String name, int drawable) {
        public String url() { return "/public/default-avatars/" + id + ".png"; }
    }
    public static final Option[] OPTIONS = {
        new Option("sprout", "书页新芽", R.drawable.avatar_sprout),
        new Option("whale", "星月鲸鱼", R.drawable.avatar_whale),
        new Option("house", "阅读小屋", R.drawable.avatar_house),
        new Option("fox", "狐狸读者", R.drawable.avatar_fox),
        new Option("cat", "眼镜小猫", R.drawable.avatar_cat)
    };
    public static void show(ImageView view, String url) {
        if(url==null||url.trim().isEmpty()){Glide.with(view).clear(view);view.setImageResource(R.drawable.ic_avatar_placeholder);return;}
        Object image = ApiClient.assetUrl(url == null ? "" : url);
        for (Option option : OPTIONS) if (option.url().equals(url)) { image = option.drawable(); break; }
        Glide.with(view).load(image).override(600, 600).placeholder(R.drawable.ic_avatar_placeholder)
            .error(R.drawable.ic_avatar_placeholder).circleCrop().into(view);
    }
    private DefaultAvatars() {}
}
