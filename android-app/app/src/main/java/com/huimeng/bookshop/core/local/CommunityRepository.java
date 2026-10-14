package com.huimeng.bookshop.core.local;
import org.json.*;
public final class CommunityRepository extends BusinessRepository {
    public CommunityRepository(LocalDatabase db,String space){super(db,space);}
    public JSONObject handle(int method,String path,JSONObject body)throws Exception{
        if(!path.startsWith("community/"))return null;if(body==null)body=new JSONObject();String[] p=path.split("/");
        if(path.equals("community/posts")){
            if(method==0)return data(db.list(space,"posts"));JSONObject user=profile(),post=new JSONObject(body.toString());text(body,"title",1,80);text(body,"content",1,2000);
            JSONArray images=body.optJSONArray("images");if(images==null)images=new JSONArray();post.put("images_json",images).put("cover_url",images.optString(0)).put("coverUrl",images.optString(0)).put("createdAt",java.time.Instant.now().toString()).put("authorId",user.optString("id",space)).put("authorName",user.optString("name","本地用户")).put("authorRole",new LocalSpaceStore(db).info(space).optString("role","USER")).put("likeCount",0).put("commentCount",0);
            return save("posts",id(),"post.create",post,body);
        }
        if(p.length>=3&&p[1].equals("users")){JSONObject user=profile();JSONArray posts=filter(db.list(space,"posts"),"authorId",p[2]);int likes=0;for(int i=0;i<posts.length();i++)likes+=posts.getJSONObject(i).optInt("likeCount");return data(new JSONObject().put("user",new JSONObject().put("id",p[2]).put("nickname",p[2].equals(user.optString("id"))?user.optString("name"):"已缓存用户").put("avatarUrl",user.optString("avatarUrl"))).put("posts",posts).put("postCount",posts.length()).put("likeCount",likes));}
        if(p.length<3)return null;String id=p[2];JSONObject post=require("posts",id);
        if(method==0){JSONArray comments=filter(db.list(space,"comments"),"postId",id);String remote=post.optString("remoteId");if(!remote.isEmpty()&&!remote.equals(id)){JSONArray extra=filter(db.list(space,"comments"),"postId",remote);for(int i=0;i<extra.length();i++)comments.put(extra.get(i));}post.put("comments",comments);return data(post);}
        if(p.length>3&&p[3].equals("comments")){text(body,"content",1,500);JSONObject comment=new JSONObject(body.toString()).put("postId",id).put("authorName",profile().optString("name")).put("createdAt",java.time.Instant.now().toString());return save("comments",id(),"comment.create",comment,new JSONObject(body.toString()).put("postId",id));}
        if(p.length>3&&p[3].equals("like")){boolean liked=method!=3;int count=Math.max(0,post.optInt("likeCount")+(liked==post.optBoolean("liked")?0:liked?1:-1));post.put("liked",liked).put("likeCount",count);save("posts",id,"post.like",post,new JSONObject().put("postId",id).put("enabled",liked));return new JSONObject().put("liked",liked).put("likeCount",count).put("pending",true);}
        return null;
    }
}
