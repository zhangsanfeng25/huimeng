import { Router } from 'express';
import { z } from 'zod';
import { db, now } from '../core/db.js';
import { asyncRoute, requireAuth } from '../core/http.js';
import { broadcast } from '../core/events.js';
import { randomUUID } from 'node:crypto';
import { writeFileSync } from 'node:fs';
import { join } from 'node:path';
import express from 'express';
import { config } from '../config.js';

export const communityRouter=Router();
const makeId=(prefix:string)=>`${prefix}${Date.now().toString(36).toUpperCase()}${Math.random().toString(36).slice(2,6).toUpperCase()}`;

communityRouter.get('/community/posts',requireAuth(),(req,res)=>{
  const data=db.prepare(`SELECT p.id,p.title,p.content,p.cover_url coverUrl,p.images_json images,p.product_id productId,p.author_user_id authorId,p.author_role authorRole,p.created_at createdAt,u.nickname authorName,u.avatar_url avatarUrl,
    (SELECT COUNT(*) FROM comments c WHERE c.post_id=p.id AND c.status='PUBLIC') commentCount,
    (SELECT COUNT(*) FROM post_likes l WHERE l.post_id=p.id) likeCount,
    EXISTS(SELECT 1 FROM post_likes l WHERE l.post_id=p.id AND l.user_id=?) liked
    FROM community_posts p JOIN users u ON u.id=p.author_user_id WHERE p.status='PUBLIC' ORDER BY p.created_at DESC`).all(req.actor!.id);
  res.json({data});
});
communityRouter.get('/community/posts/:id',requireAuth(),(req,res)=>{
  const post=db.prepare(`SELECT p.*,p.author_user_id authorId,u.nickname authorName,u.avatar_url avatarUrl,
    (SELECT COUNT(*) FROM post_likes l WHERE l.post_id=p.id) likeCount,
    EXISTS(SELECT 1 FROM post_likes l WHERE l.post_id=p.id AND l.user_id=?) liked
    FROM community_posts p JOIN users u ON u.id=p.author_user_id WHERE p.id=? AND p.status='PUBLIC'`).get(req.actor!.id,String(req.params.id));
  if(!post)return res.status(404).json({message:'帖子不存在'});
  const comments=db.prepare(`SELECT c.id,c.content,c.created_at createdAt,u.nickname authorName,u.avatar_url avatarUrl FROM comments c JOIN users u ON u.id=c.user_id WHERE c.post_id=? AND c.status='PUBLIC' ORDER BY c.created_at`).all(String(req.params.id));
  res.json({data:{...(post as any),comments}});
});
communityRouter.get('/community/users/:id',requireAuth(),(req,res)=>{
  const user=db.prepare("SELECT id,nickname,COALESCE(avatar_url,'') avatarUrl FROM users WHERE id=? AND status='ACTIVE'").get(String(req.params.id));
  if(!user)return res.status(404).json({message:'用户不存在或已停用'});
  const posts=db.prepare(`SELECT p.id,p.title,p.content,p.cover_url coverUrl,p.author_user_id authorId,p.author_role authorRole,p.created_at createdAt,u.nickname authorName,u.avatar_url avatarUrl,
    (SELECT COUNT(*) FROM comments c WHERE c.post_id=p.id AND c.status='PUBLIC') commentCount,
    (SELECT COUNT(*) FROM post_likes l WHERE l.post_id=p.id) likeCount
    FROM community_posts p JOIN users u ON u.id=p.author_user_id WHERE p.author_user_id=? AND p.status='PUBLIC' ORDER BY p.created_at DESC,p.id DESC`).all(String(req.params.id)) as Array<{likeCount:number}>;
  res.json({data:{user,posts,postCount:posts.length,likeCount:posts.reduce((total,post)=>total+post.likeCount,0)}});
});
communityRouter.post('/community/uploads',requireAuth('USER','MERCHANT'),express.raw({type:'image/jpeg',limit:'3mb'}),(req,res)=>{
  const bytes=req.body as Buffer;
  if(!Buffer.isBuffer(bytes)||bytes.length<100||bytes.length>3*1024*1024||bytes[0]!==0xff||bytes[1]!==0xd8||bytes[bytes.length-2]!==0xff||bytes[bytes.length-1]!==0xd9)
    return res.status(400).json({message:'请选择小于 3 MB 的 JPEG 图片'});
  const path=`/public/community-${randomUUID()}.jpg`;
  writeFileSync(join(config.uploadDir,path.slice('/public/'.length)),bytes,{flag:'wx'});
  db.prepare('INSERT INTO community_uploads(path,user_id,created_at) VALUES(?,?,?)').run(path,req.actor!.id,now());
  res.status(201).json({url:path});
});
communityRouter.put('/community/posts/:id/like',requireAuth('USER','MERCHANT'),(req,res)=>{
  const id=String(req.params.id);
  if(!db.prepare("SELECT 1 FROM community_posts WHERE id=? AND status='PUBLIC'").get(id))return res.status(404).json({message:'帖子不存在'});
  db.prepare('INSERT OR IGNORE INTO post_likes(post_id,user_id,created_at) VALUES(?,?,?)').run(id,req.actor!.id,now());
  const count=(db.prepare('SELECT COUNT(*) count FROM post_likes WHERE post_id=?').get(id) as {count:number}).count;
  broadcast('community','like');res.json({liked:true,likeCount:count});
});
communityRouter.delete('/community/posts/:id/like',requireAuth('USER','MERCHANT'),(req,res)=>{
  const id=String(req.params.id);
  db.prepare('DELETE FROM post_likes WHERE post_id=? AND user_id=?').run(id,req.actor!.id);
  const count=(db.prepare('SELECT COUNT(*) count FROM post_likes WHERE post_id=?').get(id) as {count:number}).count;
  broadcast('community','like');res.json({liked:false,likeCount:count});
});
communityRouter.post('/community/posts',requireAuth('USER','MERCHANT'),asyncRoute(async(req,res)=>{
  const value=z.object({title:z.string().min(1).max(80),content:z.string().min(1).max(2000),images:z.array(z.string().regex(/^\/public\/community-[a-f0-9-]+\.jpg$/)).max(9).optional(),productId:z.string().optional()}).parse(req.body),postId=makeId('T'),stamp=now(),role=req.actor!.roles.includes('MERCHANT')?'MERCHANT':'USER';
  const images=value.images||[];
  try{db.transaction(()=>{
    for(const path of images){const upload=db.prepare('SELECT 1 FROM community_uploads WHERE path=? AND user_id=? AND post_id IS NULL').get(path,req.actor!.id);if(!upload)throw new Error('INVALID_UPLOAD');}
    db.prepare(`INSERT INTO community_posts(id,author_user_id,author_role,title,content,cover_url,images_json,product_id,status,report_count,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?, 'PUBLIC',0,?,?)`).run(postId,req.actor!.id,role,value.title,value.content,images[0]||null,JSON.stringify(images),value.productId||null,stamp,stamp);
    for(const path of images)db.prepare('UPDATE community_uploads SET post_id=? WHERE path=?').run(postId,path);
  })();}catch(error){if((error as Error).message==='INVALID_UPLOAD')return res.status(400).json({message:'图片上传已失效，请重新选择'});throw error;}
  broadcast('community','create');res.status(201).json({id:postId});
}));
communityRouter.post('/community/posts/:id/comments',requireAuth('USER','MERCHANT'),asyncRoute(async(req,res)=>{
  const value=z.object({content:z.string().min(1).max(500)}).parse(req.body),commentId=makeId('CM');
  const post=db.prepare(`SELECT id FROM community_posts WHERE id=? AND status='PUBLIC'`).get(String(req.params.id));if(!post)return res.status(404).json({message:'帖子不存在'});
  db.prepare(`INSERT INTO comments(id,post_id,user_id,content,status,created_at) VALUES(?,?,?,?,'PUBLIC',?)`).run(commentId,String(req.params.id),req.actor!.id,value.content,now());broadcast('community','comment');res.status(201).json({id:commentId});
}));

