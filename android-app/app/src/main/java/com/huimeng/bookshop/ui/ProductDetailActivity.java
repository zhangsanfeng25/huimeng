package com.huimeng.bookshop.ui;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.bumptech.glide.Glide;
import com.huimeng.bookshop.R;
import com.huimeng.bookshop.core.ApiClient;
import com.huimeng.bookshop.core.LocalCartStore;
import com.huimeng.bookshop.databinding.ActivityProductDetailBinding;
import com.huimeng.bookshop.model.Product;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class ProductDetailActivity extends AppCompatActivity{
    private ActivityProductDetailBinding binding;private String productId;private BookDetailsView bookDetails;
    private boolean favorite,busy,adding;private String serviceMerchant="";
    private final List<String> images=new ArrayList<>();private int imageIndex;private String bookName="绘本";private Product product;
    @Override protected void onCreate(Bundle state){super.onCreate(state);binding=ActivityProductDetailBinding.inflate(getLayoutInflater());setContentView(binding.getRoot());
        bookDetails=new BookDetailsView(this,binding,state);productId=getIntent().getStringExtra("product_id");
        binding.addCart.setEnabled(false);binding.buyNow.setEnabled(false);binding.favorite.setEnabled(false);
        binding.back.setOnClickListener(v->finish());binding.favorite.setOnClickListener(v->toggleFavorite());
        binding.addCart.setOnClickListener(v->addCart(null));
        binding.buyNow.setOnClickListener(v->buyNow());
        ServiceEntries.compactBefore(binding.galleryHint,"联系商家",()->{if(!serviceMerchant.isEmpty())ServiceActivity.contact(this,serviceMerchant,null,productId);});
        GestureDetector gestures=new GestureDetector(this,new GestureDetector.SimpleOnGestureListener(){
            @Override public boolean onDown(MotionEvent event){return true;}
            @Override public boolean onSingleTapUp(MotionEvent event){zoomImage();return true;}
            @Override public boolean onFling(MotionEvent first,MotionEvent last,float speedX,float speedY){if(images.size()>1&&Math.abs(last.getX()-first.getX())>60){imageIndex=(imageIndex+(last.getX()<first.getX()?1:images.size()-1))%images.size();showImage();return true;}return false;}
        });
        binding.cover.setOnTouchListener((view,event)->gestures.onTouchEvent(event));
        TapMotion.attach(binding.back);TapMotion.attach(binding.favorite);TapMotion.attach(binding.addCart);
        TapMotion.attach(binding.buyNow);
        renderFavorite();load();
    }
    @Override protected void onResume(){super.onResume();loadFavorite();}
    private void load(){new ApiClient(this).get("catalog/products/"+productId,result->{JSONObject data=result.optJSONObject("data");if(data==null)return;
        Product item=Product.from(data);product=item;serviceMerchant=data.optString("merchantId");bookName=item.name();binding.favorite.setEnabled(true);binding.loading.setVisibility(View.GONE);binding.content.setVisibility(View.VISIBLE);
        binding.name.setText(item.name());binding.summary.setText(item.summary());binding.price.setText(item.price());binding.shop.setText(item.shop());
        StringBuilder tags=new StringBuilder();if(!item.ageRange().isEmpty())tags.append("适读 ").append(item.ageRange());if(!item.category().isEmpty()){if(tags.length()>0)tags.append("  ·  ");tags.append(item.category());}if(tags.length()>0)tags.append("  ·  ");tags.append("库存 ").append(item.stock());binding.tags.setText(tags);
        binding.addCart.setEnabled(item.stock()>0);binding.buyNow.setEnabled(item.stock()>0);binding.buyNow.setText(item.stock()>0?"立即购买":"已售罄");
        images.clear();if(!item.coverUrl().isEmpty())images.add(item.coverUrl());
        JSONArray detail=data.optJSONArray("detailImages");if(detail==null)try{detail=new JSONArray(data.optString("detailImages","[]"));}catch(Exception ignored){}
        if(detail!=null)for(int i=0;i<detail.length();i++){String url=detail.optString(i);if(!url.isEmpty()&&!images.contains(url))images.add(url);}
        imageIndex=0;showImage();binding.galleryHint.setVisibility(images.size()>1?View.VISIBLE:View.GONE);
        bookDetails.bind(data);
    },error->{binding.loading.setVisibility(View.GONE);binding.error.setText(error);binding.error.setVisibility(View.VISIBLE);});}
    private void loadFavorite(){new ApiClient(this).get("me/favorites",result->{JSONArray rows=result.optJSONArray("data");
        boolean found=false;if(rows!=null)for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row!=null&&productId.equals(row.optString("id"))){found=true;break;}}
        favorite=found;renderFavorite();},error->{});}
    private void renderFavorite(){binding.favorite.setText(favorite?"已收藏":"收藏");
        binding.favorite.setContentDescription(favorite?"取消收藏":"收藏绘本");
        binding.favorite.setCompoundDrawablesWithIntrinsicBounds(favorite?R.drawable.ic_heart_filled:R.drawable.ic_heart,0,0,0);
        binding.favorite.setTextColor(ContextCompat.getColor(this,favorite?R.color.price_red:R.color.cool_green_dark));}
    private void toggleFavorite(){if(busy)return;busy=true;
        if(favorite)new ApiClient(this).delete("me/favorites/"+productId,result->changeFavorite(false),this::favoriteError);
        else new ApiClient(this).post("me/favorites/"+productId,new JSONObject(),result->changeFavorite(true),this::favoriteError);
    }
    private void changeFavorite(boolean value){busy=false;favorite=value;renderFavorite();
        if(!ValueAnimator.areAnimatorsEnabled())return;
        ValueAnimator pulse=ValueAnimator.ofFloat(1f,.75f,1.2f,1f);pulse.setDuration(300);
        pulse.addUpdateListener(anim->{float scale=(float)anim.getAnimatedValue();binding.favorite.setScaleX(scale);binding.favorite.setScaleY(scale);});pulse.start();
    }
    private void favoriteError(String message){busy=false;Toast.makeText(this,message,Toast.LENGTH_SHORT).show();}
    private void addCart(Runnable done){if(adding||product==null)return;adding=true;binding.addCart.setEnabled(false);binding.buyNow.setEnabled(false);
        LocalCartStore.resolve(this,cart->{int quantity=cart.quantity(productId);
            if(quantity>=Math.min(99,product.stock())){cartError("已达到当前库存上限");return;}
            int next=quantity+1;try{JSONObject body=new JSONObject().put("quantity",next).put("selected",true);
                new ApiClient(this).put("me/cart/"+productId,body,result->{cart.upsert(product,next,true);adding=false;binding.addCart.setEnabled(true);binding.buyNow.setEnabled(true);if(done==null)flyCover();Toast.makeText(this,"已加入购物车",Toast.LENGTH_SHORT).show();if(done!=null)done.run();},this::cartError);
            }catch(Exception error){cartError("购物车更新失败");}
        },this::cartError);}
    private void cartError(String message){adding=false;binding.addCart.setEnabled(true);binding.buyNow.setEnabled(true);Toast.makeText(this,message,Toast.LENGTH_SHORT).show();}
    private void showImage(){binding.imageCount.setVisibility(images.size()>1?View.VISIBLE:View.GONE);binding.imageCount.setText((imageIndex+1)+" / "+images.size());
        Glide.with(this).load(images.isEmpty()?"":ApiClient.assetUrl(images.get(imageIndex))).placeholder(new BookCoverDrawable(bookName)).error(new BookCoverDrawable(bookName)).fitCenter().into(binding.cover);}
    private void zoomImage(){if(images.isEmpty())return;ImageView image=new ImageView(this);image.setBackgroundColor(0xFF171F26);image.setPadding(20,20,20,20);image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Glide.with(this).load(ApiClient.assetUrl(images.get(imageIndex))).placeholder(new BookCoverDrawable(bookName)).error(new BookCoverDrawable(bookName)).into(image);
        android.app.Dialog dialog=new android.app.Dialog(this,android.R.style.Theme_Black_NoTitleBar_Fullscreen);dialog.setContentView(image);image.setOnClickListener(v->dialog.dismiss());dialog.show();}
    private void flyCover(){if(images.isEmpty()||!ValueAnimator.areAnimatorsEnabled())return;ImageView copy=new ImageView(this);copy.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Glide.with(this).load(ApiClient.assetUrl(images.get(0))).placeholder(new BookCoverDrawable(bookName)).error(new BookCoverDrawable(bookName)).into(copy);
        android.widget.FrameLayout root=(android.widget.FrameLayout)binding.getRoot();int[] start=new int[2],end=new int[2],origin=new int[2];binding.cover.getLocationOnScreen(start);binding.addCart.getLocationOnScreen(end);root.getLocationOnScreen(origin);
        int w=Math.round(72*getResources().getDisplayMetrics().density),h=Math.round(96*getResources().getDisplayMetrics().density);
        android.widget.FrameLayout.LayoutParams params=new android.widget.FrameLayout.LayoutParams(w,h);params.leftMargin=start[0]-origin[0]+binding.cover.getWidth()/2-w/2;params.topMargin=start[1]-origin[1]+binding.cover.getHeight()/2-h/2;root.addView(copy,params);
        copy.animate().x(end[0]-origin[0]+binding.addCart.getWidth()/2-24).y(end[1]-origin[1]+12).scaleX(.4f).scaleY(.4f).alpha(.2f).setDuration(450).withEndAction(()->root.removeView(copy)).start();}
    private void buyNow(){if(product==null||product.stock()<1)return;android.widget.NumberPicker quantity=new android.widget.NumberPicker(this);quantity.setMinValue(1);quantity.setMaxValue(Math.min(99,product.stock()));quantity.setWrapSelectorWheel(false);new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle("选择购买数量").setView(quantity).setNegativeButton("取消",null).setPositiveButton("确认购买",(dialog,which)->startActivity(new Intent(this,CheckoutActivity.class).putExtra("product_id",productId).putExtra("quantity",quantity.getValue()))).show();}
    @Override protected void onSaveInstanceState(Bundle state){bookDetails.save(state);super.onSaveInstanceState(state);}
}
