import {randomUUID} from 'node:crypto';
import {db,now} from '../core/db.js';
import {productSelect} from '../services/shop-data.js';
import {details} from '../services/product-data.js';
import {merchantApplication} from '../services/merchant-data.js';
import {visibleNotices} from '../services/notice-data.js';
import {SyncError,type Actor} from './types.js';

export function readSyncResources(actor:Actor,resource:string):any[]{
 const products=()=>db.prepare(`${productSelect.replace('SELECT p.id,','SELECT p.id,p.version,p.created_at createdAt,p.updated_at updatedAt,')} WHERE p.status='ONLINE'`).all();
 switch(resource){
  case 'categories':return db.prepare("SELECT id,name,parent_name parent,sort_order sort FROM categories WHERE status='ENABLED' ORDER BY sort_order,id").all();
  case 'products':return products();
  case 'profile':{const row=db.prepare('SELECT id,username,nickname name,avatar_url avatarUrl,version FROM users WHERE id=?').get(actor.id);return [{...(row as object),roles:actor.roles,activeRole:actor.activeRole||'USER'}];}
  case 'addresses':return actor.roles.includes('USER')?db.prepare('SELECT *,is_default isDefault FROM addresses WHERE user_id=? ORDER BY id').all(actor.id):[];
  case 'cart':return actor.roles.includes('USER')?db.prepare(`${productSelect.replace('SELECT p.id,','SELECT ci.quantity,ci.selected,p.id,p.version,').replace(' FROM products p',' FROM cart_items ci JOIN products p ON p.id=ci.product_id')} WHERE ci.user_id=?`).all(actor.id):[];
  case 'favorites':return actor.roles.includes('USER')?db.prepare(`${productSelect} JOIN favorites f ON f.product_id=p.id WHERE f.user_id=? AND p.status='ONLINE'`).all(actor.id):[];
  case 'posts':return db.prepare(`SELECT p.*,p.author_user_id authorId,p.author_role authorRole,p.created_at createdAt,p.cover_url coverUrl,u.nickname authorName,u.avatar_url avatarUrl,(SELECT COUNT(*) FROM comments c WHERE c.post_id=p.id AND c.status='PUBLIC') commentCount,(SELECT COUNT(*) FROM post_likes l WHERE l.post_id=p.id) likeCount,EXISTS(SELECT 1 FROM post_likes l WHERE l.post_id=p.id AND l.user_id=?) liked FROM community_posts p JOIN users u ON u.id=p.author_user_id WHERE p.status='PUBLIC' ORDER BY p.id`).all(actor.id);
  case 'comments':return db.prepare("SELECT c.id,c.post_id postId,c.content,c.created_at createdAt,u.nickname authorName,u.avatar_url avatarUrl FROM comments c JOIN community_posts p ON p.id=c.post_id JOIN users u ON u.id=c.user_id WHERE c.status='PUBLIC' AND p.status='PUBLIC' ORDER BY c.id").all();
  case 'likes':return db.prepare("SELECT l.post_id id,l.post_id postId,1 enabled FROM post_likes l JOIN community_posts p ON p.id=l.post_id WHERE l.user_id=? AND p.status='PUBLIC'").all(actor.id);
  case 'notifications':return visibleNotices(actor.id,actor.roles);
  case 'merchantApplication':{const row=merchantApplication(actor.id);return row?[row]:[];}
  case 'merchantProducts':return actor.merchantId?(db.prepare('SELECT id FROM products WHERE merchant_id=? ORDER BY id').all(actor.merchantId) as Array<{id:string}>).map(row=>({...details(row.id,actor.merchantId!),version:(db.prepare('SELECT version FROM products WHERE id=?').get(row.id) as {version:number}).version})):[];
  case 'checkouts':return actor.roles.includes('USER')?db.prepare('SELECT id,status,total_cents totalCents,expires_at expiresAt,version FROM checkout_groups WHERE user_id=? ORDER BY id').all(actor.id):[];
  case 'buyerOrders':case 'merchantOrders':{
   const merchant=resource==='merchantOrders';if(merchant&&!actor.merchantId)return [];
   return (db.prepare(`SELECT o.*,m.shop_name shop,u.nickname buyer,(SELECT product_name FROM order_items WHERE order_id=o.id LIMIT 1) productName,(SELECT cover_url FROM order_items WHERE order_id=o.id LIMIT 1) coverUrl,(SELECT COUNT(*) FROM order_items WHERE order_id=o.id) itemCount FROM orders o JOIN merchants m ON m.id=o.merchant_id JOIN users u ON u.id=o.user_id WHERE o.${merchant?'merchant_id':'user_id'}=? ORDER BY o.id`).all(merchant?actor.merchantId!:actor.id) as any[]).map(row=>({...row,confirmedState:row.status,items:db.prepare('SELECT id,product_id productId,product_name productName,cover_url coverUrl,unit_price_cents unitPriceCents,quantity,subtotal_cents subtotalCents,EXISTS(SELECT 1 FROM ratings r WHERE r.order_item_id=order_items.id AND r.user_id=?) rated FROM order_items WHERE order_id=?').all(actor.id,row.id),events:db.prepare('SELECT action,from_status fromStatus,to_status toStatus,note,created_at createdAt FROM order_events WHERE order_id=? ORDER BY created_at').all(row.id)}));
  }
  case 'productRatings':return db.prepare("SELECT r.id,r.product_id productId,r.stars,r.content,r.created_at createdAt,u.nickname user,u.nickname authorName,u.avatar_url avatarUrl FROM ratings r JOIN users u ON u.id=r.user_id JOIN products p ON p.id=r.product_id WHERE r.status='PUBLIC' AND r.deleted_at IS NULL AND p.status='ONLINE'").all();
  case 'ownRatings':return db.prepare('SELECT * FROM ratings WHERE user_id=? ORDER BY id').all(actor.id);
  default:throw new SyncError(400,'UNKNOWN_RESOURCE','未知同步资源');
 }
}
export function snapshotPage(actor:Actor,resource:string,offset:number,limit:number,snapshotId?:string):{snapshotId:string;items:any[];nextOffset:number|null;complete:boolean}{
 if(!Number.isInteger(offset)||offset<0||!Number.isInteger(limit)||limit<1||limit>100)throw new SyncError(400,'INVALID_PAGE','快照分页格式不正确');
 let rows:any[],id=snapshotId;
 if(id){const row=db.prepare('SELECT data_json FROM sync_snapshots WHERE id=? AND user_id=? AND resource=? AND expires_at>?').get(id,actor.id,resource,now()) as {data_json:string}|undefined;if(!row)throw new SyncError(409,'SNAPSHOT_EXPIRED','快照已过期或不存在，请重新同步');rows=JSON.parse(row.data_json);}
 else {if(offset!==0)throw new SyncError(400,'INVALID_PAGE','新快照须从第一页开始');rows=db.transaction(()=>readSyncResources(actor,resource))();id=randomUUID();db.prepare('DELETE FROM sync_snapshots WHERE expires_at<=?').run(now());db.prepare('INSERT INTO sync_snapshots(id,user_id,resource,data_json,expires_at) VALUES(?,?,?,?,?)').run(id,actor.id,resource,JSON.stringify(rows),new Date(Date.now()+15*60_000).toISOString());}
 const next=offset+limit;return {snapshotId:id!,items:rows.slice(offset,next),nextOffset:next<rows.length?next:null,complete:next>=rows.length};
}
