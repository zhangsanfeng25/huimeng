import { Router } from 'express';
import { createHash } from 'node:crypto';
import { z } from 'zod';
import { db, now } from '../core/db.js';
import { asyncRoute, audit, requireAuth } from '../core/http.js';
import { broadcast } from '../core/events.js';
import { config } from '../config.js';
import { matchesAge, themeTerms } from '../core/catalog-filters.js';
import { closePendingCheckout, expirePendingCheckouts } from '../core/order-lifecycle.js';
import { createOrderNotifications } from '../core/order-notifications.js';
import type { Statement } from 'better-sqlite3';

import { merchantApplicationRouter } from './merchant-applications.js';
import { merchantProductsRouter } from './merchant-products.js';
export const shopRouter=Router();
shopRouter.use(merchantApplicationRouter);
shopRouter.use(merchantProductsRouter);
db.function('matches_age', { deterministic: true }, matchesAge);
shopRouter.use((req,_res,next)=>{if(/^\/(me\/orders|merchant\/orders|checkout)/.test(req.path))expirePendingCheckouts();next();});
const id=(prefix:string)=>`${prefix}${Date.now().toString(36).toUpperCase()}${Math.random().toString(36).slice(2,7).toUpperCase()}`;
const productSelect=`SELECT p.id,p.merchant_id merchantId,m.shop_name shop,p.category_id categoryId,c.name category,
 r.name,r.summary,r.age_range ageRange,r.price_cents priceCents,r.cover_url coverUrl,r.detail_images_json detailImages,
 r.audio_url audioUrl,r.ar_enabled arEnabled,r.ar_resource_url arResourceUrl,p.stock_available stock,p.status
 FROM products p JOIN product_revisions r ON r.id=p.published_revision_id
 JOIN merchants m ON m.id=p.merchant_id JOIN categories c ON c.id=p.category_id`;

