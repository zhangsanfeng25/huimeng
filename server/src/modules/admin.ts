import {readTables} from '../core/db.js';
import {withFulfillment} from '../services/after-sale-items.js';
import {bookInput,bookColumns,readBookDetails,saveBookDetails} from '../services/book-details.js';
import { Router } from 'express';
import { randomBytes } from 'node:crypto';
import { randomUUID } from 'node:crypto';
import { writeFileSync } from 'node:fs';
import { join } from 'node:path';
import express from 'express';
import { z } from 'zod';
import { config } from '../config.js';
import { db, now, normalizedSchema } from '../core/db.js';
import { asyncRoute, audit, requireAuth } from '../core/http.js';
import { broadcast, subscribe } from '../core/events.js';
import { hashPassword } from '../core/security.js';
import { categoryOptions } from '../core/category-options.js';
import { closePendingCheckout } from '../core/order-lifecycle.js';
import { afterSaleColumns, hasBlockingAfterSale } from '../services/customer-services.js';
import { createOrderNotifications } from '../core/order-notifications.js';
import { leastUsedDefaultAvatar } from '../core/default-avatars.js';
import {replaceOrderedValues} from '../normalization/relations.js';
import { merchantApplication } from './merchant-applications.js';

export const adminRouter=Router();
adminRouter.use(requireAuth('ADMIN','OPERATOR','SUPPORT'));
adminRouter.use((req,res,next)=>{
  if(req.method!=='GET' && !req.actor!.roles.some(role=>role==='ADMIN'||role==='OPERATOR'))
    return res.status(403).json({message:'当前账号只能查看管理数据'});
  next();
});

const zh={
  ACTIVE:'正常',DISABLED:'已停用',PENDING:'待审核',APPROVED:'已通过',REJECTED:'已驳回',
  OPEN:'营业中',SUSPENDED:'已停用',ENABLED:'已启用',DRAFT:'草稿',ONLINE:'销售中',OFFLINE:'已下架',
  PLATFORM_OFFLINE:'平台下架',PENDING_REVIEW:'待审核',PUBLIC:'公开',HIDDEN:'已隐藏',
  PENDING_PAYMENT:'待支付',PAID:'待发货',SHIPPED:'待收货',WAITING_PAYMENT:'待支付',PREPARING:'准备出库',
  READY_TO_SHIP:'待发货',IN_TRANSIT:'运输中',OUT_FOR_DELIVERY:'派送中',COMPLETED:'已完成',CANCELLED:'已取消',SENT:'已发送'
} as Record<string,string>;
const toZh=(value:string)=>zh[value]||value;

function rows(resource:string){
  switch(resource){
    case 'users': return (db.prepare(`SELECT u.id,u.nickname,u.avatar_url avatarUrl,COALESCE(u.email,u.phone,u.username) account,substr(u.created_at,1,10) registeredAt,u.status,COUNT(o.id) orders FROM users u LEFT JOIN ${readTables.orders} o ON o.user_id=u.id WHERE EXISTS(SELECT 1 FROM user_roles r WHERE r.user_id=u.id AND r.role='USER') GROUP BY u.id ORDER BY u.created_at DESC`).all() as any[]).map(x=>({...x,status:toZh(x.status)}));
    case 'merchants': return (db.prepare(`SELECT m.id,m.shop_name shop,u.username account,substr(m.applied_at,1,10) appliedAt,m.approval_status approval,m.business_status status,m.updated_at updatedAt,COALESCE(m.rejection_reason,'' ) note,COUNT(p.id) products FROM merchants m JOIN users u ON u.id=m.owner_user_id LEFT JOIN products p ON p.merchant_id=m.id GROUP BY m.id ORDER BY m.applied_at DESC`).all() as any[]).map(x=>({...x,approval:toZh(x.approval),status:toZh(x.status)}));
    case 'categories': return (db.prepare(`SELECT c.id,c.name,c.parent_name parent,c.sort_order sort,c.status,COUNT(p.id) products FROM categories c LEFT JOIN products p ON p.category_id=c.id GROUP BY c.id ORDER BY c.sort_order`).all() as any[]).map(x=>({...x,status:toZh(x.status)}));
    case 'reviews': return (db.prepare(`SELECT r.id,r.name product,m.shop_name merchant,c.name category,COALESCE(r.submitted_at,r.created_at) submittedAt,r.review_status status,COALESCE(r.rejection_reason,'') note FROM ${readTables.product_revisions} r JOIN products p ON p.id=r.product_id JOIN merchants m ON m.id=p.merchant_id JOIN categories c ON c.id=p.category_id WHERE r.review_status IN ('PENDING','APPROVED','REJECTED') ORDER BY r.created_at DESC`).all() as any[]).map(x=>({...x,status:toZh(x.status)}));
    case 'products': return (db.prepare(`SELECT p.id,r.name,m.shop_name shop,c.name category,r.cover_url coverUrl,r.price_cents/100.0 price,p.stock_available stock,r.review_status approval,p.status FROM products p JOIN ${readTables.product_revisions} r ON r.id=COALESCE(p.published_revision_id,(SELECT id FROM ${readTables.product_revisions} WHERE product_id=p.id ORDER BY version DESC LIMIT 1)) JOIN merchants m ON m.id=p.merchant_id JOIN categories c ON c.id=p.category_id ORDER BY p.updated_at DESC`).all() as any[]).map(x=>({...x,approval:toZh(x.approval),status:toZh(x.status)}));
    case 'orders': return (db.prepare(`SELECT o.id,${afterSaleColumns},m.shop_name shop,u.nickname buyer,o.total_cents/100.0 amount,o.logistics_status status,o.created_at orderedAt,COALESCE(o.paid_at,'') paidAt FROM ${readTables.orders} o JOIN merchants m ON m.id=o.merchant_id JOIN users u ON u.id=o.user_id ORDER BY o.created_at DESC`).all() as any[]).map(x=>({...x,status:toZh(x.status)}));
    case 'ratings': return (db.prepare(`SELECT r.id,pr.name product,u.nickname user,r.stars,r.content,substr(r.created_at,1,16) createdAt,r.status FROM ${readTables.ratings} r JOIN users u ON u.id=r.user_id JOIN products p ON p.id=r.product_id JOIN ${readTables.product_revisions} pr ON pr.id=p.published_revision_id WHERE r.deleted_at IS NULL ORDER BY r.created_at DESC`).all() as any[]).map(x=>({...x,status:toZh(x.status)}));
    case 'community': return (db.prepare(`SELECT cp.id,cp.author_user_id authorId,u.username authorAccount,u.nickname authorName,CASE cp.author_role WHEN 'MERCHANT' THEN '商家' ELSE '用户' END role,cp.title,cp.content,cp.cover_url coverUrl,cp.images_json images,COALESCE(pr.name,'') product,substr(cp.created_at,1,16) publishedAt,cp.status,cp.report_count reports,
      (SELECT COUNT(*) FROM post_likes l WHERE l.post_id=cp.id) likeCount,
      (SELECT COUNT(*) FROM comments c WHERE c.post_id=cp.id AND c.status='PUBLIC') commentCount
      FROM ${readTables.community_posts} cp JOIN users u ON u.id=cp.author_user_id LEFT JOIN products p ON p.id=cp.product_id LEFT JOIN ${readTables.product_revisions} pr ON pr.id=p.published_revision_id ORDER BY cp.created_at DESC`).all() as any[]).map(x=>({...x,status:toZh(x.status)}));
    case 'notices': return (db.prepare(`SELECT n.id,n.title,n.content,n.user_id userId,n.audience,substr(n.created_at,1,16) sentAt,n.created_at createdAt,${normalizedSchema?'EXISTS(SELECT 1 FROM notification_read_receipts r WHERE r.notification_id=n.id AND r.user_id=n.user_id)':'n.is_read'} isRead,n.status FROM notifications n ORDER BY n.created_at DESC`).all() as any[]).map(n=>{
      const eligible=`SELECT u.id FROM users u WHERE u.created_at<=? AND (?='全部用户' AND EXISTS(SELECT 1 FROM user_roles r WHERE r.user_id=u.id AND r.role='USER') OR ?='全部商家' AND EXISTS(SELECT 1 FROM user_roles r WHERE r.user_id=u.id AND r.role='MERCHANT') OR ?='全部账号')`;
      const delivered=n.status==='SENT'?(n.userId?1:(db.prepare(`SELECT COUNT(*) count FROM (${eligible})`).get(n.createdAt,n.audience,n.audience,n.audience) as any).count):0;
      const readTable=normalizedSchema?'notification_read_receipts':'notification_user_state',readPredicate=normalizedSchema?'1=1':'read_at IS NOT NULL';
      const read=n.userId?(n.isRead||db.prepare(`SELECT 1 FROM ${readTable} WHERE notification_id=? AND user_id=? AND ${readPredicate}`).get(n.id,n.userId)?1:0):(db.prepare(`SELECT COUNT(*) count FROM ${readTable} s WHERE s.notification_id=? AND ${readPredicate} AND s.user_id IN (${eligible})`).get(n.id,n.createdAt,n.audience,n.audience,n.audience) as any).count;
      return {...n,delivered,readRate:delivered?`${Math.round(read/delivered*100)}%`:'0%',status:toZh(n.status)};
    });
    default:return [];
  }
}

