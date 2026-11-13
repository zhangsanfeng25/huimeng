import {readTables} from '../core/db.js';
import {visibleNotices} from '../services/notice-data.js';
import {businessRoute} from '../services/context.js';
import {likeEnable,likeDisable,postCreate,commentCreate,ratingCreate} from '../services/community.js';
import {noticeBulk,noticeRead,noticeDelete} from '../services/notices.js';
import { Router } from 'express';

import { db, now } from '../core/db.js';
import {requireAuth} from '../core/http.js';

import { randomUUID } from 'node:crypto';
import { writeFileSync } from 'node:fs';
import { join } from 'node:path';
import express from 'express';
import { config } from '../config.js';
import type { Statement } from 'better-sqlite3';

let publicPosts:Statement,publicPost:Statement,publicComments:Statement,publicUser:Statement,authorPosts:Statement;

export const communityRouter=Router();


communityRouter.get('/community/posts',requireAuth(),(req,res)=>{
  const data=(publicPosts??=db.prepare(`SELECT p.id,p.title,p.content,p.cover_url coverUrl,p.images_json images,p.product_id productId,p.author_user_id authorId,p.author_role authorRole,p.created_at createdAt,u.nickname authorName,u.avatar_url avatarUrl,
    (SELECT COUNT(*) FROM comments c WHERE c.post_id=p.id AND c.status='PUBLIC') commentCount,
    (SELECT COUNT(*) FROM post_likes l WHERE l.post_id=p.id) likeCount,
    EXISTS(SELECT 1 FROM post_likes l WHERE l.post_id=p.id AND l.user_id=?) liked
    FROM ${readTables.community_posts} p JOIN users u ON u.id=p.author_user_id WHERE p.status='PUBLIC' ORDER BY p.created_at DESC`)).all(req.actor!.id);
  res.json({data});
});
communityRouter.get('/community/posts/:id',requireAuth(),(req,res)=>{
  const post=(publicPost??=db.prepare(`SELECT p.*,p.author_user_id authorId,u.nickname authorName,u.avatar_url avatarUrl,
    (SELECT COUNT(*) FROM post_likes l WHERE l.post_id=p.id) likeCount,
    EXISTS(SELECT 1 FROM post_likes l WHERE l.post_id=p.id AND l.user_id=?) liked
    FROM ${readTables.community_posts} p JOIN users u ON u.id=p.author_user_id WHERE p.id=? AND p.status='PUBLIC'`)).get(req.actor!.id,String(req.params.id));
  if(!post)return res.status(404).json({message:'帖子不存在'});
  const comments=(publicComments??=db.prepare(`SELECT c.id,c.content,c.created_at createdAt,u.nickname authorName,u.avatar_url avatarUrl FROM comments c JOIN users u ON u.id=c.user_id WHERE c.post_id=? AND c.status='PUBLIC' ORDER BY c.created_at`)).all(String(req.params.id));
  res.json({data:{...(post as any),comments}});
});
communityRouter.get('/community/users/:id',requireAuth(),(req,res)=>{
  const user=(publicUser??=db.prepare("SELECT id,nickname,COALESCE(avatar_url,'') avatarUrl FROM users WHERE id=? AND status='ACTIVE'")).get(String(req.params.id));
  if(!user)return res.status(404).json({message:'用户不存在或已停用'});
  const posts=(authorPosts??=db.prepare(`SELECT p.id,p.title,p.content,p.cover_url coverUrl,p.author_user_id authorId,p.author_role authorRole,p.created_at createdAt,u.nickname authorName,u.avatar_url avatarUrl,
    (SELECT COUNT(*) FROM comments c WHERE c.post_id=p.id AND c.status='PUBLIC') commentCount,
    (SELECT COUNT(*) FROM post_likes l WHERE l.post_id=p.id) likeCount,
    EXISTS(SELECT 1 FROM post_likes l WHERE l.post_id=p.id AND l.user_id=?) liked
    FROM ${readTables.community_posts} p JOIN users u ON u.id=p.author_user_id WHERE p.author_user_id=? AND p.status='PUBLIC' ORDER BY p.created_at DESC,p.id DESC`)).all(req.actor!.id,String(req.params.id)) as Array<{likeCount:number}>;
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
communityRouter.put('/community/posts/:id/like',requireAuth('USER','MERCHANT'),businessRoute(likeEnable));
communityRouter.delete('/community/posts/:id/like',requireAuth('USER','MERCHANT'),businessRoute(likeDisable));
communityRouter.post('/community/posts',requireAuth('USER','MERCHANT'),businessRoute(postCreate));
communityRouter.post('/community/posts/:id/comments',requireAuth('USER','MERCHANT'),businessRoute(commentCreate));

communityRouter.get('/me/notifications',requireAuth(),(req,res)=>res.json({data:visibleNotices(req.actor!.id,req.actor!.roles)}));
communityRouter.post('/me/notifications/bulk/:action',requireAuth(),businessRoute(noticeBulk));
communityRouter.post('/me/notifications/:id/read',requireAuth(),businessRoute(noticeRead));
communityRouter.delete('/me/notifications/:id',requireAuth(),businessRoute(noticeDelete));

communityRouter.post('/me/ratings',requireAuth('USER'),businessRoute(ratingCreate));
