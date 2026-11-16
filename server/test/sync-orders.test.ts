import test,{after} from 'node:test';import assert from 'node:assert/strict';import {mkdtempSync} from 'node:fs';import {tmpdir} from 'node:os';import {join} from 'node:path';
const fixture=mkdtempSync(join(tmpdir(),'huimeng-sync-orders-'));process.env.DATABASE_PATH=join(fixture,'db');process.env.UPLOAD_DIR=join(fixture,'uploads');
const {db}=await import('../src/core/db.js');await import('../scripts/migrate.js');await import('../scripts/seed.js');const {executeOnce,canonicalHash}=await import('../src/sync/receipts.js');
const actor={id:'U10001',name:'test',roles:['USER']};after(()=>db.close());
function op(action:string,id:string,payload:Record<string,unknown>,entityId?:string,baseVersion?:number){return {protocolVersion:1 as const,clientId:'orders-test',operationId:id,action:action as import('../src/sync/types.js').SyncAction,payload,payloadHash:canonicalHash(payload),entityId,baseVersion};}
function checkout(){const product=db.prepare("SELECT p.id,r.price_cents price FROM products p JOIN product_revisions r ON r.id=p.published_revision_id WHERE p.status='ONLINE' AND p.stock_available>2 LIMIT 1").get() as {id:string;price:number};const address=db.prepare('SELECT id FROM addresses WHERE user_id=? LIMIT 1').get(actor.id) as {id:string};return {addressId:address.id,items:[{productId:product.id,quantity:1,expectedPriceCents:product.price}],expectedTotalCents:product.price};}
test('changed price and insufficient stock never create a paid order',()=>{const payload=checkout();assert.throws(()=>executeOnce(actor,op('checkout.create','bad-price',{...payload,expectedTotalCents:payload.expectedTotalCents+1})),/金额|价格/);const item=payload.items[0];db.prepare('UPDATE products SET stock_available=0 WHERE id=?').run(item.productId);assert.throws(()=>executeOnce(actor,op('checkout.create','bad-stock',payload)),/库存/);assert.equal((db.prepare("SELECT COUNT(*) n FROM sync_receipts WHERE operation_id IN ('bad-price','bad-stock')").get() as {n:number}).n,0);db.prepare('UPDATE products SET stock_available=50 WHERE id=?').run(item.productId);});
test('checkout and pay replay after response loss without duplicate payment',()=>{const payload=checkout(),create=op('checkout.create','order-1',payload,'local-checkout',0),result=executeOnce(actor,create);assert.equal(executeOnce(actor,create).entityId,result.entityId);const pay=op('checkout.pay','pay-1',{groupId:result.entityId,expectedTotalCents:payload.expectedTotalCents,method:'DEMO_BALANCE'},result.entityId,1);executeOnce(actor,pay);assert.equal(executeOnce(actor,pay).replayed,true);assert.equal((db.prepare('SELECT COUNT(*) n FROM payments WHERE checkout_group_id=?').get(result.entityId) as {n:number}).n,1);});
test('expired payment intent is rejected rather than recreating the order',()=>{const payload=checkout(),result=executeOnce(actor,op('checkout.create','expiring',payload));db.prepare('UPDATE checkout_groups SET expires_at=? WHERE id=?').run('2000-01-01T00:00:00.000Z',result.entityId);assert.throws(()=>executeOnce(actor,op('checkout.pay','expired-pay',{groupId:result.entityId,expectedTotalCents:payload.expectedTotalCents},result.entityId,1)),/过期|支付/);assert.equal((db.prepare('SELECT COUNT(*) n FROM payments WHERE checkout_group_id=?').get(result.entityId) as {n:number}).n,0);});

test('offline feedback and an item return request upload once and accept follow-up actions',()=>{
 const buyer={...actor,activeRole:'USER'};
 const feedback=op('feedback.create','offline-feedback',{title:'订单问题',content:'反馈内容',type:'订单问题',clientId:'feedback-client'},'local-feedback');
 const record=executeOnce(buyer,feedback);
 assert.equal(executeOnce(buyer,feedback).entityId,record.entityId);
 const reply=op('feedback.reply','offline-feedback-reply',{content:'补充说明',clientId:'reply-client'},'local-feedback');
 executeOnce(buyer,reply);
 assert.equal(executeOnce(buyer,reply).replayed,true);
 assert.equal((db.prepare('SELECT COUNT(*) n FROM feedback_replies WHERE feedback_id=?').get(record.entityId) as {n:number}).n,1);
 const payload=checkout();
 const created=executeOnce(buyer,op('checkout.create','service-checkout',payload,'local-service-checkout',0));
 executeOnce(buyer,op('checkout.pay','service-payment',{
  groupId:created.entityId,expectedTotalCents:payload.expectedTotalCents
 },created.entityId,1));
 const orderId=(created.data as {orderIds:string[]}).orderIds[0];
 const request=op('afterSale.create','offline-sale',{
  clientId:'sale-client',orderId,type:'REFUND_ONLY',reason:'售后申请',
  items:[{orderItemId:'local-line',productId:payload.items[0].productId,quantity:1,amountCents:payload.expectedTotalCents}]
 },'local-sale');
 const sale=executeOnce(buyer,request);
 assert.equal(executeOnce(buyer,request).entityId,sale.entityId);
 assert.equal((db.prepare('SELECT COUNT(*) n FROM after_sale_items WHERE after_sale_id=?').get(sale.entityId) as {n:number}).n,1);
 executeOnce(buyer,op('afterSale.cancel','offline-sale-cancel',{},'local-sale'));
 assert.equal((db.prepare('SELECT status FROM after_sales WHERE id=?').get(sale.entityId) as {status:string}).status,'CANCELLED');
});