adminRouter.get('/events',(_req,res)=>{res.set({'Content-Type':'text/event-stream','Cache-Control':'no-cache','Connection':'keep-alive'});res.flushHeaders();res.write('event: ready\ndata: {}\n\n');subscribe(res);});
adminRouter.get('/dashboard',(req,res)=>{
  const days=Number(req.query.days)===30?30:7;
  const orderRows=rows('orders') as any[],productRows=rows('products') as any[],reviewRows=rows('reviews') as any[],userRows=rows('users') as any[];
  const metrics={orders:orderRows.length,turnover:orderRows.filter(x=>x.paidAt&&x.status!=='已取消').reduce((n,x)=>n+Number(x.amount||0)-Number(x.refunded_cents||0)/100,0),products:productRows.filter(x=>x.status==='销售中').length,users:userRows.length,pendingReviews:reviewRows.filter(x=>x.status==='待审核').length,pendingShipment:orderRows.filter(x=>(x.status==='准备出库'||x.status==='待发货')&&(!x.after_sale_state||['REJECTED','CANCELLED','COMPLETED'].includes(x.after_sale_state))&&Number(x.refunded_cents||0)<Number(x.amount||0)*100).length};
  const grouped=new Map<string,{orders:number;turnover:number}>();
  for(const order of orderRows){const key=String(order.orderedAt||'').slice(0,10);if(!key)continue;const point=grouped.get(key)||{orders:0,turnover:0};point.orders+=1;if(order.paidAt&&order.status!=='已取消')point.turnover+=Number(order.amount||0)-Number(order.refunded_cents||0)/100;grouped.set(key,point)}
  const registrations=db.prepare(`SELECT substr(created_at,1,10) day,COUNT(*) count FROM users WHERE EXISTS(SELECT 1 FROM user_roles r WHERE r.user_id=users.id AND r.role='USER') GROUP BY day`).all() as {day:string;count:number}[];
  const additions=db.prepare('SELECT substr(created_at,1,10) day,COUNT(*) count FROM products GROUP BY day').all() as {day:string;count:number}[];
  const userCounts=new Map(registrations.map(row=>[row.day,row.count]));
  const productCounts=new Map(additions.map(row=>[row.day,row.count]));
  const trend=Array.from({length:days},(_,index)=>{const date=new Date();date.setHours(0,0,0,0);date.setDate(date.getDate()-(days-1-index));const key=`${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}`;return{label:key.slice(5),products:productCounts.get(key)||0,users:userCounts.get(key)||0,...(grouped.get(key)||{orders:0,turnover:0})}});
  const distribution=(db.prepare(`SELECT c.name,COUNT(p.id) value FROM categories c LEFT JOIN products p ON p.category_id=c.id GROUP BY c.id ORDER BY c.sort_order`).all());
  const operations=db.prepare('SELECT request_id id,operator_name operator,module,action,object_id objectId,created_at createdAt FROM audit_logs ORDER BY created_at DESC,id DESC LIMIT 20').all();
  res.json({metrics,trend,distribution,operations,tasks:reviewRows.filter(x=>x.status==='待审核').slice(0,5),recentOrders:orderRows.slice(0,20),updatedAt:now()});
});
adminRouter.get('/settings',(_req,res)=>res.json({data:Object.fromEntries((db.prepare('SELECT key,value FROM settings').all() as any[]).map(x=>[x.key,x.value]))}));
adminRouter.put('/settings',(req,res)=>{
  const statement=db.prepare(`INSERT INTO settings(key,value,updated_at) VALUES(?,?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value,updated_at=excluded.updated_at`);
  db.transaction(()=>{for(const [key,value] of Object.entries(req.body||{}))statement.run(key,String(value),now())})();
  audit(req.actor!.id,req.actor!.name,'系统设置','保存配置','settings');broadcast('settings','update');res.json({data:req.body});
});
adminRouter.get('/logs',(_req,res)=>{
  const data=db.prepare(`SELECT request_id id,operator_name operator,module,action,object_id objectId,created_at createdAt,result FROM audit_logs ORDER BY id DESC LIMIT 200`).all();res.json({data});
});

