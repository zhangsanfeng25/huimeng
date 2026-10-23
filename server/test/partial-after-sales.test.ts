import test,{after} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtempSync} from 'node:fs';import {tmpdir} from 'node:os';import {join} from 'node:path';
process.env.DATABASE_PATH=join(mkdtempSync(join(tmpdir(),'huimeng-partial-')),'db');
const {db}=await import('../src/core/db.js');await import('../scripts/migrate.js');await import('../scripts/seed.js');
const {serviceAction,serviceDetail,orderAfterSale,hasBlockingAfterSale}=await import('../src/services/customer-services.js');
const {withFulfillment}=await import('../src/services/after-sale-items.js');
const {runBusiness}=await import('../src/services/context.js');
const buyer={id:'U10001',name:'buyer',roles:['USER'],activeRole:'USER'};
const merchant={id:'M10001',name:'merchant',roles:['USER','MERCHANT'],merchantId:'M20001',activeRole:'MERCHANT'};
const owner=db.prepare('SELECT owner_user_id id FROM merchants WHERE id=?').get(merchant.merchantId) as any;merchant.id=owner.id;
const admin={id:'A0001',name:'admin',roles:['ADMIN'],activeRole:'ADMIN'};
let n=0;
function order(status='SHIPPED'){
 const id='PARTIAL-'+(++n),group=(db.prepare('SELECT id FROM checkout_groups LIMIT 1').get() as any).id;
 const product=(db.prepare('SELECT id FROM products WHERE merchant_id=? LIMIT 1').get(merchant.merchantId) as any).id;
 db.prepare("INSERT INTO orders(id,checkout_group_id,user_id,merchant_id,address_snapshot_json,total_cents,status,expires_at,created_at,updated_at) VALUES(?,?,?,?, '{}',9000,?,'2099-01-01','2026-10-23','2026-10-23')").run(id,group,buyer.id,merchant.merchantId,status);
 db.prepare("INSERT INTO order_items(id,order_id,product_id,product_name,unit_price_cents,quantity,subtotal_cents) VALUES(?,?,?,'测试绘本',3000,3,9000)").run(id+'-item',id,product);
 return {id,item:id+'-item',product};
}
function act(actor:any,id:string,action:string,payload:any={}){return runBusiness(serviceAction,actor,{...payload,action},{kind:'after-sales',id});}
function create(o:ReturnType<typeof order>,quantity:number,amountCents:number,type='REFUND_ONLY',clientId='case-'+(++n)){
 return act(buyer,'','create',{orderId:o.id,type,reason:'商品问题',clientId,items:[{orderItemId:o.item,quantity,amountCents}]}).data.data.id as string;
}
function finish(id:string,returns=false){act(merchant,id,'merchant-review',{approve:true,note:'同意'});act(admin,id,'approve',{note:'复核通过',returnAddress:'测试地址'});if(returns){act(buyer,id,'return',{carrier:'测试快递',trackingNo:'TEST'});act(merchant,id,'receive');}return act(admin,id,'refund');}
after(()=>db.close());
test('repeat application eligibility follows remaining items, completed, rejected and cancelled outcomes',()=>{
 const o=order('PAID');const a=create(o,1,3000);
 assert.equal(serviceDetail(buyer,'after-sales',a).can_apply_again,false);
 finish(a);assert.equal(serviceDetail(buyer,'after-sales',a).can_apply_again,true);
 const b=create(o,2,6000);finish(b);
 assert.equal(serviceDetail(buyer,'after-sales',b).can_apply_again,false);
 assert.equal(serviceDetail(buyer,'after-sales',a).can_apply_again,false);
 const other=order();const c=create(other,1,1000);act(buyer,c,'cancel');
 assert.equal(serviceDetail(buyer,'after-sales',c).can_apply_again,true);
 const d=create(other,1,1000);act(merchant,d,'merchant-review',{approve:false,note:'凭证不符'});act(admin,d,'reject',{note:'凭证不符'});
 assert.equal(serviceDetail(buyer,'after-sales',d).can_apply_again,true);
 const e=create(other,1,1000);assert.equal(serviceDetail(buyer,'after-sales',d).can_apply_again,false);
 assert.equal(serviceDetail(buyer,'after-sales',e).can_apply_again,false);
});
test('partial compensation leaves inventory unchanged and supports further item refunds',()=>{
 const o=order(),before=(db.prepare('SELECT stock_available n FROM products WHERE id=?').get(o.product) as any).n;
 const a=create(o,1,1000);assert.equal(serviceDetail(buyer,'after-sales',a).amount_cents,1000);
 assert.throws(()=>create(o,1,1000),/进行中/);finish(a);
 assert.equal((db.prepare('SELECT stock_available n FROM products WHERE id=?').get(o.product) as any).n,before);
 assert.equal(hasBlockingAfterSale(o.id),false);
 const b=create(o,1,3000,'RETURN_REFUND');finish(b,true);act(admin,b,'refund');
 assert.equal((db.prepare('SELECT stock_available n FROM products WHERE id=?').get(o.product) as any).n,before+1);
 const context=orderAfterSale(buyer,o.id);assert.equal(context.order.refunded_cents,4000);
 assert.equal(context.items[0].refundable_cents,5000);assert.equal(context.items[0].returnable_quantity,2);
 const c=create(o,2,5000,'RETURN_REFUND');finish(c,true);
 assert.equal((db.prepare('SELECT SUM(amount_cents) n FROM refunds WHERE order_id=?').get(o.id) as any).n,9000);
 assert.equal(hasBlockingAfterSale(o.id),true);assert.throws(()=>create(o,1,1),/退款|额度|数量/);
});
test('rejects foreign, duplicate, fractional, excessive quantity and amount; cancelled cases release reservation',()=>{
 const o=order();assert.throws(()=>create(o,1,3001),/金额|额度/);assert.throws(()=>create(o,4,1000),/数量/);assert.throws(()=>create(o,1,0),/格式|金额/);
 const payload={orderId:o.id,type:'RETURN_REFUND',reason:'问题',clientId:'bad-items'};
 assert.throws(()=>act(buyer,'','create',{...payload,items:[{orderItemId:'foreign',quantity:1,amountCents:1}]}),/商品/);
 const item={orderItemId:o.item,quantity:1,amountCents:1000};assert.throws(()=>act(buyer,'','create',{...payload,items:[item,item]}),/重复/);
 const a=create(o,1,1000);act(buyer,a,'cancel');const b=create(o,1,1000);assert.ok(b);
});
test('unshipped full item refund restores only selected quantity and does not mark remaining order fully refunded',()=>{
 const o=order('PAID'),before=(db.prepare('SELECT stock_available n FROM products WHERE id=?').get(o.product) as any).n;
 const a=create(o,1,3000);finish(a);assert.equal((db.prepare('SELECT stock_available n FROM products WHERE id=?').get(o.product) as any).n,before+1);
 assert.equal(hasBlockingAfterSale(o.id),false);assert.equal(serviceDetail(buyer,'after-sales',a).items[0].quantity,1);
 const items=withFulfillment(db.prepare('SELECT * FROM order_items WHERE order_id=?').all(o.id));assert.equal(items[0].quantity,3);assert.equal(items[0].cancelledQuantity,1);assert.equal(items[0].fulfillableQuantity,2);
});
test('successive unshipped compensation ending in full refund restores every outstanding unit once',()=>{
 const o=order('PAID'),before=(db.prepare('SELECT stock_available n FROM products WHERE id=?').get(o.product) as any).n;
 finish(create(o,1,1000));finish(create(o,1,2000));const last=create(o,2,6000);finish(last);act(admin,last,'refund');
 assert.equal((db.prepare('SELECT stock_available n FROM products WHERE id=?').get(o.product) as any).n,before+3);
 assert.equal(serviceDetail(buyer,'after-sales',last).items[0].restock_quantity,3);assert.equal(hasBlockingAfterSale(o.id),true);
});