shopRouter.get('/catalog/categories',(_req,res)=>res.json({data:db.prepare(`SELECT id,name,parent_name parent,sort_order sort FROM categories WHERE status='ENABLED' ORDER BY sort_order`).all()}));
shopRouter.get('/catalog/products',(req,res)=>{
  const parsed=z.object({q:z.string().max(100).default(''),category:z.string().max(100).default(''),age:z.string().regex(/^(\d{1,2}-(\d{1,2}|plus))?$/).default(''),theme:z.enum(['','bedtime','emotion','science']).default(''),sort:z.enum(['comprehensive','newest','price_asc','price_desc']).default('comprehensive'),page:z.coerce.number().int().min(1).max(100000).optional(),pageSize:z.coerce.number().int().min(1).max(100).default(20)}).safeParse(req.query);
  if(!parsed.success)return res.status(400).json({message:'筛选或排序参数不正确'});
  const {q,category,age,theme,sort,page,pageSize}=parsed.data;
  const terms=themeTerms[theme]||[];
  const where=` WHERE p.status='ONLINE' AND (?='' OR r.name LIKE '%'||?||'%' OR r.summary LIKE '%'||?||'%') AND (?='' OR p.category_id=?) AND matches_age(r.age_range,?)=1`+(terms.length?` AND (${terms.map(()=>"(r.name||' '||r.summary||' '||c.name) LIKE ?").join(' OR ')})`:'');
  const args=[q.trim(),q.trim(),q.trim(),category,category,age,...terms.map(term=>`%${term}%`)];
  const sortSql={comprehensive:'p.updated_at DESC,p.id ASC',newest:'p.created_at DESC,p.id DESC',price_asc:'r.price_cents ASC,p.id ASC',price_desc:'r.price_cents DESC,p.id ASC'}[sort];
  const total=(db.prepare(`SELECT COUNT(*) total FROM (${productSelect}${where})`).get(...args) as {total:number}).total;
  const data=db.prepare(`${productSelect}${where} ORDER BY ${sortSql}`+(page?' LIMIT ? OFFSET ?':'')).all(...args,...(page?[pageSize,(page-1)*pageSize]:[]));
  res.json({data,total,hasMore:page? page*pageSize<total:false});
});
shopRouter.get('/catalog/products/:id/ratings',(req,res)=>{
  const parsed=z.object({page:z.coerce.number().int().min(1).max(100000).default(1),pageSize:z.coerce.number().int().min(1).max(50).default(20)}).safeParse(req.query);
  if(!parsed.success)return res.status(400).json({message:'评价分页参数不正确'});
  const id=String(req.params.id);if(!db.prepare("SELECT 1 FROM products WHERE id=? AND status='ONLINE'").get(id))return res.status(404).json({message:'商品不存在或已下架'});
  const total=(db.prepare("SELECT COUNT(*) count FROM ratings WHERE product_id=? AND status='PUBLIC' AND deleted_at IS NULL").get(id) as {count:number}).count;
  const {page,pageSize}=parsed.data;
  const data=db.prepare(`SELECT r.id,r.stars,r.content,r.created_at createdAt,u.nickname user,u.avatar_url avatarUrl FROM ratings r JOIN users u ON u.id=r.user_id WHERE r.product_id=? AND r.status='PUBLIC' AND r.deleted_at IS NULL ORDER BY r.created_at DESC,r.id DESC LIMIT ? OFFSET ?`).all(id,pageSize,(page-1)*pageSize);
  res.json({data,total,page,hasMore:page*pageSize<total});
});
shopRouter.get('/catalog/products/:id',(req,res)=>{
  const data=db.prepare(`${productSelect} WHERE p.id=? AND p.status='ONLINE'`).get(String(req.params.id));
  if(!data)return res.status(404).json({message:'商品不存在或已下架'});
  const ratings=db.prepare(`SELECT r.id,r.stars,r.content,r.created_at createdAt,u.nickname user FROM ratings r JOIN users u ON u.id=r.user_id WHERE r.product_id=? AND r.status='PUBLIC' AND r.deleted_at IS NULL ORDER BY r.created_at DESC`).all(String(req.params.id));
  res.json({data:{...(data as any),ratings}});
});

shopRouter.get('/me/favorites',requireAuth('USER'),(req,res)=>res.json({data:db.prepare(`${productSelect} JOIN favorites f ON f.product_id=p.id WHERE f.user_id=? AND p.status='ONLINE' ORDER BY f.created_at DESC`).all(req.actor!.id)}));
shopRouter.post('/me/favorites/:productId',requireAuth('USER'),(req,res)=>{db.prepare('INSERT OR IGNORE INTO favorites(user_id,product_id,created_at) VALUES(?,?,?)').run(req.actor!.id,String(req.params.productId),now());res.status(201).json({ok:true});});
shopRouter.delete('/me/favorites/:productId',requireAuth('USER'),(req,res)=>{db.prepare('DELETE FROM favorites WHERE user_id=? AND product_id=?').run(req.actor!.id,String(req.params.productId));res.json({ok:true});});