const postInput=z.object({title:z.string().trim().min(1).max(80),content:z.string().trim().min(1).max(2000),status:z.enum(['公开','已隐藏']).default('公开'),images:z.array(z.string().regex(/^\/public\/community-[a-f0-9-]+\.jpg$/)).max(9).default([])});
const commentInput=z.object({content:z.string().trim().min(1).max(500),status:z.enum(['公开','已隐藏']).default('公开')});
const idFor=(prefix:string)=>`${prefix}${randomBytes(9).toString('hex').toUpperCase()}`;
function postDetails(id:string){
  const post=db.prepare(`SELECT p.id,p.author_user_id authorId,p.author_role authorRole,p.title,p.content,p.cover_url coverUrl,p.images_json images,p.product_id productId,p.status,p.report_count reports,p.created_at createdAt,u.username authorAccount,u.nickname authorName,u.avatar_url avatarUrl
    FROM ${readTables.community_posts} p JOIN users u ON u.id=p.author_user_id WHERE p.id=?`).get(id) as any;
  if(!post)return null;
  const likes=db.prepare(`SELECT l.user_id userId,u.username account,u.nickname nickname,u.avatar_url avatarUrl,l.created_at createdAt FROM post_likes l JOIN users u ON u.id=l.user_id WHERE l.post_id=? ORDER BY l.created_at DESC`).all(id);
  const comments=db.prepare(`SELECT c.id,c.user_id userId,u.username account,u.nickname nickname,u.avatar_url avatarUrl,c.content,c.status,c.created_at createdAt FROM comments c JOIN users u ON u.id=c.user_id WHERE c.post_id=? ORDER BY c.created_at DESC`).all(id);
  let images:unknown=[];try{images=JSON.parse(post.images||'[]');}catch{}
  return {...post,images:[...new Set([post.coverUrl,...(Array.isArray(images)?images:[])].filter(v=>typeof v==='string'&&v))],likes,comments,likeCount:likes.length,commentCount:(comments as any[]).filter(c=>c.status==='PUBLIC').length};
}
adminRouter.get('/community/:id/details',(req,res)=>{
  const data=postDetails(String(req.params.id));
  if(!data)return res.status(404).json({message:'帖子不存在'});
  res.json({data});
});
adminRouter.post(['/admin/community/uploads','/admin/media/uploads'],express.raw({type:'image/jpeg',limit:'3mb'}),(req,res)=>{
  const bytes=req.body as Buffer;
  if(!Buffer.isBuffer(bytes)||bytes.length<100||bytes.length>3*1024*1024||bytes[0]!==255||bytes[1]!==216||bytes[bytes.length-2]!==255||bytes[bytes.length-1]!==217)
    return res.status(400).json({message:'请选择小于 3 MB 的 JPEG 图片'});
  const path=`/public/community-${randomUUID()}.jpg`;
  writeFileSync(join(config.uploadDir,path.slice('/public/'.length)),bytes,{flag:'wx'});
  db.prepare('INSERT INTO community_uploads(path,user_id,created_at) VALUES(?,?,?)').run(path,req.actor!.id,now());
  audit(req.actor!.id,req.actor!.name,'community','上传图片',path);
  res.status(201).json({url:path});
});
adminRouter.put('/community/:id/details',(req,res)=>{
  const id=String(req.params.id),parsed=postInput.safeParse(req.body);
  if(!parsed.success)return res.status(400).json({message:'请填写标题、正文，并检查图片数量'});
  if(!postDetails(id))return res.status(404).json({message:'帖子不存在'});
  const value=parsed.data,stamp=now();
  if(value.images.some(path=>!db.prepare('SELECT 1 FROM community_uploads WHERE path=? AND (post_id=? OR (post_id IS NULL AND user_id=?))').get(path,id,req.actor!.id)))
    return res.status(400).json({message:'图片不存在或不属于此帖子'});
  db.transaction(()=>{
    db.prepare(`UPDATE community_posts SET title=?,content=?,status=?,cover_url=?,${normalizedSchema?'':'images_json=?,'}updated_at=? WHERE id=?`).run(value.title,value.content,value.status==='公开'?'PUBLIC':'HIDDEN',value.images[0]||null,...(normalizedSchema?[]:[JSON.stringify(value.images)]),stamp,id);
    if(normalizedSchema)replaceOrderedValues(db,'community_post_images',id,value.images);
    for(const path of value.images)db.prepare('UPDATE community_uploads SET post_id=? WHERE path=? AND post_id IS NULL').run(id,path);
  })();
  audit(req.actor!.id,req.actor!.name,'community','编辑帖子',id);broadcast('community','update');res.json({data:postDetails(id)});
});
adminRouter.post('/community/:id/comments',(req,res)=>{
  const id=String(req.params.id),parsed=commentInput.safeParse(req.body);
  if(!parsed.success)return res.status(400).json({message:'评论内容须为 1 至 500 字'});
  if(!postDetails(id))return res.status(404).json({message:'帖子不存在'});
  const commentId=idFor('CM');
  db.prepare('INSERT INTO comments(id,post_id,user_id,content,status,created_at) VALUES(?,?,?,?,?,?)').run(commentId,id,req.actor!.id,parsed.data.content,parsed.data.status==='公开'?'PUBLIC':'HIDDEN',now());
  audit(req.actor!.id,req.actor!.name,'community','新增评论',commentId);broadcast('community','comment');res.status(201).json({data:postDetails(id)});
});
adminRouter.put('/community/:id/comments/:commentId',(req,res)=>{
  const id=String(req.params.id),commentId=String(req.params.commentId),parsed=commentInput.safeParse(req.body);
  if(!parsed.success)return res.status(400).json({message:'评论内容须为 1 至 500 字'});
  const result=db.prepare(`UPDATE comments SET content=?,status=? WHERE id=? AND post_id=? AND status!='DELETED'`).run(parsed.data.content,parsed.data.status==='公开'?'PUBLIC':'HIDDEN',commentId,id);
  if(!result.changes)return res.status(404).json({message:'评论不存在'});
  audit(req.actor!.id,req.actor!.name,'community','编辑评论',commentId);broadcast('community','comment');res.json({data:postDetails(id)});
});
adminRouter.delete('/community/:id/comments/:commentId',(req,res)=>{
  const id=String(req.params.id),commentId=String(req.params.commentId);
  const result=db.prepare(`UPDATE comments SET status='DELETED' WHERE id=? AND post_id=? AND status!='DELETED'`).run(commentId,id);
  if(!result.changes)return res.status(404).json({message:'评论不存在'});
  audit(req.actor!.id,req.actor!.name,'community','删除评论',commentId);broadcast('community','comment');res.json({data:postDetails(id)});
});
adminRouter.delete('/community/:id/likes/:userId',(req,res)=>{
  const result=db.prepare('DELETE FROM post_likes WHERE post_id=? AND user_id=?').run(String(req.params.id),String(req.params.userId));
  if(!result.changes)return res.status(404).json({message:'点赞记录不存在'});
  audit(req.actor!.id,req.actor!.name,'community','移除点赞',String(req.params.userId));broadcast('community','like');res.json({data:postDetails(String(req.params.id))});
});
function accountContent(id:string,merchantId?:string){
  const orderList=db.prepare(`SELECT o.id,o.user_id userId,o.total_cents/100.0 amount,o.logistics_status status,o.created_at createdAt,m.shop_name shop FROM ${readTables.orders} o JOIN merchants m ON m.id=o.merchant_id WHERE ${merchantId?'o.merchant_id':'o.user_id'}=? ORDER BY o.created_at DESC`).all(merchantId||id);
  const postList=db.prepare(`SELECT id,author_user_id authorId,title,content,status,created_at createdAt FROM ${readTables.community_posts} WHERE author_user_id=? AND status!='DELETED' ORDER BY created_at DESC`).all(id);
  const commentList=db.prepare(`SELECT c.id,c.post_id postId,c.user_id userId,c.content,c.status,c.created_at createdAt,p.title postTitle FROM comments c JOIN ${readTables.community_posts} p ON p.id=c.post_id WHERE ${merchantId?'(c.user_id=? OR p.author_user_id=?)':'c.user_id=?'} AND c.status!='DELETED' ORDER BY c.created_at DESC`).all(...(merchantId?[id,id]:[id]));
  const ratingList=db.prepare(`SELECT r.id,r.product_id productId,r.content,r.stars,r.status,r.created_at createdAt FROM ${readTables.ratings} r ${merchantId?'JOIN products p ON p.id=r.product_id':''} WHERE ${merchantId?'p.merchant_id':'r.user_id'}=? AND r.deleted_at IS NULL ORDER BY r.created_at DESC`).all(merchantId||id);
  return {orderList,postList,commentList,ratingList};
}
adminRouter.get('/merchants/:id/details',(req,res)=>{
  const id=String(req.params.id),merchant=db.prepare('SELECT m.id,m.owner_user_id ownerId,m.shop_name shop,u.avatar_url avatarUrl FROM merchants m JOIN users u ON u.id=m.owner_user_id WHERE m.id=?').get(id) as {id:string;ownerId:string;shop:string}|undefined;
  if(!merchant)return res.status(404).json({message:'商家不存在'});
  const productList=db.prepare(`SELECT p.id,p.merchant_id merchantId,r.name title,r.price_cents/100.0 amount,p.stock_available stock,p.status FROM products p JOIN ${readTables.product_revisions} r ON r.id=COALESCE(p.published_revision_id,(SELECT id FROM ${readTables.product_revisions} WHERE product_id=p.id ORDER BY version DESC LIMIT 1)) WHERE p.merchant_id=? ORDER BY p.updated_at DESC`).all(id);
  res.json({data:{...merchant,...accountContent(merchant.ownerId,id),productList,application:merchantApplication(merchant.ownerId)}});
});
adminRouter.get('/category-options',(_req,res)=>{
  const data=Object.fromEntries(Object.entries(categoryOptions).map(([key,values])=>[key,[...values]]));
  for(const row of db.prepare('SELECT name,parent_name parent FROM categories ORDER BY sort_order').all() as Array<{name:string;parent:string}>){(data[row.parent]??=[]);if(!data[row.parent].includes(row.name))data[row.parent].push(row.name);}
  res.json({data});
});
adminRouter.get('/users/:id/details',(req,res)=>{
  const id=String(req.params.id);
  const user=db.prepare(`SELECT u.id,u.username,u.nickname,u.email,u.phone,u.avatar_url avatarUrl,u.status,u.created_at createdAt,
    (SELECT COUNT(*) FROM ${readTables.orders} WHERE user_id=u.id) orders,
    (SELECT COUNT(*) FROM ${readTables.community_posts} WHERE author_user_id=u.id AND status!='DELETED') posts,
    (SELECT COUNT(*) FROM comments WHERE user_id=u.id AND status='PUBLIC') comments,
    (SELECT COUNT(*) FROM post_likes WHERE user_id=u.id) likes
    FROM users u WHERE u.id=?`).get(id);
  if(!user)return res.status(404).json({message:'账户不存在'});
  const roles=(db.prepare('SELECT role FROM user_roles WHERE user_id=?').all(id) as Array<{role:string}>).map(row=>row.role);
  res.json({data:{...user,roles,...accountContent(id)}});
});
adminRouter.get('/products/:id/details',(req,res)=>{
  const data=db.prepare(`SELECT p.id,p.status,p.stock_available stock,p.stock_reserved reserved,r.id revisionId,r.version,r.created_at revisionCreatedAt,r.submitted_at submittedAt,r.reviewed_at reviewedAt,r.rejection_reason rejectionReason,${bookColumns},r.name,r.summary,r.age_range ageRange,r.price_cents/100.0 price,r.cover_url coverUrl,r.detail_images_json images,r.audio_url audioUrl,r.review_status approval,m.shop_name shop,m.id merchantId,c.name category
    FROM products p JOIN ${readTables.product_revisions} r ON r.id=COALESCE(p.published_revision_id,(SELECT id FROM ${readTables.product_revisions} WHERE product_id=p.id ORDER BY version DESC LIMIT 1))
    JOIN merchants m ON m.id=p.merchant_id JOIN categories c ON c.id=p.category_id WHERE p.id=?`).get(String(req.params.id)) as any;
  if(!data)return res.status(404).json({message:'商品不存在'});
  res.json({data:{...data,...readBookDetails(data),images:JSON.parse(data.images||'[]')}});
});
adminRouter.get('/reviews/:id/details',(req,res)=>{
  const data=db.prepare(`SELECT r.id revisionId,r.product_id id,r.version,${bookColumns},r.name,r.summary,r.age_range ageRange,r.price_cents/100.0 price,r.cover_url coverUrl,r.detail_images_json images,r.audio_url audioUrl,r.review_status approval,r.rejection_reason rejectionReason,r.submitted_at submittedAt,r.reviewed_at reviewedAt,p.status,p.stock_available stock,p.stock_reserved reserved,m.shop_name shop,c.name category,
    (SELECT MAX(version) FROM ${readTables.product_revisions} WHERE product_id=p.id) latestVersion
    FROM ${readTables.product_revisions} r JOIN products p ON p.id=r.product_id JOIN merchants m ON m.id=p.merchant_id JOIN categories c ON c.id=p.category_id WHERE r.id=?`).get(String(req.params.id)) as any;
  if(!data)return res.status(404).json({message:'审核版本不存在'});
  res.json({data:{...data,...readBookDetails(data),images:JSON.parse(data.images||'[]')}});
});
adminRouter.put('/products/:id/content',(req,res)=>{
  const parsed=z.object({revisionId:z.string(),name:z.string().trim().min(1).max(100),summary:z.string().trim().min(1).max(4000),ageRange:z.string().trim().min(1).max(50),coverUrl:z.string().max(1000),images:z.array(z.string().max(1000)).max(9)}).extend(bookInput.shape).safeParse(req.body);
  if(!parsed.success)return res.status(400).json({message:'请完整填写商品名称、简介、适龄范围和图片'});
  const value=parsed.data,product=db.prepare(`SELECT published_revision_id,(SELECT id FROM ${readTables.product_revisions} WHERE product_id=? ORDER BY version DESC LIMIT 1) latest_revision_id FROM products WHERE id=?`).get(String(req.params.id),String(req.params.id)) as any;
  if(!product)return res.status(404).json({message:'商品不存在'});
  if(value.revisionId!==(product.published_revision_id||product.latest_revision_id))return res.status(409).json({message:'展示版本已变化，请刷新后维护当前图文'});
  const revision=db.prepare(`SELECT id,cover_url,detail_images_json FROM ${readTables.product_revisions} WHERE id=? AND product_id=?`).get(value.revisionId,String(req.params.id)) as any;
  if(!revision)return res.status(409).json({message:'商品版本已变化，请刷新后重试'});
  const existing=[revision.cover_url,...JSON.parse(revision.detail_images_json||'[]')];
  const urls=[value.coverUrl,...value.images].filter(Boolean);
  if(urls.some(url=>!existing.includes(url)&&!db.prepare('SELECT 1 FROM community_uploads WHERE path=? AND user_id=?').get(url,req.actor!.id)))return res.status(400).json({message:'新增图片必须通过当前管理员上传'});
  db.transaction(()=>{
    db.prepare(`UPDATE product_revisions SET name=?,summary=?,age_range=?,cover_url=?${normalizedSchema?'':',detail_images_json=?'} WHERE id=?`).run(value.name,value.summary,value.ageRange,value.coverUrl||null,...(normalizedSchema?[]:[JSON.stringify(value.images)]),value.revisionId);
    if(normalizedSchema)replaceOrderedValues(db,'product_revision_images',value.revisionId,value.images);
    saveBookDetails(value.revisionId,value);
    db.prepare('UPDATE products SET version=version+1,updated_at=? WHERE id=?').run(now(),String(req.params.id));
    audit(req.actor!.id,req.actor!.name,'products','维护图文',String(req.params.id));
  })();broadcast('products','update');broadcast('reviews','update');res.json({ok:true});
});
adminRouter.get('/orders/:id/details',(req,res)=>{
  const id=String(req.params.id),order=db.prepare(`SELECT o.*,${afterSaleColumns},u.username buyerAccount,u.nickname buyerName,m.shop_name shop FROM ${readTables.orders} o JOIN users u ON u.id=o.user_id JOIN merchants m ON m.id=o.merchant_id WHERE o.id=?`).get(id) as any;
  if(!order)return res.status(404).json({message:'订单不存在'});
  const items=withFulfillment(db.prepare(`SELECT id,product_id productId,product_name name,cover_url coverUrl,quantity,unit_price_cents/100.0 price,subtotal_cents/100.0 subtotal FROM ${readTables.order_items} WHERE order_id=?`).all(id));
  const events=db.prepare(`SELECT action,from_status fromStatus,to_status toStatus,note,created_at createdAt FROM order_events WHERE order_id=? ORDER BY created_at`).all(id);
  res.json({data:{...order,address:JSON.parse(order.address_snapshot_json||'{}'),items,events}});
});