function visibleNotices(userId:string,roles:string[]){
  const buyer=roles.includes('USER')?1:0,merchant=roles.includes('MERCHANT')?1:0;
  return db.prepare(`SELECT n.id,n.title,n.content,n.audience,n.created_at,
    CASE WHEN s.read_at IS NOT NULL OR (n.user_id=? AND n.is_read=1) THEN 1 ELSE 0 END is_read
    FROM notifications n LEFT JOIN notification_user_state s ON s.notification_id=n.id AND s.user_id=?
    WHERE n.status='SENT' AND s.deleted_at IS NULL AND (n.user_id=? OR (n.user_id IS NULL AND
      n.created_at >= (SELECT created_at FROM users WHERE id=?) AND
      (n.audience NOT IN ('全部商家','全部用户') OR (n.audience='全部商家' AND ?=1) OR (n.audience='全部用户' AND ?=1))))
    ORDER BY n.created_at DESC`).all(userId,userId,userId,userId,merchant,buyer);
}
const noticeVisibility=db.prepare(`SELECT n.id FROM notifications n LEFT JOIN notification_user_state s ON s.notification_id=n.id AND s.user_id=?
  WHERE n.id=? AND n.status='SENT' AND s.deleted_at IS NULL AND (n.user_id=? OR (n.user_id IS NULL AND
  n.created_at >= (SELECT created_at FROM users WHERE id=?) AND
  (n.audience NOT IN ('全部商家','全部用户') OR (n.audience='全部商家' AND ?=1) OR (n.audience='全部用户' AND ?=1))))`);