shopRouter.get('/me/cart',requireAuth('USER'),(req,res)=>res.json({data:db.prepare(`${productSelect.replace(' FROM products p',',ci.quantity,ci.selected FROM cart_items ci JOIN products p ON p.id=ci.product_id')} WHERE ci.user_id=? ORDER BY ci.updated_at DESC`).all(req.actor!.id)}));
shopRouter.put('/me/cart/:productId',requireAuth('USER'),(req,res)=>{
  const parsed=z.object({quantity:z.number().int().min(1).max(99),selected:z.boolean().optional()}).safeParse(req.body);
  if(!parsed.success)return res.status(400).json({message:'购买数量须为 1 至 99 的整数'});
  const product=db.prepare('SELECT status,stock_available stock FROM products WHERE id=?').get(String(req.params.productId)) as {status:string;stock:number}|undefined;
  if(parsed.data.selected===false){
    const existing=db.prepare('SELECT quantity FROM cart_items WHERE user_id=? AND product_id=?').get(req.actor!.id,String(req.params.productId)) as {quantity:number}|undefined;
    if(existing&&existing.quantity===parsed.data.quantity){db.prepare('UPDATE cart_items SET selected=0,updated_at=? WHERE user_id=? AND product_id=?').run(now(),req.actor!.id,String(req.params.productId));return res.json({ok:true});}
  }
  if(!product||product.status!=='ONLINE'||parsed.data.quantity>product.stock)return res.status(409).json({message:'商品已下架或购买数量超过当前库存'});
  const quantity=parsed.data.quantity,selected=parsed.data.selected===false?0:1;
  db.prepare(`INSERT INTO cart_items(user_id,product_id,quantity,selected,updated_at) VALUES(?,?,?,?,?) ON CONFLICT(user_id,product_id) DO UPDATE SET quantity=excluded.quantity,selected=excluded.selected,updated_at=excluded.updated_at`).run(req.actor!.id,String(req.params.productId),quantity,selected,now());
  res.json({ok:true});
});
shopRouter.delete('/me/cart/:productId',requireAuth('USER'),(req,res)=>{db.prepare('DELETE FROM cart_items WHERE user_id=? AND product_id=?').run(req.actor!.id,String(req.params.productId));res.json({ok:true});});

shopRouter.get('/me/addresses',requireAuth('USER'),(req,res)=>res.json({data:db.prepare('SELECT * FROM addresses WHERE user_id=? ORDER BY is_default DESC,created_at DESC').all(req.actor!.id)}));
shopRouter.post('/me/addresses',requireAuth('USER'),asyncRoute(async(req,res)=>{
  const value=z.object({recipient:z.string().min(1),phone:z.string().min(6),region:z.string().min(2),detail:z.string().min(2),isDefault:z.boolean().optional()}).parse(req.body),addressId=id('ADDR'),stamp=now();
  db.transaction(()=>{if(value.isDefault)db.prepare('UPDATE addresses SET is_default=0 WHERE user_id=?').run(req.actor!.id);db.prepare(`INSERT INTO addresses(id,user_id,recipient,phone,region,detail,is_default,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?)`).run(addressId,req.actor!.id,value.recipient,value.phone,value.region,value.detail,value.isDefault?1:0,stamp,stamp)})();
  res.status(201).json({id:addressId});
}));

shopRouter.put('/me/addresses/:id',requireAuth('USER'),asyncRoute(async(req,res)=>{
  const value=z.object({recipient:z.string().trim().min(1).max(50),phone:z.string().trim().min(6).max(30),region:z.string().trim().min(2).max(100),detail:z.string().trim().min(2).max(300),isDefault:z.boolean().optional()}).parse(req.body);
  const addressId=String(req.params.id),userId=req.actor!.id;
  if(!db.prepare('SELECT 1 FROM addresses WHERE id=? AND user_id=?').get(addressId,userId))return res.status(404).json({message:'收货地址不存在'});
  db.transaction(()=>{
    if(value.isDefault)db.prepare('UPDATE addresses SET is_default=0 WHERE user_id=?').run(userId);
    db.prepare('UPDATE addresses SET recipient=?,phone=?,region=?,detail=?,is_default=?,updated_at=? WHERE id=? AND user_id=?').run(value.recipient,value.phone,value.region,value.detail,value.isDefault?1:0,now(),addressId,userId);
    if(!db.prepare('SELECT 1 FROM addresses WHERE user_id=? AND is_default=1').get(userId))db.prepare('UPDATE addresses SET is_default=1 WHERE id=?').run(addressId);
  })();res.json({ok:true});
}));
shopRouter.delete('/me/addresses/:id',requireAuth('USER'),(req,res)=>{
  const changed=db.transaction(()=>{
    const removed=db.prepare('DELETE FROM addresses WHERE id=? AND user_id=?').run(String(req.params.id),req.actor!.id);
    if(removed.changes&&!db.prepare('SELECT 1 FROM addresses WHERE user_id=? AND is_default=1').get(req.actor!.id))db.prepare('UPDATE addresses SET is_default=1 WHERE id=(SELECT id FROM addresses WHERE user_id=? ORDER BY created_at DESC LIMIT 1)').run(req.actor!.id);
    return removed.changes;
  })();if(!changed)return res.status(404).json({message:'收货地址不存在'});res.json({ok:true});
});

