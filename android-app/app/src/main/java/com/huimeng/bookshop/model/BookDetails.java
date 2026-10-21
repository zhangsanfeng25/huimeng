package com.huimeng.bookshop.model;

import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalDate;

/** Optional bibliographic data; deliberately separate from cart/order Product values. */
public final class BookDetails {
    public static final String[] KEYS={"authorName","illustratorName","publisher","publicationDate","authorBio","illustratorBio","tableOfContents","editorialReview","metadataSource"};
    private final JSONObject data;
    public BookDetails(JSONObject data){this.data=data==null?new JSONObject():data;}
    public String text(String key){return data.isNull(key)?"":data.optString(key,"").trim();}
    public JSONArray chapters(){return chapters(data.opt("tableOfContents"));}
    public static JSONArray chapters(Object value){if(value instanceof JSONArray)return (JSONArray)value;if(value instanceof String)try{return new JSONArray((String)value);}catch(Exception ignored){}return new JSONArray();}
    public String authorText(){String author=text("authorName"),artist=text("illustratorName");return (author.isEmpty()?"暂未填写":author+" 文")+(artist.isEmpty()?"":" / "+artist+" 图");}
    public String dateText(){try{LocalDate date=LocalDate.parse(text("publicationDate"));return date.getYear()+"年"+date.getMonthValue()+"月"+date.getDayOfMonth()+"日";}catch(Exception ignored){return text("publicationDate").isEmpty()?"暂未填写":text("publicationDate");}}
    public String authorBody(){StringBuilder out=new StringBuilder();if(!text("authorName").isEmpty()||!text("authorBio").isEmpty())out.append(text("authorName")).append(" / 文字作者\n\n").append(text("authorBio").isEmpty()?"暂未填写简介":text("authorBio"));if(!text("illustratorName").isEmpty()||!text("illustratorBio").isEmpty()){if(out.length()>0)out.append("\n\n");out.append(text("illustratorName")).append(" / 绘图作者\n\n").append(text("illustratorBio").isEmpty()?"暂未填写简介":text("illustratorBio"));}return out.length()==0?"暂未填写作者资料":out.toString();}
    public static JSONObject inherit(JSONObject previous,JSONObject update)throws Exception{
        JSONObject result=new JSONObject(update.toString());if(previous!=null)for(String key:KEYS)if(!result.has(key)&&previous.has(key))result.put(key,previous.get(key));return result;
    }
    public static void validate(JSONObject value){
        String[] fields={"authorName","illustratorName","publisher","authorBio","illustratorBio","editorialReview"};int[] max={100,100,160,3000,3000,1500};
        for(int i=0;i<fields.length;i++)if(value.has(fields[i])&&(!(value.opt(fields[i]) instanceof String)||value.optString(fields[i]).trim().length()>max[i]))throw new IllegalArgumentException("图书资料过长或格式不正确");
        if(value.has("publicationDate")){Object raw=value.opt("publicationDate");if(!(raw instanceof String))throw new IllegalArgumentException("请填写有效发售日期");String date=(String)raw;if(!date.isEmpty())try{if(!date.matches("\\d{4}-\\d{2}-\\d{2}"))throw new IllegalArgumentException();LocalDate.parse(date);}catch(Exception error){throw new IllegalArgumentException("请填写有效发售日期，例如 2025-09-01");}}
        if(value.has("tableOfContents")){if(!(value.opt("tableOfContents") instanceof JSONArray))throw new IllegalArgumentException("目录格式不正确");JSONArray rows=value.optJSONArray("tableOfContents");if(rows.length()>80)throw new IllegalArgumentException("目录最多 80 项");for(int i=0;i<rows.length();i++)if(!(rows.opt(i) instanceof String)||rows.optString(i).trim().isEmpty()||rows.optString(i).trim().length()>180)throw new IllegalArgumentException("每项目录需为 1—180 字");}
        if(value.has("metadataSource")&&!java.util.Arrays.asList("","fictional_demo","manual").contains(value.optString("metadataSource")))throw new IllegalArgumentException("图书资料来源不正确");
    }
}