function accessibleNotice(id:string,userId:string,roles:string[]){
  return !!noticeVisibility.get(userId,id,userId,userId,roles.includes('MERCHANT')?1:0,roles.includes('USER')?1:0);
}
const readNotice=db.prepare(`INSERT INTO notification_user_state(notification_id,user_id,read_at) VALUES(?,?,?)
  ON CONFLICT(notification_id,user_id) DO UPDATE SET read_at=COALESCE(notification_user_state.read_at,excluded.read_at)`);
const deleteNotice=db.prepare(`INSERT INTO notification_user_state(notification_id,user_id,deleted_at) VALUES(?,?,?)
  ON CONFLICT(notification_id,user_id) DO UPDATE SET deleted_at=excluded.deleted_at`);
communityRouter.get('/me/notifications',requireAuth(),(req,res)=>res.json({data:visibleNotices(req.actor!.id,req.actor!.roles)}));
communityRouter.post('/me/notifications/bulk/:action',requireAuth(),(req,res)=>{
  const action=String(req.params.action);
  if(action!=='read'&&action!=='delete')return res.status(404).json({message:'操作不存在'});
  const parsed=z.object({ids:z.array(z.string().min(1)).min(1).max(1000)}).safeParse(req.body);
  if(!parsed.success)return res.status(400).json({message:'请选择 1 至 1000 条消息'});
  const ids=[...new Set(parsed.data.ids)];
  if(ids.some(id=>!accessibleNotice(id,req.actor!.id,req.actor!.roles)))return res.status(404).json({message:'部分消息不存在或已删除，请刷新列表'});
  db.transaction(()=>{for(const id of ids)(action==='read'?readNotice:deleteNotice).run(id,req.actor!.id,now());})();
  res.json({ok:true,count:ids.length});
});
communityRouter.post('/me/notifications/:id/read',requireAuth(),(req,res)=>{
  const id=String(req.params.id);
  if(!accessibleNotice(id,req.actor!.id,req.actor!.roles))return res.status(404).json({message:'消息不存在'});
  readNotice.run(id,req.actor!.id,now());res.json({ok:true});
});
communityRouter.delete('/me/notifications/:id',requireAuth(),(req,res)=>{
  const id=String(req.params.id);
  if(!accessibleNotice(id,req.actor!.id,req.actor!.roles))return res.status(404).json({message:'消息不存在'});
  deleteNotice.run(id,req.actor!.id,now());res.json({ok:true});
});

communityRouter.post('/me/ratings',requireAuth('USER'),asyncRoute(async(req,res)=>{
  const value=z.object({orderItemId:z.string(),stars:z.number().int().min(1).max(5),content:z.string().min(1).max(1000)}).parse(req.body);
  const item=db.prepare(`SELECT oi.product_id,o.user_id,o.status FROM order_items oi JOIN orders o ON o.id=oi.order_id WHERE oi.id=?`).get(value.orderItemId) as any;
  if(!item||item.user_id!==req.actor!.id||item.status!=='COMPLETED')return res.status(403).json({message:'只能评价本人已完成订单'});
  const ratingId=makeId('R');
  try{db.prepare(`INSERT INTO ratings(id,order_item_id,user_id,product_id,stars,content,status,created_at) VALUES(?,?,?,?,?,?,'PUBLIC',?)`).run(ratingId,value.orderItemId,req.actor!.id,item.product_id,value.stars,value.content,now());}catch{return res.status(409).json({message:'该商品已评价'});}
  broadcast('ratings','create');res.status(201).json({id:ratingId});
}));