shopRouter.post('/checkout',requireAuth('USER'),asyncRoute(async(req,res)=>{
  const key=String(req.headers['idempotency-key']||'');
  if(!key)return res.status(400).json({message:'缺少 Idempotency-Key'});
  const parsed=z.object({addressId:z.string(),items:z.array(z.object({productId:z.string(),quantity:z.number().int().min(1).max(99)})).min(1)}).safeParse(req.body);
  if(!parsed.success)return res.status(400).json({message:'结算参数不正确'});
  const requestHash=createHash('sha256').update(JSON.stringify(parsed.data)).digest('hex');
  const existing=db.prepare('SELECT id,request_hash requestHash FROM checkout_groups WHERE user_id=? AND idempotency_key=?').get(req.actor!.id,key) as any;
  if(existing){if(existing.requestHash!==requestHash)return res.status(409).json({message:'同一幂等键不能用于不同订单'});return res.json({checkoutGroupId:existing.id,reused:true});}
  const address=db.prepare('SELECT * FROM addresses WHERE id=? AND user_id=?').get(parsed.data.addressId,req.actor!.id) as any;
  if(!address)return res.status(400).json({message:'收货地址不存在'});
  const groupId=id('CG'),stamp=now(),expiresAt=new Date(Date.now()+config.orderTimeoutMinutes*60_000).toISOString();
  const result=db.transaction(()=>{
    let productQuery:Statement,reserveStock:Statement,orderInsert:Statement,itemInsert:Statement,reserveMovement:Statement,createEvent:Statement;
    const byMerchant=new Map<string,Array<any>>();let grand=0;
    for(const item of parsed.data.items){
      const product=(productQuery??=db.prepare(`${productSelect} WHERE p.id=? AND p.status='ONLINE'`)).get(item.productId) as any;
      if(!product)throw new Error('部分商品已下架');
      const changed=(reserveStock??=db.prepare(`UPDATE products SET stock_available=stock_available-?,stock_reserved=stock_reserved+?,updated_at=? WHERE id=? AND stock_available>=?`)).run(item.quantity,item.quantity,stamp,item.productId,item.quantity);
      if(changed.changes!==1)throw new Error(`${product.name} 库存不足`);
      const subtotal=product.priceCents*item.quantity;grand+=subtotal;
      const group=byMerchant.get(product.merchantId)||[];group.push({...item,...product,subtotal});byMerchant.set(product.merchantId,group);
    }
    db.prepare(`INSERT INTO checkout_groups(id,user_id,total_cents,status,version,idempotency_key,request_hash,created_at,expires_at) VALUES(?,?,?,'PENDING',1,?,?,?,?)`).run(groupId,req.actor!.id,grand,key,requestHash,stamp,expiresAt);
    const orderIds:string[]=[];
    for(const [merchantId,items] of byMerchant){
      const orderId=id('HM'),total=items.reduce((n,x)=>n+x.subtotal,0);orderIds.push(orderId);
      (orderInsert??=db.prepare(`INSERT INTO orders(id,checkout_group_id,user_id,merchant_id,address_snapshot_json,total_cents,status,version,expires_at,created_at,updated_at) VALUES(?,?,?,?,?,?,'PENDING_PAYMENT',1,?,?,?)`)).run(orderId,groupId,req.actor!.id,merchantId,JSON.stringify(address),total,expiresAt,stamp,stamp);
      for(const item of items){
        (itemInsert??=db.prepare(`INSERT INTO order_items(id,order_id,product_id,product_name,cover_url,unit_price_cents,quantity,subtotal_cents) VALUES(?,?,?,?,?,?,?,?)`)).run(id('OI'),orderId,item.productId,item.name,item.coverUrl,item.priceCents,item.quantity,item.subtotal);
        (reserveMovement??=db.prepare(`INSERT INTO stock_movements(product_id,order_id,type,available_delta,reserved_delta,created_at) VALUES(?,?,'RESERVE',?,?,?)`)).run(item.productId,orderId,-item.quantity,item.quantity,stamp);
      }
      (createEvent??=db.prepare(`INSERT INTO order_events(order_id,actor_id,action,from_status,to_status,created_at) VALUES(?,?,'CREATE',NULL,'PENDING_PAYMENT',?)`)).run(orderId,req.actor!.id,stamp);
      createOrderNotifications(orderId,'CREATED',stamp);
    }
    return {checkoutGroupId:groupId,orderIds,totalCents:grand,expiresAt};
  })();
  broadcast('orders','create');broadcast('products','stock');broadcast('notices','create');res.status(201).json(result);
}));

