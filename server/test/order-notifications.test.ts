import test, { after } from 'node:test';
import assert from 'node:assert/strict';
import express from 'express';
import { mkdtempSync, rmSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';

const fixture=mkdtempSync(join(tmpdir(),'huimeng-order-notices-'));
process.env.DATABASE_PATH=join(fixture,'test.db');
const {db}=await import('../src/core/db.js');
await import('../scripts/migrate.js');await import('../scripts/seed.js');
const {authRouter}=await import('../src/modules/auth.js');
const {shopRouter}=await import('../src/modules/shop.js');
const {communityRouter}=await import('../src/modules/community.js');
const {adminRouter}=await import('../src/modules/admin.js');
const app=express();app.use(express.json());app.use('/auth',authRouter);app.use('/admin',adminRouter);app.use(shopRouter);app.use(communityRouter);
app.use((error:Error,_req:express.Request,res:express.Response,_next:express.NextFunction)=>res.status(400).json({message:error.message}));
const server=app.listen(0,'127.0.0.1');await new Promise<void>(resolve=>server.once('listening',resolve));
const address=server.address();assert.ok(address&&typeof address!=='string');const base=`http://127.0.0.1:${address.port}`;
after(async()=>{await new Promise<void>(resolve=>server.close(()=>resolve()));db.close();rmSync(fixture,{recursive:true,force:true});});
async function login(username:string,role='USER'){const response=await fetch(base+'/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username,password:'demo12345',role})});assert.equal(response.status,200);return (await response.json() as {token:string}).token;}
const buyer=await login('xingxing'),merchant=await login('merchant_a','MERCHANT'),other=await login('xiaoxiong');
async function call(path:string,actor:string,body?:unknown,key?:string){return fetch(base+path,{method:body===undefined?'GET':'POST',headers:{Authorization:`Bearer ${actor}`,'Content-Type':'application/json',...(key?{'Idempotency-Key':key}:{})},body:body===undefined?undefined:JSON.stringify(body)});}
async function notices(actor:string,orderId:string){const response=await call('/me/notifications',actor);assert.equal(response.status,200);const {data}=await response.json() as {data:Array<{id:string;title:string;content:string;is_read:number}>};return data.filter(n=>n.content.includes(orderId));}

test('下单、付款、发货和确认收货同步生成买家与商家的未读消息，重试不重复',async()=>{
  const address=db.prepare('SELECT id FROM addresses WHERE user_id=? LIMIT 1').get('U10001') as {id:string};
  const product=db.prepare("SELECT p.id FROM products p JOIN merchants m ON m.id=p.merchant_id JOIN users u ON u.id=m.owner_user_id WHERE u.username='merchant_a' AND p.status='ONLINE' LIMIT 1").get() as {id:string};
  const input={addressId:address.id,items:[{productId:product.id,quantity:1}]};
  const createdResponse=await call('/checkout',buyer,input,'notice-checkout');assert.equal(createdResponse.status,201);
  const created=await createdResponse.json() as {checkoutGroupId:string;orderIds:string[]};const orderId=created.orderIds[0];
  assert.equal((await notices(buyer,orderId)).length,1);assert.equal((await notices(merchant,orderId)).length,1);
  assert.equal((await notices(other,orderId)).length,0);
  assert.equal((await call('/checkout',buyer,input,'notice-checkout')).status,200);
  assert.equal((await notices(buyer,orderId)).length,1);
  assert.equal((await call(`/checkout/${created.checkoutGroupId}/pay`,buyer,{method:'DEMO_BALANCE'},'notice-pay')).status,200);
  assert.equal((await notices(buyer,orderId)).length,2);assert.equal((await notices(merchant,orderId)).length,2);
  assert.equal((await call(`/checkout/${created.checkoutGroupId}/pay`,buyer,{method:'DEMO_BALANCE'},'notice-pay')).status,200);
  assert.equal((await notices(buyer,orderId)).length,2);
  assert.equal((await call(`/me/orders/${orderId}/confirm`,buyer,{})).status,409);
  assert.equal((await notices(buyer,orderId)).length,2);
  assert.equal((await call(`/merchant/orders/${orderId}/ship`,merchant,{})).status,200);
  assert.equal((await notices(buyer,orderId)).length,3);assert.equal((await notices(merchant,orderId)).length,3);
  assert.equal((await call(`/merchant/orders/${orderId}/ship`,merchant,{})).status,409);
  assert.equal((await notices(buyer,orderId)).length,3);
  assert.equal((await call(`/merchant/orders/${orderId}/advance`,merchant,{})).status,200);
  assert.equal((await call(`/merchant/orders/${orderId}/advance`,merchant,{})).status,200);
  assert.equal((await call(`/me/orders/${orderId}/confirm`,buyer,{})).status,200);
  const buyerNotices=await notices(buyer,orderId),merchantNotices=await notices(merchant,orderId);
  assert.equal(buyerNotices.length,4);assert.equal(merchantNotices.length,4);
  assert.ok(buyerNotices.every(n=>n.is_read===0));assert.ok(merchantNotices.every(n=>n.is_read===0));
  assert.ok(buyerNotices.some(n=>n.title.includes('发货')));assert.ok(merchantNotices.some(n=>n.title.includes('完成')));
  assert.equal((await call(`/me/orders/${orderId}/confirm`,buyer,{})).status,409);
  assert.equal((await notices(buyer,orderId)).length,4);assert.equal((await notices(other,orderId)).length,0);
});

test('订单事务失败时不会留下付款或发货提醒',async()=>{
  const before=(db.prepare('SELECT COUNT(*) count FROM notifications').get() as {count:number}).count;
  assert.equal((await call('/checkout',buyer,{addressId:'missing',items:[{productId:'P0001',quantity:1}]},'invalid-notice')).status,400);
  assert.equal((await call('/checkout/missing/pay',buyer,{method:'DEMO_BALANCE'},'invalid-pay')).status,400);
  assert.equal((await call('/merchant/orders/missing/ship',merchant,{})).status,409);
  assert.equal((db.prepare('SELECT COUNT(*) count FROM notifications').get() as {count:number}).count,before);
});

async function paidOrder(key:string){
  const address=db.prepare('SELECT id FROM addresses WHERE user_id=? LIMIT 1').get('U10001') as {id:string};
  const product=db.prepare("SELECT p.id FROM products p JOIN merchants m ON m.id=p.merchant_id JOIN users u ON u.id=m.owner_user_id WHERE u.username='merchant_a' AND p.status='ONLINE' LIMIT 1").get() as {id:string};
  const response=await call('/checkout',buyer,{addressId:address.id,items:[{productId:product.id,quantity:1}]},key);assert.equal(response.status,201);
  const group=await response.json() as {checkoutGroupId:string;orderIds:string[]};
  assert.equal((await call(`/checkout/${group.checkoutGroupId}/pay`,buyer,{method:'DEMO_BALANCE'},key+'-pay')).status,200);
  return group.orderIds[0];
}

test('商家逐步更新物流时在发货节点提醒，准备和运输节点不重复提醒',async()=>{
  const id=await paidOrder('notice-advance');
  assert.equal((await call(`/merchant/orders/${id}/advance`,merchant,{})).status,200);
  assert.equal((await notices(buyer,id)).length,2);
  assert.equal((await call(`/merchant/orders/${id}/advance`,merchant,{})).status,200);
  assert.equal((await notices(buyer,id)).length,3);assert.equal((await notices(merchant,id)).length,3);
  assert.equal((await call(`/merchant/orders/${id}/advance`,merchant,{})).status,200);
  assert.equal((await notices(buyer,id)).length,3);
});

test('网页管理端代处理发货与完成时同样通知买家和商家，旧版本操作不重复',async()=>{
  const response=await fetch(base+'/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'admin',password:'huimeng123',role:'ADMIN'})});assert.equal(response.status,200);
  const admin=(await response.json() as {token:string}).token,id=await paidOrder('notice-admin');
  const manage=async(action:string,version:number)=>fetch(base+`/admin/orders/${id}/manage`,{method:'PUT',headers:{Authorization:`Bearer ${admin}`,'Content-Type':'application/json'},body:JSON.stringify({action,note:'订单消息回归测试',version})});
  const version=()=> (db.prepare('SELECT version FROM orders WHERE id=?').get(id) as {version:number}).version;
  const previous=version();assert.equal((await manage('ship',previous)).status,200);
  assert.equal((await notices(buyer,id)).length,3);assert.equal((await notices(merchant,id)).length,3);
  assert.equal((await manage('ship',previous)).status,409);assert.equal((await notices(buyer,id)).length,3);
  assert.equal((await manage('transit',version())).status,200);assert.equal((await manage('deliver',version())).status,200);
  assert.equal((await manage('complete',version())).status,200);
  assert.equal((await notices(buyer,id)).length,4);assert.equal((await notices(merchant,id)).length,4);
});

test('订单提醒通过当前用户 SSE 订阅即时推送',async()=>{
  const abort=new AbortController();
  const stream=await fetch(base+'/me/events',{headers:{Authorization:`Bearer ${buyer}`},signal:abort.signal});
  assert.equal(stream.status,200);assert.ok(stream.body);
  let count=0;
  const reading=(async()=>{
    let pending='';
    for await(const chunk of stream.body!){
      pending+=Buffer.from(chunk).toString('utf8');let end:number;
      while((end=pending.indexOf('\n\n'))>=0){
        const block=pending.slice(0,end);pending=pending.slice(end+2);
        if(block.startsWith('event: update')&&JSON.parse(block.split('data: ')[1]).resource==='notices')count++;
      }
    }
  })().catch(error=>{if(!abort.signal.aborted)throw error;});
  const expectCount=async(expected:number)=>{for(let attempt=0;attempt<30&&count<expected;attempt++)await new Promise(resolve=>setTimeout(resolve,50));assert.equal(count,expected);};
  try{
    const id=await paidOrder('notice-sse');await expectCount(2);
    assert.equal((await call(`/merchant/orders/${id}/ship`,merchant,{})).status,200);await expectCount(3);
    assert.equal((await call(`/merchant/orders/${id}/advance`,merchant,{})).status,200);
    assert.equal((await call(`/merchant/orders/${id}/advance`,merchant,{})).status,200);
    assert.equal((await call(`/me/orders/${id}/confirm`,buyer,{})).status,200);await expectCount(4);
    assert.equal((await notices(buyer,id)).length,4);
  }finally{abort.abort();await reading;}
});
