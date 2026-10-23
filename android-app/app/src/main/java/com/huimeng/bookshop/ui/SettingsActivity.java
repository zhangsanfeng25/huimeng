package com.huimeng.bookshop.ui;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.android.material.card.MaterialCardView;
import com.huimeng.bookshop.R;
import com.huimeng.bookshop.core.AppearanceStore;
import com.huimeng.bookshop.core.WallpaperStore;
import com.huimeng.bookshop.databinding.ActivitySettingsBinding;

public class SettingsActivity extends AppCompatActivity{
    private ActivitySettingsBinding binding;private AppearanceStore store;private WallpaperStore wallpapers;private boolean ready;
    @Override protected void onCreate(Bundle state){super.onCreate(state);
        binding=ActivitySettingsBinding.inflate(getLayoutInflater());setContentView(binding.getRoot());
        binding.localSync.setOnClickListener(v->startActivity(new android.content.Intent(this,new com.huimeng.bookshop.core.SessionStore(this).authenticated()?SyncStatusActivity.class:LoginActivity.class)));
        binding.changeAvatar.setOnClickListener(v->startActivity(new android.content.Intent(this,new com.huimeng.bookshop.core.SessionStore(this).authenticated()?EditAccountActivity.class:LoginActivity.class)));
        store=new AppearanceStore(this);wallpapers=new WallpaperStore(this);binding.back.setOnClickListener(v->finish());
        String value=store.theme();binding.themeGroup.check(AppearanceStore.LIGHT.equals(value)?binding.light.getId():AppearanceStore.DARK.equals(value)?binding.dark.getId():binding.system.getId());
        binding.wallpaperDefault.setOnClickListener(v->chooseWallpaper(WallpaperStore.SOLID));
        binding.wallpaperMint.setOnClickListener(v->chooseWallpaper(WallpaperStore.MINT));
        binding.wallpaperTwilight.setOnClickListener(v->chooseWallpaper(WallpaperStore.TWILIGHT));
        binding.wallpaperSunrise.setOnClickListener(v->chooseWallpaper(WallpaperStore.SUNRISE));
        binding.wallpaperOcean.setOnClickListener(v->chooseWallpaper(WallpaperStore.OCEAN));
        binding.wallpaperGarden.setOnClickListener(v->chooseWallpaper(WallpaperStore.GARDEN));
        showWallpaper(wallpapers.wallpaper());ready=true;
        binding.themeGroup.addOnButtonCheckedListener((group,id,checked)->{if(!ready||!checked)return;store.setTheme(id==binding.light.getId()?AppearanceStore.LIGHT:id==binding.dark.getId()?AppearanceStore.DARK:AppearanceStore.SYSTEM);});
    }
    private void chooseWallpaper(String value){if(value.equals(wallpapers.wallpaper()))value=WallpaperStore.DEFAULT;wallpapers.setWallpaper(value);wallpapers.apply(this);showWallpaper(value);}
    private void showWallpaper(String value){
        select(binding.wallpaperDefault,WallpaperStore.SOLID.equals(value));select(binding.wallpaperMint,WallpaperStore.MINT.equals(value));
        select(binding.wallpaperTwilight,WallpaperStore.TWILIGHT.equals(value));select(binding.wallpaperSunrise,WallpaperStore.SUNRISE.equals(value));
        select(binding.wallpaperOcean,WallpaperStore.OCEAN.equals(value));select(binding.wallpaperGarden,WallpaperStore.GARDEN.equals(value));
        String label=switch(value){case WallpaperStore.MINT->"薄荷晴空";case WallpaperStore.TWILIGHT->"紫蓝梦境";
            case WallpaperStore.SUNRISE->"暖霞书页";case WallpaperStore.OCEAN->"深海微光";case WallpaperStore.GARDEN->"绿野花园";case WallpaperStore.SOLID->"纯色背景";default->"未选择背景，跟随界面颜色";};
        binding.wallpaperHint.setText("当前："+label+"\n点击选中，再次点击取消");
    }
    private void select(MaterialCardView card,boolean selected){card.setSelected(selected);card.setCheckable(true);card.setCheckedIconResource(R.drawable.ic_book_check);card.setCheckedIconTint(null);card.setChecked(selected);String name=card==binding.wallpaperDefault?"纯色背景":card==binding.wallpaperMint?"薄荷晴空":card==binding.wallpaperTwilight?"紫蓝梦境":card==binding.wallpaperSunrise?"暖霞书页":card==binding.wallpaperOcean?"深海微光":"绿野花园";card.setContentDescription(name+(selected?"，已选中":"，未选中"));card.setStrokeColor(ContextCompat.getColor(this,selected?R.color.cool_green:R.color.line_soft));card.setStrokeWidth((int)(getResources().getDisplayMetrics().density*(selected?3:1)));}
}