shopRouter.get('/checkout/:groupId',requireAuth('USER'),(req,res)=>{
  const group=db.prepare('SELECT id,total_cents totalCents,status,expires_at expiresAt FROM checkout_groups WHERE id=? AND user_id=?').get(String(req.params.groupId),req.actor!.id);
  if(!group)return res.status(404).json({message:'结算单不存在'});
  res.json({data:group});
});

shopRouter.post('/checkout/:groupId/pay',requireAuth('USER'),asyncRoute(async(req,res)=>{
  const key=String(req.headers['idempotency-key']||'');if(!key)return res.status(400).json({message:'缺少 Idempotency-Key'});
  const method=z.enum(['DEMO_BALANCE','DEMO_ALIPAY','DEMO_WECHAT']).parse(req.body?.method||'DEMO_BALANCE');
  const groupId=String(req.params.groupId),stamp=now();
  const result=db.transaction(()=>{
    let payEvent:Statement,deductReservedStock:Statement,payMovement:Statement;
    const group=db.prepare(`SELECT * FROM checkout_groups WHERE id=? AND user_id=?`).get(groupId,req.actor!.id) as any;
    if(!group)throw new Error('结算单不存在');
    const paid=db.prepare('SELECT * FROM payments WHERE checkout_group_id=?').get(groupId) as any;if(paid)return {paymentId:paid.id,reused:true};
    if(group.status!=='PENDING'||group.expires_at<=stamp)throw new Error('订单已过期或不能支付');
    const paymentId=id('PAY');
    db.prepare(`INSERT INTO payments(id,checkout_group_id,amount_cents,method,idempotency_key,paid_at) VALUES(?,?,?,?,?,?)`).run(paymentId,groupId,group.total_cents,method,key,stamp);
    db.prepare(`UPDATE checkout_groups SET status='PAID',version=version+1 WHERE id=?`).run(groupId);
    db.prepare(`UPDATE orders SET status='PAID',logistics_status='PREPARING',paid_at=?,updated_at=?,version=version+1 WHERE checkout_group_id=? AND status='PENDING_PAYMENT'`).run(stamp,stamp,groupId);
    for(const order of db.prepare(`SELECT id FROM orders WHERE checkout_group_id=?`).all(groupId) as any[]){
      (payEvent??=db.prepare(`INSERT INTO order_events(order_id,actor_id,action,from_status,to_status,created_at) VALUES(?,?,'PAY','WAITING_PAYMENT','PREPARING',?)`)).run(order.id,req.actor!.id,stamp);
      createOrderNotifications(order.id,'PAID',stamp);
    }
    for(const item of db.prepare(`SELECT oi.product_id,oi.quantity FROM order_items oi JOIN orders o ON o.id=oi.order_id WHERE o.checkout_group_id=?`).all(groupId) as any[]){
      (deductReservedStock??=db.prepare(`UPDATE products SET stock_reserved=stock_reserved-? WHERE id=? AND stock_reserved>=?`)).run(item.quantity,item.product_id,item.quantity);
      (payMovement??=db.prepare(`INSERT INTO stock_movements(product_id,type,available_delta,reserved_delta,created_at) VALUES(?,'PAY',0,?,?)`)).run(item.product_id,-item.quantity,stamp);
    }
    db.prepare(`DELETE FROM cart_items WHERE user_id=? AND product_id IN
      (SELECT oi.product_id FROM order_items oi JOIN orders o ON o.id=oi.order_id WHERE o.checkout_group_id=?)`).run(req.actor!.id,groupId);
    return {paymentId,paidAt:stamp};
  })();broadcast('orders','pay');if(!result.reused)broadcast('notices','create');res.json(result);
}));

