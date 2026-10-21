package com.huimeng.bookshop.core.local;
import org.json.*;
import android.net.Uri;
public final class CatalogRepository extends BusinessRepository {
    public CatalogRepository(LocalDatabase db,String space){super(db,space);}
    public JSONObject handle(int method,String path,JSONObject body)throws Exception{
        if(method!=0||!path.startsWith("catalog/"))return null;
        if(path.equals("catalog/categories"))return data(db.list(space,"categories"));
        String clean=path.split("\\?",2)[0];
        if(clean.equals("catalog/products")){
            Uri uri=Uri.parse("http://local/"+path);String q=parameter(uri,"q"),category=parameter(uri,"category"),age=parameter(uri,"age"),theme=parameter(uri,"theme"),sort=parameter(uri,"sort");JSONArray rows=db.list(space,"products");java.util.List<JSONObject> matches=new java.util.ArrayList<>();
            if(q.length()>100||!age.matches("(\\d{1,2}-(\\d{1,2}|plus))?")||!java.util.Arrays.asList("","bedtime","emotion","science").contains(theme)||!java.util.Arrays.asList("","comprehensive","newest","price_asc","price_desc").contains(sort))throw new IllegalArgumentException("筛选或排序参数不正确");
            String[] terms=switch(theme){case "bedtime"->new String[]{"睡前","晚安","安睡"};case "emotion"->new String[]{"情绪","朋友","勇敢","成长","上学","分享"};case "science"->new String[]{"科普","百科","海洋","昆虫","恐龙","地球","星空","四季"};default->new String[]{};};
            for(int i=0;i<rows.length();i++){JSONObject p=rows.getJSONObject(i);String content=p.optString("name")+" "+p.optString("summary")+" "+p.optString("category");if(!"ONLINE".equals(p.optString("status","ONLINE")))continue;if(!q.isEmpty()&&!content.toLowerCase(java.util.Locale.ROOT).contains(q.trim().toLowerCase(java.util.Locale.ROOT)))continue;if(!category.isEmpty()&&!category.equals(p.optString("categoryId")))continue;if(!matchesAge(p.optString("ageRange"),age))continue;boolean themed=terms.length==0;for(String term:terms)if(content.contains(term))themed=true;if(themed)matches.add(p);}
            matches.sort((a,b)->{int compared=sort.equals("price_asc")?Long.compare(a.optLong("priceCents"),b.optLong("priceCents")):sort.equals("price_desc")?Long.compare(b.optLong("priceCents"),a.optLong("priceCents")):b.optString(sort.equals("newest")?"createdAt":"updatedAt").compareTo(a.optString(sort.equals("newest")?"createdAt":"updatedAt"));return compared==0?a.optString("id").compareTo(b.optString("id")):compared;});
            return paginate(uri,new JSONArray(matches));
        }
        String[] parts=clean.split("/");if(parts.length>=3){JSONArray ratings=filter(db.list(space,"productRatings"),"productId",parts[2]);if(parts.length==4&&parts[3].equals("ratings"))return paginate(Uri.parse("http://local/"+path),ratings);JSONObject value=require("products",parts[2]);if(!"ONLINE".equals(value.optString("status","ONLINE")))throw new IllegalArgumentException("商品已下架");value.put("ratings",ratings).put("ratingCount",ratings.length()).put("recommendations",recommendations(value));return data(value);}
        throw new IllegalArgumentException("本地暂未找到此分类内容");
    }
    private JSONArray recommendations(JSONObject current)throws Exception{
        java.util.List<JSONObject> candidates=new java.util.ArrayList<>();JSONArray rows=db.list(space,"products");java.util.Set<String> seen=new java.util.HashSet<>();
        for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);String id=row.optString("id");if(id.isEmpty()||id.equals(current.optString("id"))||!"ONLINE".equals(row.optString("status","ONLINE"))||row.optInt("stock")<1||!seen.add(id))continue;candidates.add(row);}
        candidates.sort((a,b)->{int category=Boolean.compare(b.optString("categoryId").equals(current.optString("categoryId")),a.optString("categoryId").equals(current.optString("categoryId")));if(category!=0)return category;int age=Boolean.compare(b.optString("ageRange").equals(current.optString("ageRange")),a.optString("ageRange").equals(current.optString("ageRange")));return age!=0?age:a.optString("id").compareTo(b.optString("id"));});
        JSONArray result=new JSONArray();for(int i=0;i<Math.min(4,candidates.size());i++)result.put(candidates.get(i));return result;
    }
    private String parameter(Uri uri,String key){String value=uri.getQueryParameter(key);return value==null?"":value;}
    private JSONObject paginate(Uri uri,JSONArray rows)throws Exception{int page=parameter(uri,"page").isEmpty()?1:Integer.parseInt(parameter(uri,"page")),size=parameter(uri,"pageSize").isEmpty()?100:Integer.parseInt(parameter(uri,"pageSize"));if(page<1||page>100000||size<1||size>100)throw new IllegalArgumentException("分页参数不正确");JSONArray result=new JSONArray();int start=(page-1)*size;for(int i=start;i<Math.min(rows.length(),start+size);i++)result.put(rows.get(i));return data(result).put("total",rows.length()).put("page",page).put("hasMore",start+size<rows.length());}
    private boolean matchesAge(String value,String selection){if(selection.isEmpty())return true;String[] bounds=selection.split("-");int low=Integer.parseInt(bounds[0]),high=bounds[1].equals("plus")?Integer.MAX_VALUE:Integer.parseInt(bounds[1]);String normalized=value.replaceAll("[–—～~至]","-");java.util.regex.Matcher range=java.util.regex.Pattern.compile("(\\d+)\\s*-\\s*(\\d+)").matcher(normalized);if(range.find())return Integer.parseInt(range.group(1))<=high&&Integer.parseInt(range.group(2))>=low;java.util.regex.Matcher single=java.util.regex.Pattern.compile("(\\d+)\\s*岁").matcher(normalized);if(!single.find())return false;int age=Integer.parseInt(single.group(1));return normalized.contains("以上")||normalized.contains("+")?age<=high:age>=low&&age<=high;}
}