const resources=new Set(['users','merchants','categories','reviews','products','orders','ratings','community','notices']);
const categoryInput=z.object({name:z.string().trim().min(1).max(60),parent:z.string().trim().min(1).max(60),sort:z.coerce.number().int().min(0).max(9999),status:z.enum(['已启用','已停用'])});
function validCategory(body:unknown){const parsed=categoryInput.safeParse(body);return parsed.success&&(categoryOptions[parsed.data.parent]||db.prepare('SELECT 1 FROM categories WHERE parent_name=?').get(parsed.data.parent))?parsed.data:null;}
adminRouter.put('/orders/:id/manage',(req,res)=>{
  const input=z.object({action:z.enum(['cancel','ship','transit','deliver','complete']),note:z.string().trim().min(1).max(500),version:z.number().int()}).safeParse(req.body);
  if(!input.success)return res.status(400).json({message:'请选择操作并填写处理说明'});
  const id=String(req.params.id),value=input.data;
  const order=db.prepare(`SELECT * FROM ${readTables.orders} WHERE id=?`).get(id) as any;
  if(!order)return res.status(404).json({message:'订单不存在'});
  if(order.version!==value.version)return res.status(409).json({message:'订单已更新，请重新查看'});
  if(hasBlockingAfterSale(id))return res.status(409).json({message:'订单存在售后或已退款，请在售后管理处理'});
  if(value.action==='cancel'){
    if(order.status!=='PENDING_PAYMENT')return res.status(409).json({message:'仅未付款订单可取消，已付款订单须走售后流程'});
    if(!closePendingCheckout(order.checkout_group_id,req.actor!.id,value.note))return res.status(409).json({message:'订单已处理'});
  }else{
    const next={ship:'SHIPPED',transit:'IN_TRANSIT',deliver:'OUT_FOR_DELIVERY',complete:'COMPLETED'}[value.action];
    const allowed=value.action==='ship'?order.status==='PAID':value.action==='transit'?order.logistics_status==='SHIPPED':value.action==='deliver'?order.logistics_status==='IN_TRANSIT':order.logistics_status==='OUT_FOR_DELIVERY';
    if(!allowed)return res.status(409).json({message:'当前订单状态不允许此操作'});
    db.transaction(()=>{
      const stamp=now();
      const result=db.prepare(`UPDATE orders SET ${normalizedSchema?'':'status=?,'}logistics_status=?,shipped_at=CASE WHEN ?='ship' THEN ? ELSE shipped_at END,completed_at=CASE WHEN ?='complete' THEN ? ELSE completed_at END,updated_at=?,version=version+1 WHERE id=? AND version=?`).run(...(normalizedSchema?[]:[next==='COMPLETED'?'COMPLETED':'SHIPPED']),next,value.action,stamp,value.action,stamp,stamp,id,value.version);
      if(!result.changes)throw new Error('订单已更新');
      db.prepare('INSERT INTO order_events(order_id,actor_id,action,from_status,to_status,note,created_at) VALUES(?,?,?,?,?,?,?)').run(id,req.actor!.id,value.action,order.status,next,value.note,stamp);
      if(value.action==='ship'||value.action==='complete')createOrderNotifications(id,value.action==='ship'?'SHIPPED':'COMPLETED',stamp);
    })();
  }
  audit(req.actor!.id,req.actor!.name,'orders',value.action,id);broadcast('orders','update');broadcast('products','update');if(value.action==='ship'||value.action==='complete')broadcast('notices','create');res.json({ok:true});
});
adminRouter.get('/:resource',(req,res)=>{
  const resource=String(req.params.resource);
  if(!resources.has(resource))return res.status(404).json({message:'接口不存在'});
  res.json({data:rows(resource),updatedAt:now()});
});