shopRouter.get('/me/orders',requireAuth('USER'),(req,res)=>res.json({data:db.prepare(`SELECT o.*,m.shop_name shop,
 (SELECT oi.product_name FROM order_items oi WHERE oi.order_id=o.id LIMIT 1) productName,
 (SELECT oi.cover_url FROM order_items oi WHERE oi.order_id=o.id LIMIT 1) coverUrl,
 (SELECT COUNT(*) FROM order_items oi WHERE oi.order_id=o.id) itemCount
 FROM orders o JOIN merchants m ON m.id=o.merchant_id WHERE o.user_id=? ORDER BY o.created_at DESC`).all(req.actor!.id)}));
shopRouter.get('/me/orders/:orderId',requireAuth('USER'),(req,res)=>{
  const order=db.prepare(`SELECT o.*,m.shop_name shop FROM orders o JOIN merchants m ON m.id=o.merchant_id WHERE o.id=? AND o.user_id=?`).get(String(req.params.orderId),req.actor!.id) as any;
  if(!order)return res.status(404).json({message:'订单不存在'});
  const items=db.prepare(`SELECT oi.id,oi.product_id productId,oi.product_name productName,oi.cover_url coverUrl,oi.unit_price_cents unitPriceCents,oi.quantity,oi.subtotal_cents subtotalCents,EXISTS(SELECT 1 FROM ratings r WHERE r.order_item_id=oi.id AND r.user_id=?) rated FROM order_items oi WHERE oi.order_id=?`).all(req.actor!.id,order.id);
  const events=db.prepare(`SELECT action,from_status fromStatus,to_status toStatus,note,created_at createdAt FROM order_events WHERE order_id=? ORDER BY created_at`).all(order.id);
  res.json({data:{...order,items,events}});
});
shopRouter.post('/me/orders/:orderId/confirm',requireAuth('USER'),(req,res)=>{
  const stamp=now(),orderId=String(req.params.orderId);
  const completed=db.transaction(()=>{
    const changed=db.prepare(`UPDATE orders SET status='COMPLETED',logistics_status='COMPLETED',completed_at=?,updated_at=?,version=version+1 WHERE id=? AND user_id=? AND status='SHIPPED' AND logistics_status='OUT_FOR_DELIVERY'`).run(stamp,stamp,orderId,req.actor!.id);
    if(changed.changes!==1)return false;
    db.prepare(`INSERT INTO order_events(order_id,actor_id,action,from_status,to_status,created_at) VALUES(?,?, 'CONFIRM','OUT_FOR_DELIVERY','COMPLETED',?)`).run(orderId,req.actor!.id,stamp);
    createOrderNotifications(orderId,'COMPLETED',stamp);return true;
  })();
  if(!completed)return res.status(409).json({message:'订单尚未进入派送阶段'});
  broadcast('orders','complete');broadcast('notices','create');res.json({ok:true});
});
shopRouter.post('/me/orders/:orderId/cancel',requireAuth('USER'),(req,res)=>{
  const order=db.prepare('SELECT checkout_group_id groupId,status FROM orders WHERE id=? AND user_id=?').get(String(req.params.orderId),req.actor!.id) as {groupId:string;status:string}|undefined;
  if(!order)return res.status(404).json({message:'订单不存在'});
  if(order.status==='CANCELLED')return res.json({ok:true,reused:true});
  if(order.status!=='PENDING_PAYMENT')return res.status(409).json({message:'仅待付款订单可以取消，已付款订单请申请售后'});
  if(!closePendingCheckout(order.groupId,req.actor!.id,'买家取消待付款结算单'))return res.status(409).json({message:'订单状态已变化，请刷新'});
  broadcast('orders','cancel');broadcast('products','stock');res.json({ok:true});
});

