package com.huimeng.bookshop.ui;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.huimeng.bookshop.R;
import com.huimeng.bookshop.core.ApiClient;
import com.huimeng.bookshop.databinding.ActivityProductDetailBinding;
import com.huimeng.bookshop.model.BookDetails;
import com.huimeng.bookshop.model.Product;
import org.json.*;

/** Renders optional book content without owning purchase/favorite state. */
final class BookDetailsView {
    private final AppCompatActivity page;private final ActivityProductDetailBinding ui;private boolean expanded;
    BookDetailsView(AppCompatActivity page,ActivityProductDetailBinding ui,Bundle state){this.page=page;this.ui=ui;expanded=state!=null&&state.getBoolean("book_summary_expanded");
        ui.expandSummary.setOnClickListener(view->{expanded=!expanded;showSummary();});
        if(page.getResources().getConfiguration().screenWidthDp<360||page.getResources().getConfiguration().fontScale>1.15f){
            ui.bookHero.setOrientation(LinearLayout.VERTICAL);
            View cover=(View)ui.cover.getParent();LinearLayout.LayoutParams coverParams=(LinearLayout.LayoutParams)cover.getLayoutParams();coverParams.width=-1;coverParams.height=dp(200);cover.setLayoutParams(coverParams);
            FrameLayout.LayoutParams imageParams=(FrameLayout.LayoutParams)ui.cover.getLayoutParams();imageParams.width=dp(140);imageParams.gravity=android.view.Gravity.CENTER;ui.cover.setLayoutParams(imageParams);
            LinearLayout.LayoutParams infoParams=(LinearLayout.LayoutParams)ui.bookHeroInfo.getLayoutParams();infoParams.width=-1;infoParams.weight=0;infoParams.setMarginStart(0);infoParams.topMargin=dp(12);ui.bookHeroInfo.setLayoutParams(infoParams);
            ui.bookPreviewCards.setOrientation(LinearLayout.VERTICAL);for(View card:new View[]{ui.contentsCard,ui.authorCard}){LinearLayout.LayoutParams params=(LinearLayout.LayoutParams)card.getLayoutParams();params.width=-1;params.weight=0;params.setMargins(0,dp(12),0,0);card.setLayoutParams(params);}
        }
    }
    void save(Bundle state){state.putBoolean("book_summary_expanded",expanded);}
    void bind(JSONObject data){BookDetails book=new BookDetails(data);ui.bookMetadata.setText("作者："+book.authorText()+"\n出版社："+(book.text("publisher").isEmpty()?"暂未填写":book.text("publisher"))+"\n发售日期："+book.dateText());ui.demoHint.setVisibility("fictional_demo".equals(book.text("metadataSource"))?View.VISIBLE:View.GONE);
        JSONArray chapters=book.chapters();StringBuilder preview=new StringBuilder();for(int i=0;i<Math.min(3,chapters.length());i++){if(i>0)preview.append('\n');preview.append(i+1).append(". ").append(chapters.optString(i));}ui.contentsPreview.setText(preview.length()==0?"暂未填写目录":preview);ui.contentsCard.setEnabled(chapters.length()>0);ui.contentsAction.setVisibility(chapters.length()>0?View.VISIBLE:View.GONE);ui.contentsCard.setOnClickListener(view->BookDetailsSheet.open(page,"目录",chapters.toString(),true));
        boolean author=!book.text("authorName").isEmpty()||!book.text("authorBio").isEmpty()||!book.text("illustratorName").isEmpty()||!book.text("illustratorBio").isEmpty();ui.authorPreview.setText(author?book.authorText()+"\n"+(book.text("authorBio").isEmpty()?book.text("illustratorBio"):book.text("authorBio")):"暂未填写作者资料");ui.authorCard.setEnabled(author);ui.authorAction.setVisibility(author?View.VISIBLE:View.GONE);ui.authorCard.setOnClickListener(view->BookDetailsSheet.open(page,"作者简介",book.authorBody(),false));
        ui.editorialReview.setText(book.text("editorialReview"));ui.editorialCard.setVisibility(book.text("editorialReview").isEmpty()?View.GONE:View.VISIBLE);showSummary();ratings(data.optJSONArray("ratings"),data.optString("id"));recommendations(data.optJSONArray("recommendations"),data.optString("id"));
    }
    private void showSummary(){ui.summary.setMaxLines(expanded?Integer.MAX_VALUE:4);ui.summary.setEllipsize(expanded?null:android.text.TextUtils.TruncateAt.END);ui.expandSummary.setText(expanded?"收起全文 ⌃":"展开全文 ⌄");ui.summary.post(()->{var layout=ui.summary.getLayout();boolean clipped=layout!=null&&layout.getLineCount()>0&&layout.getEllipsisCount(layout.getLineCount()-1)>0;ui.expandSummary.setVisibility(expanded||clipped?View.VISIBLE:View.GONE);});}
    private void ratings(JSONArray rows,String id){ui.ratingsBox.removeAllViews();int count=rows==null?0:rows.length();ui.ratingsBox.addView(text("读者书评 · "+count+" 条",18,true));
        if(count==0)ui.ratingsBox.addView(text("暂时没有评价，期待你的阅读感受",14,false));
        for(int i=0;i<Math.min(count,2);i++){JSONObject row=rows.optJSONObject(i);if(row==null)continue;int stars=Math.max(0,Math.min(5,row.optInt("stars")));TextView name=text(row.optString("user")+"  "+"★★★★★".substring(0,stars)+" · "+stars+" 分",14,true);name.setPadding(0,dp(14),0,dp(4));ui.ratingsBox.addView(name);TextView body=text(row.optString("content"),15,false);body.setMaxLines(3);body.setEllipsize(android.text.TextUtils.TruncateAt.END);ui.ratingsBox.addView(body);}
        MaterialButton all=new MaterialButton(page,null,com.google.android.material.R.attr.materialButtonOutlinedStyle);all.setText("查看全部评价");all.setMinHeight(dp(48));all.setOnClickListener(view->page.startActivity(new Intent(page,RatingsActivity.class).putExtra("product_id",id)));ui.ratingsBox.addView(all);
    }
    private void recommendations(JSONArray rows,String currentId){ui.recommendationsBox.removeAllViews();java.util.Set<String> seen=new java.util.HashSet<>();LinearLayout line=null;int count=0,columns=page.getResources().getConfiguration().fontScale>1.15?1:2;
        if(rows!=null)for(int i=0;i<rows.length()&&count<4;i++){JSONObject row=rows.optJSONObject(i);if(row==null||row.optString("id").isEmpty()||currentId.equals(row.optString("id"))||row.optInt("stock")<1||!"ONLINE".equals(row.optString("status","ONLINE"))||!seen.add(row.optString("id")))continue;Product product=Product.from(row);
            if(count%columns==0){line=new LinearLayout(page);line.setOrientation(LinearLayout.HORIZONTAL);ui.recommendationsBox.addView(line,new LinearLayout.LayoutParams(-1,-2));}
            LinearLayout card=new LinearLayout(page);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(6),dp(8),dp(6),dp(12));LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1);line.addView(card,params);ImageView cover=new ImageView(page);cover.setContentDescription(product.name()+"封面");cover.setScaleType(ImageView.ScaleType.FIT_CENTER);card.addView(cover,new LinearLayout.LayoutParams(-1,dp(112)));Glide.with(page).load(ApiClient.assetUrl(product.coverUrl())).placeholder(new BookCoverDrawable(product.name())).error(new BookCoverDrawable(product.name())).fitCenter().into(cover);TextView name=text(product.name(),15,true);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);card.addView(name);TextView price=text(product.price(),16,true);price.setTextColor(ContextCompat.getColor(page,R.color.price_red));card.addView(price);card.setFocusable(true);card.setContentDescription("查看《"+product.name()+"》，"+product.price());card.setOnClickListener(view->page.startActivity(new Intent(page,ProductDetailActivity.class).putExtra("product_id",product.id())));count++;
        }
        if(line!=null&&count%columns!=0)line.addView(new View(page),new LinearLayout.LayoutParams(0,1,1));ui.similarCard.setVisibility(count==0?View.GONE:View.VISIBLE);
    }
    private TextView text(String value,int size,boolean bold){TextView text=new TextView(page);text.setText(value);text.setTextSize(size);text.setTextColor(ContextCompat.getColor(page,bold?R.color.ink:R.color.muted));text.setLineSpacing(dp(4),1);if(bold)text.setTypeface(null,android.graphics.Typeface.BOLD);return text;}
    private int dp(int value){return Math.round(value*page.getResources().getDisplayMetrics().density);}
}