adminRouter.put('/:resource/:id',asyncRoute(async(req,res)=>{
  const resource=String(req.params.resource),id=String(req.params.id),b=req.body||{},stamp=now();
  if(resource==='merchants'){
    const parsed=z.object({shop:z.string().trim().min(2).max(60).optional(),approval:z.enum(['待审核','已通过','已驳回']),status:z.enum(['待审核','营业中','已停用']),note:z.string().trim().max(1000).optional(),expectedUpdatedAt:z.string().optional()}).safeParse(b);
    if(!parsed.success)return res.status(400).json({message:'请选择有效审核结果和经营状态'});
    const merchant=db.prepare('SELECT owner_user_id ownerId,updated_at updatedAt FROM merchants WHERE id=?').get(id) as {ownerId:string;updatedAt:string}|undefined;
    if(!merchant)return res.status(404).json({message:'申请不存在'});
    const v=parsed.data;if(v.approval==='已驳回'&&!v.note)return res.status(400).json({message:'驳回申请必须填写原因'});
    if(v.expectedUpdatedAt&&v.expectedUpdatedAt!==merchant.updatedAt)return res.status(409).json({message:'申请已更新或已由其他管理员审核，请刷新详情'});
    if(v.approval!=='已通过'&&v.status==='营业中')return res.status(400).json({message:'只有审核通过的店铺才能营业'});
    db.transaction(()=>{
      db.prepare(`UPDATE merchants SET shop_name=COALESCE(?,shop_name),approval_status=?,business_status=?,rejection_reason=?,reviewed_at=?,reviewed_by=?,updated_at=? WHERE id=?`)
        .run(v.shop??null,v.approval==='已通过'?'APPROVED':v.approval==='已驳回'?'REJECTED':'PENDING',v.approval==='已通过'?(v.status==='营业中'?'OPEN':'SUSPENDED'):'PENDING',v.approval==='已驳回'?v.note:null,v.approval==='待审核'?null:stamp,req.actor!.id,stamp,id);
      if(v.approval==='已通过')db.prepare("INSERT OR IGNORE INTO user_roles(user_id,role) VALUES(?,'MERCHANT')").run(merchant.ownerId);
      else db.prepare("DELETE FROM user_roles WHERE user_id=? AND role='MERCHANT'").run(merchant.ownerId);
      if(v.approval!=='已通过'||v.status!=='营业中'){
        db.prepare("DELETE FROM sessions WHERE user_id=? AND active_role='MERCHANT'").run(merchant.ownerId);
      }
      audit(req.actor!.id,req.actor!.name,'商家申请',v.approval,id);
    })();
    broadcast('merchants','review');broadcast('users','update');return res.json({ok:true});
  }
  if(!resources.has(resource))return res.status(404).json({message:'接口不存在'});
  if(resource==='orders')return res.status(400).json({message:'请使用订单详情中的业务操作，不可直接改写支付状态'});
  if(resource==='categories'&&!validCategory(b))return res.status(400).json({message:'请填写分类名称、选择有效分类类型及非负整数排序'});
  if(resource==='ratings'&&!db.prepare(`SELECT 1 FROM ${readTables.ratings} WHERE id=? AND deleted_at IS NULL`).get(id))return res.status(404).json({message:'评价不存在或已删除'});
  if(resource==='users'){
    const value=z.object({nickname:z.string().trim().min(1).max(60),status:z.enum(['正常','已停用'])}).safeParse(b);
    if(!value.success)return res.status(400).json({message:'请填写昵称并选择账户状态'});
    const existing=db.prepare('SELECT id FROM users WHERE id=?').get(id);
    if(!existing)return res.status(404).json({message:'账户不存在'});
    if(value.data.status==='已停用'&&(id===req.actor!.id||db.prepare("SELECT 1 FROM user_roles WHERE user_id=? AND role IN ('ADMIN','OPERATOR','SUPPORT')").get(id)))
      return res.status(403).json({message:'不能从用户管理停用管理员账户'});
  }
  if(resource==='products'){
    const value=z.object({stock:z.coerce.number().int().min(0),status:z.enum(['草稿','待审核','已驳回','销售中','已下架','平台下架'])}).safeParse(b);
    if(!value.success)return res.status(400).json({message:'库存必须为非负整数，请选择销售状态'});
    if(value.data.status==='销售中'&&!db.prepare(`SELECT 1 FROM products p JOIN ${readTables.product_revisions} r ON r.id=p.published_revision_id WHERE p.id=? AND r.review_status='APPROVED'`).get(id))
      return res.status(400).json({message:'商品尚未审核通过，不能上架'});
  }
  if(resource==='reviews'){
    const revision=db.prepare(`SELECT product_id,version,review_status FROM ${readTables.product_revisions} WHERE id=?`).get(id) as any;
    if(!revision)return res.status(404).json({message:'审核版本不存在'});
    if(!['已通过','已驳回'].includes(b.status))return res.status(400).json({message:'请选择通过或驳回'});
    if(revision.review_status!=='PENDING')return res.status(409).json({message:'此版本已审核，请刷新查看结果'});
    const latest=db.prepare(`SELECT MAX(version) version FROM ${readTables.product_revisions} WHERE product_id=?`).get(revision.product_id) as any;
    if(revision.version!==latest.version)return res.status(409).json({message:'商家已提交新版本，请审核最新版本'});
    if(b.status==='已驳回'&&!String(b.note||'').trim())return res.status(400).json({message:'驳回商品必须填写原因'});
  }
  if(resource==='ratings'&&(!Number.isInteger(Number(b.stars))||Number(b.stars)<1||Number(b.stars)>5||!String(b.content||'').trim()))
    return res.status(400).json({message:'评价星级须为 1 至 5，内容不能为空'});
  db.transaction(()=>{
    if(resource==='users')db.prepare(`UPDATE users SET nickname=COALESCE(?,nickname),status=CASE ? WHEN '已停用' THEN 'DISABLED' ELSE 'ACTIVE' END,updated_at=? WHERE id=?`).run(b.nickname,b.status,stamp,id);
    else if(resource==='merchants')db.prepare(`UPDATE merchants SET shop_name=COALESCE(?,shop_name),approval_status=CASE ? WHEN '已通过' THEN 'APPROVED' WHEN '已驳回' THEN 'REJECTED' ELSE 'PENDING' END,business_status=CASE ? WHEN '营业中' THEN 'OPEN' WHEN '已停用' THEN 'SUSPENDED' ELSE 'PENDING' END,rejection_reason=?,updated_at=? WHERE id=?`).run(b.shop,b.approval,b.status,b.note||null,stamp,id);
    else if(resource==='categories')db.prepare(`UPDATE categories SET name=COALESCE(?,name),parent_name=COALESCE(?,parent_name),sort_order=COALESCE(?,sort_order),status=CASE ? WHEN '已停用' THEN 'DISABLED' ELSE 'ENABLED' END,updated_at=? WHERE id=?`).run(b.name,b.parent,Number(b.sort),b.status,stamp,id);
    else if(resource==='reviews'){
      const status=b.status==='已通过'?'APPROVED':b.status==='已驳回'?'REJECTED':'PENDING';
      db.prepare(`UPDATE product_revisions SET review_status=?,rejection_reason=?,reviewed_at=?,reviewed_by=? WHERE id=?`).run(status,b.note||null,status==='PENDING'?null:stamp,req.actor!.id,id);
      const revision=db.prepare(`SELECT product_id FROM ${readTables.product_revisions} WHERE id=?`).get(id) as any;
      if(status==='APPROVED')db.prepare(`UPDATE products SET published_revision_id=?,status='ONLINE',updated_at=? WHERE id=?`).run(id,stamp,revision.product_id);
      if(status==='REJECTED')db.prepare(`UPDATE products SET status=CASE WHEN published_revision_id IS NULL THEN 'REJECTED' ELSE status END,updated_at=? WHERE id=?`).run(stamp,revision.product_id);
    }
    else if(resource==='products')db.prepare(`UPDATE products SET stock_available=COALESCE(?,stock_available),status=CASE ? WHEN '销售中' THEN 'ONLINE' WHEN '已下架' THEN 'OFFLINE' WHEN '平台下架' THEN 'PLATFORM_OFFLINE' ELSE status END,updated_at=? WHERE id=?`).run(Number(b.stock),b.status,stamp,id);
    else if(resource==='ratings')db.prepare(`UPDATE ratings SET stars=COALESCE(?,stars),content=COALESCE(?,content),status=CASE ? WHEN '已隐藏' THEN 'HIDDEN' ELSE 'PUBLIC' END WHERE id=?`).run(Number(b.stars),b.content,b.status,id);
    else if(resource==='community')db.prepare(`UPDATE community_posts SET title=COALESCE(?,title),status=CASE ? WHEN '已隐藏' THEN 'HIDDEN' ELSE 'PUBLIC' END,updated_at=? WHERE id=?`).run(b.title,b.status,stamp,id);
    else if(resource==='notices')db.prepare(`UPDATE notifications SET title=COALESCE(?,title),content=COALESCE(?,content) WHERE id=?`).run(b.title,b.content,id);
  })();
  audit(req.actor!.id,req.actor!.name,resource,'编辑',id);broadcast(resource,'update');
  res.json({data:(rows(resource) as any[]).find(x=>String(x.id)===id)});
}));