shopRouter.get('/merchant/orders',requireAuth('MERCHANT'),(req,res)=>res.json({data:db.prepare(`SELECT o.*,u.nickname buyer,
 (SELECT oi.product_name FROM order_items oi WHERE oi.order_id=o.id LIMIT 1) productName,
 (SELECT oi.cover_url FROM order_items oi WHERE oi.order_id=o.id LIMIT 1) coverUrl,
 (SELECT COUNT(*) FROM order_items oi WHERE oi.order_id=o.id) itemCount
 FROM orders o JOIN users u ON u.id=o.user_id WHERE o.merchant_id=? ORDER BY o.created_at DESC`).all(req.actor!.merchantId)}));
shopRouter.get('/merchant/orders/:orderId',requireAuth('MERCHANT'),(req,res)=>{
  const order=db.prepare('SELECT o.*,u.nickname buyer FROM orders o JOIN users u ON u.id=o.user_id WHERE o.id=? AND o.merchant_id=?').get(String(req.params.orderId),req.actor!.merchantId) as any;
  if(!order)return res.status(404).json({message:'订单不存在'});
  const items=db.prepare('SELECT id,product_id productId,product_name productName,cover_url coverUrl,unit_price_cents unitPriceCents,quantity,subtotal_cents subtotalCents FROM order_items WHERE order_id=?').all(order.id);
  const events=db.prepare('SELECT action,from_status fromStatus,to_status toStatus,note,created_at createdAt FROM order_events WHERE order_id=? ORDER BY created_at').all(order.id);
  res.json({data:{...order,items,events}});
});
shopRouter.post('/merchant/orders/:orderId/ship',requireAuth('MERCHANT'),(req,res)=>{
  const stamp=now(),orderId=String(req.params.orderId),trackingNo=`DEMO${Date.now()}`;
  const shipped=db.transaction(()=>{
    const changed=db.prepare(`UPDATE orders SET status='SHIPPED',logistics_status='SHIPPED',shipped_at=?,updated_at=?,version=version+1 WHERE id=? AND merchant_id=? AND status='PAID'`).run(stamp,stamp,orderId,req.actor!.merchantId);
    if(changed.changes!==1)return false;
    db.prepare(`INSERT INTO order_events(order_id,actor_id,action,from_status,to_status,note,created_at) VALUES(?,?,'SHIP','READY_TO_SHIP','SHIPPED',?,?)`).run(orderId,req.actor!.id,trackingNo,stamp);
    createOrderNotifications(orderId,'SHIPPED',stamp);return true;
  })();
  if(!shipped)return res.status(409).json({message:'订单不存在或状态不允许发货'});
  audit(req.actor!.id,req.actor!.name,'订单','模拟发货',orderId);broadcast('orders','ship');broadcast('notices','create');res.json({ok:true,trackingNo});
});
shopRouter.post('/merchant/orders/:orderId/advance',requireAuth('MERCHANT'),(req,res)=>{
  const orderId=String(req.params.orderId),stamp=now(),order=db.prepare(`SELECT status,logistics_status logisticsStatus FROM orders WHERE id=? AND merchant_id=?`).get(orderId,req.actor!.merchantId) as any;if(!order)return res.status(404).json({message:'订单不存在'});
  const next:Record<string,string>={PREPARING:'READY_TO_SHIP',READY_TO_SHIP:'SHIPPED',SHIPPED:'IN_TRANSIT',IN_TRANSIT:'OUT_FOR_DELIVERY'},to=next[order.logisticsStatus];if(!to)return res.status(409).json({message:'当前物流状态无需继续更新'});
  const status=to==='SHIPPED'?'SHIPPED':order.status,tracking=to==='SHIPPED'?`DEMO${Date.now()}`:null;
  db.transaction(()=>{db.prepare(`UPDATE orders SET status=?,logistics_status=?,shipped_at=CASE WHEN ?='SHIPPED' THEN ? ELSE shipped_at END,updated_at=?,version=version+1 WHERE id=?`).run(status,to,to,stamp,stamp,orderId);db.prepare(`INSERT INTO order_events(order_id,actor_id,action,from_status,to_status,note,created_at) VALUES(?,?,'ADVANCE',?,?,?,?)`).run(orderId,req.actor!.id,order.logisticsStatus,to,tracking,stamp);if(to==='SHIPPED')createOrderNotifications(orderId,'SHIPPED',stamp)})();
  broadcast('orders','advance');if(to==='SHIPPED')broadcast('notices','create');res.json({ok:true,status,toStatus:to,trackingNo:tracking});
});

