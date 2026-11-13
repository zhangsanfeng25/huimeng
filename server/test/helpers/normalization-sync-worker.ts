import assert from 'node:assert/strict';
import {readFileSync,writeFileSync} from 'node:fs';
import {db,normalizedSchema} from '../../src/core/db.js';
import {executeOnce,canonicalHash} from '../../src/sync/receipts.js';
import {snapshotPage,readSyncResources} from '../../src/sync/snapshots.js';
import {captureBusinessBaseline} from '../../src/normalization/baseline.js';
import {compareBusinessBaselines} from '../../src/normalization/compare.js';
import type {Actor,SyncEnvelope,SyncAction} from '../../src/sync/types.js';
const [mode,statePath]=process.argv.slice(2);
const buyer={id:'U1',name:'测试U1',roles:['USER'],activeRole:'USER'} as Actor;
const merchant={id:'MU1',name:'测试MU1',roles:['MERCHANT'],activeRole:'MERCHANT',merchantId:'M1'} as Actor;
function operation(action:SyncAction,operationId:string,payload:Record<string,unknown>,entityId?:string,baseVersion?:number):SyncEnvelope{
 return {protocolVersion:1,clientId:'legacy-android-1.1.6',action,operationId,payload,payloadHash:canonicalHash(payload),entityId,baseVersion};
}
try{
 if(mode==='prepare'){
  assert.equal(normalizedSchema,false);
  const address=operation('address.save','old-address',{recipient:'离线收件人',phone:'13800000001',region:'测试地区',detail:'离线地址'},'local-address',0);
  const addressResult=executeOnce(buyer,address);
  const payload={addressId:'local-address',items:[{productId:'P1',quantity:1,expectedPriceCents:3000},{productId:'P2',quantity:1,expectedPriceCents:4000}],expectedTotalCents:7000};
  const checkout=operation('checkout.create','old-checkout',payload,'local-checkout',0),checkoutResult=executeOnce(buyer,checkout);
  const pendingPay=operation('checkout.pay','old-pending-pay',{groupId:'local-checkout',expectedTotalCents:7000,method:'DEMO_BALANCE'},'local-checkout',1);
  const cached=snapshotPage(buyer,'buyerOrders',0,1);
  writeFileSync(statePath,JSON.stringify({address,addressResult,checkout,checkoutResult,pendingPay,cached}));
 }else{
  assert.equal(normalizedSchema,true);
  const state=JSON.parse(readFileSync(statePath,'utf8'));
  const before=captureBusinessBaseline(db,'normalized');
  for(let i=0;i<3;i++){
   assert.deepEqual(executeOnce(buyer,state.address),{...state.addressResult,replayed:true});
   assert.deepEqual(executeOnce(buyer,state.checkout),{...state.checkoutResult,replayed:true});
  }
  assert.equal(compareBusinessBaselines(before,captureBusinessBaseline(db,'normalized')).equal,true,'回放旧回执不得产生业务写入');
  const page=snapshotPage(buyer,'buyerOrders',1,100,state.cached.snapshotId);assert.equal(page.snapshotId,state.cached.snapshotId);assert.ok(page.items.length>0);
  const resource=mode==='snapshots'?'buyerOrders':mode==='orders'?'merchantOrders':undefined;
  if(resource){
   const actor=resource==='merchantOrders'?merchant:buyer;
   const fresh=snapshotPage(actor,resource,0,100);assert.ok(fresh.items.length>0);
   for(const order of fresh.items){assert.ok(order.user_id);assert.ok(order.items.every((i:any)=>Number.isInteger(i.subtotalCents)));if(resource==='merchantOrders')assert.equal(order.merchant_id,'M1');}
   for(const name of ['products','posts','productRatings','ownRatings','merchantProducts','notifications'])assert.ok(Array.isArray(readSyncResources(actor,name)));
  }else{
   const paid=executeOnce(buyer,state.pendingPay);assert.equal(executeOnce(buyer,state.pendingPay).replayed,true);assert.equal((db.prepare('SELECT COUNT(*) n FROM payments WHERE checkout_group_id=?').get(state.checkoutResult.entityId) as any).n,1);assert.equal(paid.entityId,state.checkoutResult.entityId);
   const snapshot=snapshotPage(buyer,'buyerOrders',0,100);const orders=snapshot.items.filter((o:any)=>o.checkout_group_id===state.checkoutResult.entityId);assert.equal(orders.length,2);assert.deepEqual(new Set(orders.map((o:any)=>o.merchant_id)),new Set(['M1','M2']));
   const order=orders.find((o:any)=>o.merchant_id==='M1');
   const advanced=executeOnce(merchant,operation('order.advance','new-advance',{},order.id,order.version));assert.equal(advanced.version,order.version+1);
   assert.throws(()=>executeOnce(merchant,operation('order.advance','stale-advance',{},order.id,order.version)),/另一端修改|冲突/);
  }
 }
 assert.deepEqual(db.pragma('foreign_key_check'),[]);
}finally{db.close();}
