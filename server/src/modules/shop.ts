import {withFulfillment} from '../services/after-sale-items.js';
import {afterSaleColumns,orderServiceEvents} from '../services/customer-services.js';
import {businessRoute} from '../services/context.js';
import {favoriteEnable,favoriteDisable,cartSet,cartRemove,addressCreate,addressUpdate,addressDelete} from '../services/buyer.js';
import {checkoutCreate,checkoutPay,orderConfirm,orderCancel,orderShip,orderAdvance} from '../services/orders.js';
import {productOffline,productSubmit} from '../services/product-status.js';
import { Router } from 'express';

import { z } from 'zod';
import {db} from '../core/db.js';
import {requireAuth} from '../core/http.js';


import { matchesAge, themeTerms } from '../core/catalog-filters.js';
import {expirePendingCheckouts} from '../core/order-lifecycle.js';



import { merchantApplicationRouter } from './merchant-applications.js';
import { merchantProductsRouter } from './merchant-products.js';
export const shopRouter=Router();
shopRouter.use(merchantApplicationRouter);
shopRouter.use(merchantProductsRouter);
db.function('matches_age', { deterministic: true }, matchesAge);
shopRouter.use((req,_res,next)=>{if(/^\/(me\/orders|merchant\/orders|checkout)/.test(req.path))expirePendingCheckouts();next();});
import {productSelect} from '../services/shop-data.js';
import {readBookDetails} from '../services/book-details.js';
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
  res.json({data:data.map(row=>({...row as object,...readBookDetails(row)})),total,hasMore:page? page*pageSize<total:false});
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
  const item=data as any;
  const recommendations=db.prepare(`${productSelect} WHERE p.status='ONLINE' AND p.stock_available>0 AND p.id<>? ORDER BY (p.category_id=?) DESC,(r.age_range=?) DESC,p.id ASC LIMIT 4`).all(item.id,item.categoryId,item.ageRange);
  res.json({data:{...item,...readBookDetails(item),ratings,recommendations}});
});

shopRouter.get('/me/favorites',requireAuth('USER'),(req,res)=>res.json({data:db.prepare(`${productSelect} JOIN favorites f ON f.product_id=p.id WHERE f.user_id=? AND p.status='ONLINE' ORDER BY f.created_at DESC`).all(req.actor!.id)}));
shopRouter.post('/me/favorites/:productId',requireAuth('USER'),businessRoute(favoriteEnable));
shopRouter.delete('/me/favorites/:productId',requireAuth('USER'),businessRoute(favoriteDisable));

shopRouter.get('/me/cart',requireAuth('USER'),(req,res)=>res.json({data:db.prepare(`${productSelect.replace(' FROM products p',',ci.quantity,ci.selected FROM cart_items ci JOIN products p ON p.id=ci.product_id')} WHERE ci.user_id=? ORDER BY ci.updated_at DESC`).all(req.actor!.id)}));
shopRouter.put('/me/cart/:productId',requireAuth('USER'),businessRoute(cartSet));
shopRouter.delete('/me/cart/:productId',requireAuth('USER'),businessRoute(cartRemove));

shopRouter.get('/me/addresses',requireAuth('USER'),(req,res)=>res.json({data:db.prepare('SELECT * FROM addresses WHERE user_id=? ORDER BY is_default DESC,created_at DESC').all(req.actor!.id)}));
shopRouter.post('/me/addresses',requireAuth('USER'),businessRoute(addressCreate));

shopRouter.put('/me/addresses/:id',requireAuth('USER'),businessRoute(addressUpdate));
shopRouter.delete('/me/addresses/:id',requireAuth('USER'),businessRoute(addressDelete));

shopRouter.post('/checkout',requireAuth('USER'),businessRoute(checkoutCreate));

shopRouter.get('/checkout/:groupId',requireAuth('USER'),(req,res)=>{
  const group=db.prepare('SELECT id,total_cents totalCents,status,expires_at expiresAt FROM checkout_groups WHERE id=? AND user_id=?').get(String(req.params.groupId),req.actor!.id);
  if(!group)return res.status(404).json({message:'结算单不存在'});
  res.json({data:group});
});

shopRouter.post('/checkout/:groupId/pay',requireAuth('USER'),businessRoute(checkoutPay));

shopRouter.get('/me/orders',requireAuth('USER'),(req,res)=>res.json({data:db.prepare(`SELECT o.*,${afterSaleColumns},m.shop_name shop,
 (SELECT oi.product_name FROM order_items oi WHERE oi.order_id=o.id LIMIT 1) productName,
 (SELECT oi.cover_url FROM order_items oi WHERE oi.order_id=o.id LIMIT 1) coverUrl,
 (SELECT COUNT(*) FROM order_items oi WHERE oi.order_id=o.id) itemCount
 FROM orders o JOIN merchants m ON m.id=o.merchant_id WHERE o.user_id=? ORDER BY o.created_at DESC`).all(req.actor!.id)}));