shopRouter.get('/merchant/products',requireAuth('MERCHANT'),(req,res)=>res.json({data:db.prepare(`SELECT p.id,p.merchant_id merchantId,m.shop_name shop,p.category_id categoryId,c.name category,
 r.name,r.summary,r.age_range ageRange,r.price_cents priceCents,r.cover_url coverUrl,r.detail_images_json detailImages,
 r.audio_url audioUrl,r.ar_enabled arEnabled,r.ar_resource_url arResourceUrl,p.stock_available stock,p.status,r.review_status reviewStatus
 FROM products p JOIN product_revisions r ON r.id=(SELECT id FROM product_revisions latest WHERE latest.product_id=p.id ORDER BY latest.version DESC LIMIT 1)
 JOIN merchants m ON m.id=p.merchant_id JOIN categories c ON c.id=p.category_id WHERE p.merchant_id=? ORDER BY p.updated_at DESC`).all(req.actor!.merchantId)}));
shopRouter.post('/merchant/products/:productId/offline',requireAuth('MERCHANT'),(req,res)=>{const changed=db.prepare(`UPDATE products SET status='OFFLINE',updated_at=? WHERE id=? AND merchant_id=? AND status='ONLINE'`).run(now(),String(req.params.productId),req.actor!.merchantId);if(changed.changes!==1)return res.status(409).json({message:'仅已上架商品可以下架'});broadcast('products','offline');res.json({ok:true});});
shopRouter.post('/merchant/products/:productId/submit',requireAuth('MERCHANT'),(req,res)=>{
  const productId=String(req.params.productId),stamp=now();const product=db.prepare(`SELECT id FROM products WHERE id=? AND merchant_id=?`).get(productId,req.actor!.merchantId);if(!product)return res.status(404).json({message:'商品不存在'});const revision=db.prepare(`SELECT id FROM product_revisions WHERE product_id=? ORDER BY version DESC LIMIT 1`).get(productId) as any;db.transaction(()=>{db.prepare(`UPDATE product_revisions SET review_status='PENDING',submitted_at=?,rejection_reason=NULL WHERE id=?`).run(stamp,revision.id);db.prepare(`UPDATE products SET status='PENDING_REVIEW',updated_at=? WHERE id=?`).run(stamp,productId)})();broadcast('reviews','submit');res.json({ok:true,revisionId:revision.id});
});
