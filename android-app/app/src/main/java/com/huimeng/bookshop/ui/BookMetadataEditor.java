package com.huimeng.bookshop.ui;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.core.content.ContextCompat;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.huimeng.bookshop.R;
import com.huimeng.bookshop.model.BookDetails;
import org.json.*;
import java.util.LinkedHashMap;
final class BookMetadataEditor {
    private final LinkedHashMap<String,TextInputEditText> fields=new LinkedHashMap<>();private final LinearLayout body;private final TextView toggle;private Bundle restored;private String source="";
    BookMetadataEditor(Context context,View anchor,Bundle state){
        LinearLayout container=(LinearLayout)anchor.getParent();LinearLayout group=new LinearLayout(context);group.setOrientation(LinearLayout.VERTICAL);toggle=new TextView(context);toggle.setTextColor(ContextCompat.getColor(context,R.color.cool_green_dark));toggle.setTextSize(16);toggle.setMinHeight(dp(context,48));toggle.setGravity(android.view.Gravity.CENTER_VERTICAL);toggle.setFocusable(true);toggle.setText("图书资料（选填） ⌄");group.addView(toggle);body=new LinearLayout(context);body.setOrientation(LinearLayout.VERTICAL);body.setVisibility(View.GONE);group.addView(body);toggle.setOnClickListener(view->expanded(body.getVisibility()!=View.VISIBLE));
        String[][] entries={{"authorName","文字作者"},{"illustratorName","绘图作者"},{"publisher","发行出版社"},{"publicationDate","发售日期 YYYY-MM-DD"},{"authorBio","作者简介"},{"illustratorBio","绘图作者简介"},{"tableOfContents","图书目录（每行一项，最多80项）"},{"editorialReview","编辑推荐"}};
        for(String[] entry:entries){TextInputLayout box=new TextInputLayout(context);box.setHint(entry[1]);TextInputEditText input=new TextInputEditText(context);input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|(entry[0].endsWith("Bio")||entry[0].equals("tableOfContents")||entry[0].equals("editorialReview")?android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE:0));if((input.getInputType()&android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE)!=0)input.setMinLines(3);int max=entry[0].endsWith("Bio")?3000:entry[0].equals("editorialReview")?1500:entry[0].equals("tableOfContents")?14500:entry[0].equals("publisher")?160:entry[0].equals("publicationDate")?10:100;input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(max)});box.addView(input,new LinearLayout.LayoutParams(-1,-2));LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.bottomMargin=dp(context,10);body.addView(box,params);fields.put(entry[0],input);}
        container.addView(group,container.indexOfChild(anchor));restored=state==null?null:state.getBundle("book_editor");if(restored!=null){for(String key:fields.keySet())fields.get(key).setText(restored.getString(key,""));source=restored.getString("metadataSource","");expanded(restored.getBoolean("expanded"));}
    }
    void setData(JSONObject value){if(restored!=null){restored=null;return;}BookDetails details=new BookDetails(value);source=details.text("metadataSource");for(String key:fields.keySet()){if(key.equals("tableOfContents")){StringBuilder text=new StringBuilder();JSONArray rows=details.chapters();for(int i=0;i<rows.length();i++){if(i>0)text.append('\n');text.append(rows.optString(i));}fields.get(key).setText(text);}else fields.get(key).setText(details.text(key));}}
    JSONObject applyTo(JSONObject value)throws Exception{for(String key:fields.keySet()){String text=String.valueOf(fields.get(key).getText()).trim();if(key.equals("tableOfContents")){JSONArray rows=new JSONArray();if(!text.isEmpty())for(String line:text.split("\\r?\\n",-1))if(!line.trim().isEmpty())rows.put(line.trim());value.put(key,rows);}else value.put(key,text);}if(!source.isEmpty())value.put("metadataSource",source);BookDetails.validate(value);return value;}
    void setEnabled(boolean enabled){for(TextInputEditText input:fields.values())input.setEnabled(enabled);}
    void save(Bundle state){Bundle values=new Bundle();for(String key:fields.keySet())values.putString(key,String.valueOf(fields.get(key).getText()));values.putString("metadataSource",source);values.putBoolean("expanded",body.getVisibility()==View.VISIBLE);state.putBundle("book_editor",values);}
    private void expanded(boolean value){body.setVisibility(value?View.VISIBLE:View.GONE);toggle.setText(value?"图书资料（选填） ⌃":"图书资料（选填） ⌄");}
    private int dp(Context context,int value){return Math.round(context.getResources().getDisplayMetrics().density*value);}
}