adminRouter.post('/:resource',asyncRoute(async(req,res)=>{
  const resource=String(req.params.resource),b=req.body||{},id=String(b.id||`${String(req.params.resource).slice(0,1).toUpperCase()}${Date.now().toString().slice(-9)}`),stamp=now();
  if(!resources.has(resource))return res.status(404).json({message:'接口不存在'});
  if(resource==='categories'){const value=validCategory(b);if(!value)return res.status(400).json({message:'请填写分类名称、选择有效分类类型及非负整数排序'});db.prepare(`INSERT INTO categories(id,name,parent_name,sort_order,status,created_at,updated_at) VALUES(?,?,?,?,?,?,?)`).run(id,value.name,value.parent,value.sort,value.status==='已停用'?'DISABLED':'ENABLED',stamp,stamp);}
  else if(resource==='notices'){
    const value=z.object({title:z.string().trim().min(1).max(100),content:z.string().trim().min(1).max(2000),audience:z.enum(['全部用户','全部商家','指定账号']),account:z.string().trim().optional()}).safeParse(b);
    if(!value.success)return res.status(400).json({message:'请填写通知标题、正文与接收对象'});
    let userId:string|null=null;
    if(value.data.audience==='指定账号'){
      const target=db.prepare('SELECT id FROM users WHERE username=? OR email=? OR phone=?').get(value.data.account,value.data.account,value.data.account) as {id:string}|undefined;
      if(!target)return res.status(404).json({message:'指定账号不存在'});
      userId=target.id;
    }
    db.prepare(`INSERT INTO notifications(id,user_id,audience,title,content,status,${normalizedSchema?'':'is_read,'}created_at) VALUES(?,?,?,?,?,'SENT',${normalizedSchema?'':'0,'}?)`).run(id,userId,value.data.audience,value.data.title,value.data.content,stamp);
  }
  else if(resource==='users'){
    const value=z.object({account:z.string().trim().min(3).max(100),nickname:z.string().trim().min(1).max(60),password:z.string().min(8).max(128),status:z.enum(['正常','已停用'])}).safeParse(b);
    if(!value.success)return res.status(400).json({message:'请填写账户、昵称和至少 8 位的初始密码'});
    const account=value.data.account;
    if(db.prepare('SELECT 1 FROM users WHERE username=? OR email=?').get(account,account))return res.status(409).json({message:'账号已存在'});
    db.transaction(()=>{
      db.prepare(`INSERT INTO users(id,username,email,phone,nickname,password_hash,status,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?)`).run(id,account,account.includes('@')?account:null,null,value.data.nickname,hashPassword(value.data.password),value.data.status==='已停用'?'DISABLED':'ACTIVE',stamp,stamp);
      db.prepare('UPDATE users SET avatar_url=? WHERE id=?').run(leastUsedDefaultAvatar(),id);
      db.prepare(`INSERT INTO user_roles(user_id,role) VALUES(?,'USER')`).run(id);
    })();
  } else if(resource==='community'){
    const value=postInput.safeParse(b);
    if(!value.success)return res.status(400).json({message:'请填写帖子标题、正文并检查图片'});
    if(value.data.images.some(path=>!db.prepare('SELECT 1 FROM community_uploads WHERE path=? AND post_id IS NULL AND user_id=?').get(path,req.actor!.id)))
      return res.status(400).json({message:'图片不存在，请重新上传'});
    db.transaction(()=>{
      db.prepare(`INSERT INTO community_posts(id,author_user_id,author_role,title,content,cover_url,${normalizedSchema?'':'images_json,'}status,report_count,created_at,updated_at) VALUES(?,?,'USER',?,?,?,${normalizedSchema?'':'?,'}?,0,?,?)`).run(id,req.actor!.id,value.data.title,value.data.content,value.data.images[0]||null,...(normalizedSchema?[]:[JSON.stringify(value.data.images)]),value.data.status==='公开'?'PUBLIC':'HIDDEN',stamp,stamp);
      if(normalizedSchema)replaceOrderedValues(db,'community_post_images',id,value.data.images);
      for(const path of value.data.images)db.prepare('UPDATE community_uploads SET post_id=? WHERE path=?').run(id,path);
    })();
  } else return res.status(400).json({message:'该模块请通过对应业务流程新增，不能由后台直接伪造'});
  audit(req.actor!.id,req.actor!.name,resource,'新增',id);broadcast(resource,'create');res.status(201).json({data:(rows(resource) as any[]).find(x=>String(x.id)===id)});
}));

