package com.huimeng.bookshop.model;

import org.json.JSONObject;

public record CommunityPost(String id,String title,String content,String coverUrl,String authorName,String authorRole,int commentCount,int likeCount,String createdAt,String authorId,String avatarUrl){
    public static CommunityPost from(JSONObject item){return new CommunityPost(item.optString("id"),item.optString("title"),item.optString("content"),item.optString("coverUrl",item.optString("cover_url")),item.optString("authorName"),item.optString("authorRole",item.optString("author_role")),item.optInt("commentCount"),item.optInt("likeCount"),item.optString("createdAt",item.optString("created_at")),item.optString("authorId",item.optString("author_user_id")),item.optString("avatarUrl"));}
}