shopRouter.get('/me/orders/:orderId',requireAuth('USER'),(req,res)=>{
  const order=db.prepare(`SELECT o.*,${afterSaleColumns},m.shop_name shop FROM orders o JOIN merchants m ON m.id=o.merchant_id WHERE o.id=? AND o.user_id=?`).get(String(req.params.orderId),req.actor!.id) as any;
  if(!order)return res.status(404).json({message:'订单不存在'});
  const items=withFulfillment(db.prepare(`SELECT oi.id,oi.product_id productId,oi.product_name productName,oi.cover_url coverUrl,oi.unit_price_cents unitPriceCents,oi.quantity,oi.subtotal_cents subtotalCents,EXISTS(SELECT 1 FROM ratings r WHERE r.order_item_id=oi.id AND r.user_id=?) rated FROM order_items oi WHERE oi.order_id=?`).all(req.actor!.id,order.id));
  const events=db.prepare(`SELECT action,from_status fromStatus,to_status toStatus,note,created_at createdAt FROM order_events WHERE order_id=? ORDER BY created_at`).all(order.id);
  res.json({data:{...order,items,events,serviceEvents:orderServiceEvents(order.id)}});
});
shopRouter.post('/me/orders/:orderId/confirm',requireAuth('USER'),businessRoute(orderConfirm));
shopRouter.post('/me/orders/:orderId/cancel',requireAuth('USER'),businessRoute(orderCancel));

shopRouter.get('/merchant/orders',requireAuth('MERCHANT'),(req,res)=>res.json({data:db.prepare(`SELECT o.*,${afterSaleColumns},u.nickname buyer,
 (SELECT oi.product_name FROM order_items oi WHERE oi.order_id=o.id LIMIT 1) productName,
 (SELECT oi.cover_url FROM order_items oi WHERE oi.order_id=o.id LIMIT 1) coverUrl,
 (SELECT COUNT(*) FROM order_items oi WHERE oi.order_id=o.id) itemCount
 FROM orders o JOIN users u ON u.id=o.user_id WHERE o.merchant_id=? ORDER BY o.created_at DESC`).all(req.actor!.merchantId)}));
shopRouter.get('/merchant/orders/:orderId',requireAuth('MERCHANT'),(req,res)=>{
  const order=db.prepare(`SELECT o.*,${afterSaleColumns},u.nickname buyer FROM orders o JOIN users u ON u.id=o.user_id WHERE o.id=? AND o.merchant_id=?`).get(String(req.params.orderId),req.actor!.merchantId) as any;
  if(!order)return res.status(404).json({message:'订单不存在'});
  const items=withFulfillment(db.prepare('SELECT id,product_id productId,product_name productName,cover_url coverUrl,unit_price_cents unitPriceCents,quantity,subtotal_cents subtotalCents FROM order_items WHERE order_id=?').all(order.id));
  const events=db.prepare('SELECT action,from_status fromStatus,to_status toStatus,note,created_at createdAt FROM order_events WHERE order_id=? ORDER BY created_at').all(order.id);
  res.json({data:{...order,items,events,serviceEvents:orderServiceEvents(order.id)}});
});
shopRouter.post('/merchant/orders/:orderId/ship',requireAuth('MERCHANT'),businessRoute(orderShip));
shopRouter.post('/merchant/orders/:orderId/advance',requireAuth('MERCHANT'),businessRoute(orderAdvance));

shopRouter.get('/merchant/products',requireAuth('MERCHANT'),(req,res)=>res.json({data:db.prepare(`SELECT p.id,p.merchant_id merchantId,m.shop_name shop,p.category_id categoryId,c.name category,
 r.name,r.summary,r.age_range ageRange,r.price_cents priceCents,r.cover_url coverUrl,r.detail_images_json detailImages,
 r.audio_url audioUrl,r.ar_enabled arEnabled,r.ar_resource_url arResourceUrl,p.stock_available stock,p.status,r.review_status reviewStatus
 FROM products p JOIN product_revisions r ON r.id=(SELECT id FROM product_revisions latest WHERE latest.product_id=p.id ORDER BY latest.version DESC LIMIT 1)
 JOIN merchants m ON m.id=p.merchant_id JOIN categories c ON c.id=p.category_id WHERE p.merchant_id=? ORDER BY p.updated_at DESC`).all(req.actor!.merchantId)}));
shopRouter.post('/merchant/products/:productId/offline',requireAuth('MERCHANT'),businessRoute(productOffline));
shopRouter.post('/merchant/products/:productId/submit',requireAuth('MERCHANT'),businessRoute(productSubmit));