adminRouter.delete('/:resource/:id',(req,res)=>{
  const resource=String(req.params.resource),id=String(req.params.id);
  if(resource==='ratings'){
    if(!db.prepare("UPDATE ratings SET status='HIDDEN',deleted_at=? WHERE id=? AND deleted_at IS NULL").run(now(),id).changes)return res.status(404).json({message:'评价不存在或已删除'});
  }
  else if(resource==='users'){
    if(id===req.actor!.id)return res.status(400).json({message:'不能停用当前登录的账户'});
    if(db.prepare("SELECT 1 FROM user_roles WHERE user_id=? AND role IN ('ADMIN','OPERATOR','SUPPORT')").get(id))return res.status(403).json({message:'不能删除管理账户'});
    const user=db.prepare(`SELECT 1 FROM user_roles WHERE user_id=? AND role='USER'`).get(id);
    if(!user)return res.status(404).json({message:'用户不存在'});
    db.prepare(`UPDATE users SET status='DISABLED',updated_at=? WHERE id=?`).run(now(),id);
    db.prepare('DELETE FROM sessions WHERE user_id=?').run(id);
  }
  else if(resource==='categories'){
    if(db.prepare('SELECT 1 FROM products WHERE category_id=?').get(id))return res.status(409).json({message:'分类下仍有商品，请先迁移商品或停用分类'});
    if(!db.prepare('DELETE FROM categories WHERE id=?').run(id).changes)return res.status(404).json({message:'分类不存在'});
  }
  else if(resource==='notices'){
    if(!db.prepare('DELETE FROM notifications WHERE id=?').run(id).changes)return res.status(404).json({message:'通知不存在'});
  }
  else if(resource==='community'){
    if(!db.prepare(`UPDATE community_posts SET status='DELETED',updated_at=? WHERE id=?`).run(now(),id).changes)return res.status(404).json({message:'帖子不存在'});
  }
  else return res.status(400).json({message:'该记录有关联业务数据，只允许停用或隐藏'});
  audit(req.actor!.id,req.actor!.name,resource,'删除',id);broadcast(resource,'delete');res.json({ok:true});
});
