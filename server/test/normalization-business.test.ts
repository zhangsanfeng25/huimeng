import test from 'node:test';
import assert from 'node:assert/strict';
import {fork} from 'node:child_process';
import {execFileSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {join} from 'node:path';
import Database from 'better-sqlite3';
import {hashPassword} from '../src/core/security.js';
import {createNormalizationFixture} from './helpers/normalization-fixture.js';
import {buildNormalizedCopy} from '../src/normalization/build-target.js';
import {captureBusinessBaseline} from '../src/normalization/baseline.js';
import {compareBusinessBaselines} from '../src/normalization/compare.js';
import {inspectNormalization} from '../src/normalization/preflight.js';

async function start(){
 const fixture=await createNormalizationFixture({through:'011',case:'standard'}),target=join(fixture.uploadDir,'..','target.db');
 const source=new Database(fixture.databasePath);source.prepare('UPDATE users SET password_hash=?').run(hashPassword('fixture-password'));source.close();
 await buildNormalizedCopy({sourcePath:fixture.databasePath,targetPath:target,manifestPath:join(fixture.uploadDir,'..','manifest.json'),uploadDir:fixture.uploadDir});
 const child=fork(fileURLToPath(new URL('./helpers/normalization-http-worker.ts',import.meta.url)),[],{execArgv:['--import','tsx'],env:{...process.env,DATABASE_PATH:target,UPLOAD_DIR:fixture.uploadDir,DEMO_MODE:'true'},stdio:['ignore','pipe','pipe','ipc'],windowsHide:true});
 let errors='';child.stderr?.on('data',d=>errors+=d.toString().slice(0,2000));
 const port=await new Promise<number>((resolve,reject)=>{const timer=setTimeout(()=>{child.kill();reject(Error('测试服务启动超时'));},15000);child.once('message',(m:any)=>{clearTimeout(timer);resolve(m.port);});child.once('exit',()=>{clearTimeout(timer);reject(Error(errors||'测试服务提前退出'));});child.once('error',reject);});
 const base='http://127.0.0.1:'+port;
 const call=(path:string,method='GET',body?:unknown,token?:string,key?:string)=>fetch(base+path,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{}),...(key?{'Idempotency-Key':key}:{})},body:body===undefined?undefined:JSON.stringify(body)});
 const login=async(username:string,role:string)=>{const response=await call('/auth/login','POST',{username,password:'fixture-password',role});assert.equal(response.status,200);return (await response.json()).token as string;};
 const user=await login('U1','USER'),merchant=await login('MU1','MERCHANT'),admin=await login('A1','ADMIN');
 return {fixture,target,call,login,user,merchant,admin,async close(){await new Promise<void>(r=>{child.once('exit',()=>r());child.send('close');});await fixture.dispose();}};
}
test('normalized_http_keeps_catalog_order_details_and_role_boundaries',async()=>{
 const h=await start();try{
  const products=await h.call('/catalog/products');assert.equal(products.status,200,await products.clone().text());const data=(await products.json()).data;assert.equal(data.length,2);
  const buyer=await h.call('/me/orders/O1','GET',undefined,h.user);assert.equal(buyer.status,200,await buyer.clone().text());assert.equal((await buyer.json()).data.items[0].subtotalCents,6000);
  const seller=await h.call('/merchant/orders/O1','GET',undefined,h.merchant);assert.equal(seller.status,200,await seller.clone().text());assert.equal((await seller.json()).data.buyer,'测试U1');
  assert.equal((await h.call('/merchant/orders/O2','GET',undefined,h.merchant)).status,404);
  assert.equal((await h.call('/orders/O1/details','GET',undefined,h.user)).status,403);
  const admin=await h.call('/orders/O1/details','GET',undefined,h.admin);assert.equal(admin.status,200,await admin.clone().text());assert.equal((await admin.json()).data.address.detail,'成交时地址');
 }finally{await h.close();}
});

async function checkout(h:Awaited<ReturnType<typeof start>>,key:string){
 const address=await h.call('/me/addresses','POST',{recipient:'测试收件人',phone:'13800000001',region:'测试地区',detail:'购买时地址',isDefault:true},h.user);
 assert.equal(address.status,201,await address.clone().text());const addressId=(await address.json()).id;
 const result=await h.call('/checkout','POST',{addressId,items:[{productId:'P1',quantity:2},{productId:'P2',quantity:1}]},h.user,key);
 assert.equal(result.status,201,await result.clone().text());return await result.json();
}

test('normalized_checkout_splits_merchants_and_pays_once_with_historical_address',async()=>{
 const h=await start();try{
  const created=await checkout(h,'normalized-pay');assert.equal(created.orderIds.length,2);assert.equal(created.totalCents,10000);
  const paid=await h.call('/checkout/'+created.checkoutGroupId+'/pay','POST',{method:'DEMO_BALANCE'},h.user,'payment-1');assert.equal(paid.status,200,await paid.text());
  const repeated=await h.call('/checkout/'+created.checkoutGroupId+'/pay','POST',{},h.user,'payment-1');assert.equal(repeated.status,200);assert.equal((await repeated.json()).reused,true);
  const reader=new Database(h.target,{readonly:true});try{
   assert.equal((reader.prepare('SELECT COUNT(*) n FROM payments WHERE checkout_group_id=?').get(created.checkoutGroupId) as any).n,1);
   for(const id of created.orderIds){const order=(await (await h.call('/me/orders/'+id,'GET',undefined,h.user)).json()).data;assert.equal(order.status,'PAID');assert.equal(JSON.parse(order.address_snapshot_json).detail,'购买时地址');assert.equal(order.items.reduce((n:number,i:any)=>n+i.subtotalCents,0),order.total_cents);}
   assert.deepEqual(reader.pragma('foreign_key_check'),[]);
  }finally{reader.close();}
 }finally{await h.close();}
});

test('normalized_cancellation_releases_whole_checkout_once_and_enforces_owner',async()=>{
 const h=await start();try{
  const created=await checkout(h,'normalized-cancel'),other=await h.login('U2','USER');
  assert.equal((await h.call('/me/orders/'+created.orderIds[0]+'/cancel','POST',{},other)).status,404);
  const cancel=await h.call('/me/orders/'+created.orderIds[0]+'/cancel','POST',{},h.user);assert.equal(cancel.status,200,await cancel.text());
  assert.equal((await h.call('/me/orders/'+created.orderIds[0]+'/cancel','POST',{},h.user)).status,200);
  const reader=new Database(h.target,{readonly:true});try{
   assert.equal((reader.prepare("SELECT COUNT(*) n FROM compat_orders WHERE checkout_group_id=? AND status='CANCELLED'").get(created.checkoutGroupId) as any).n,2);
   assert.equal((reader.prepare("SELECT COUNT(*) n FROM stock_movements WHERE type='RELEASE'").get() as any).n,2);
   assert.equal((reader.prepare('SELECT SUM(stock_reserved) n FROM products').get() as any).n,0);
  }finally{reader.close();}
 }finally{await h.close();}
});
test('normalized_merchant_advances_and_buyer_confirms_recorded_order',async()=>{
 const h=await start();try{
  for(let i=0;i<4;i++){const response=await h.call('/merchant/orders/O1/advance','POST',{},h.merchant);assert.equal(response.status,200,await response.text());}
  const confirmed=await h.call('/me/orders/O1/confirm','POST',{},h.user);assert.equal(confirmed.status,200,await confirmed.text());
  const row=(await (await h.call('/me/orders/O1','GET',undefined,h.user)).json()).data;assert.equal(row.status,'COMPLETED');assert.equal(row.logistics_status,'COMPLETED');assert.equal(row.events.at(-1).action,'CONFIRM');
 }finally{await h.close();}
});

test('normalized_partial_refunds_keep_idempotency_ownership_and_remaining_amount',async()=>{
 const h=await start();try{
  const request={orderId:'O1',type:'REFUND_ONLY',reason:'部分金额补偿',clientId:'partial-case-1',items:[{orderItemId:'I1',quantity:1,amountCents:1000}]};
  const created=await h.call('/service/after-sales','POST',request,h.user);assert.equal(created.status,201,await created.clone().text());const id=(await created.json()).data.id;
  const replay=await h.call('/service/after-sales','POST',request,h.user);assert.equal(replay.status,201);assert.equal((await replay.json()).reused,true);
  const other=await h.login('U2','USER');assert.equal((await h.call('/service/after-sales/'+id,'GET',undefined,other)).status,403);
  const action=async(token:string,payload:unknown)=>{const r=await h.call('/service/after-sales/'+id,'POST',payload,token);assert.equal(r.status,200,await r.clone().text());return r.json();};
  await action(h.merchant,{action:'merchant-review',approve:true,note:'同意部分退款'});
  await action(h.admin,{action:'approve',note:'复核通过'});const refunds=await Promise.all([action(h.admin,{action:'refund'}),action(h.admin,{action:'refund'}),action(h.admin,{action:'refund'})]);assert.equal(refunds.filter(r=>r.reused).length,2);
  const detail=await h.call('/service/after-sales/'+id,'GET',undefined,h.user);assert.equal(detail.status,200,await detail.clone().text());const sale=(await detail.json()).data;assert.equal(sale.amount_cents,1000);assert.equal(sale.refund.order_id,'O1');assert.equal(sale.can_apply_again,true);
  const context=await h.call('/service/order-after-sale/O1','GET',undefined,h.user);assert.equal(context.status,200,await context.clone().text());const data=(await context.json()).data;assert.equal(data.order.refunded_cents,1000);assert.equal(data.items[0].refundable_cents,5000);
  const reader=new Database(h.target,{readonly:true});try{assert.equal((reader.prepare('SELECT COUNT(*) n FROM refunds WHERE after_sale_id=?').get(id) as any).n,1);assert.deepEqual(reader.pragma('foreign_key_check'),[]);}finally{reader.close();}
  const remainder=await h.call('/service/after-sales','POST',{...request,clientId:'partial-case-remainder',items:[{orderItemId:'I1',quantity:2,amountCents:5000}]},h.user);assert.equal(remainder.status,201,await remainder.clone().text());const remainingId=(await remainder.json()).data.id;
  for(const [token,payload] of [[h.merchant,{action:'merchant-review',approve:true,note:'同意退还剩余额度'}],[h.admin,{action:'approve',note:'复核累计全额退款'}],[h.admin,{action:'refund'}]] as const){const r=await h.call('/service/after-sales/'+remainingId,'POST',payload,token);assert.equal(r.status,200,await r.clone().text());}
  const finished=(await (await h.call('/service/order-after-sale/O1','GET',undefined,h.user)).json()).data;assert.equal(finished.order.refunded_cents,6000);assert.equal(finished.items[0].refundable_cents,0);assert.equal(finished.items[0].returnable_quantity,0);
  const complete=(await (await h.call('/service/after-sales/'+remainingId,'GET',undefined,h.user)).json()).data;assert.equal(complete.can_apply_again,false);
  assert.equal((await h.call('/merchant/orders/O1/ship','POST',{},h.merchant)).status,409,'累计全额退款后不能发货');
  const secondReader=new Database(h.target,{readonly:true});try{assert.equal((secondReader.prepare("SELECT SUM(available_delta) n FROM stock_movements WHERE order_id='O1' AND type='REFUND_RETURN'").get() as any).n,2);assert.deepEqual(secondReader.pragma('foreign_key_check'),[]);}finally{secondReader.close();}
 }finally{await h.close();}
});

test('normalized_product_publication_requires_admin_and_keeps_book_contents',async()=>{
 const h=await start();try{
  const input={name:'迁移测试绘本',summary:'测试图书简介',categoryId:'C1',ageRange:'3-6岁',priceCents:2500,stock:5,tableOfContents:['序章','故事','故事'],detailImages:[]};
  const created=await h.call('/merchant/products','POST',input,h.merchant);assert.equal(created.status,201,await created.clone().text());const product=await created.json();
  assert.equal((await h.call('/catalog/products/'+product.productId)).status,404);
  const saved=await h.call('/merchant/products/'+product.productId,'PUT',{...input,summary:'修改后的简介'},h.merchant);assert.equal(saved.status,200,await saved.text());
  const submit=await h.call('/merchant/products/'+product.productId+'/submit','POST',{},h.merchant);assert.equal(submit.status,200,await submit.text());
  assert.equal((await h.call('/catalog/products/'+product.productId)).status,404);
  assert.equal((await h.call('/reviews/'+product.revisionId,'PUT',{status:'已通过'},h.merchant)).status,403);
  const approve=await h.call('/reviews/'+product.revisionId,'PUT',{status:'已通过',note:'审核通过'},h.admin);assert.equal(approve.status,200,await approve.text());
  const visible=await h.call('/catalog/products/'+product.productId);assert.equal(visible.status,200,await visible.clone().text());assert.deepEqual((await visible.json()).data.tableOfContents,input.tableOfContents);
  const edited=await h.call('/merchant/products/'+product.productId,'PUT',{...input,name:'已发布商品再编辑'},h.merchant);assert.equal(edited.status,200,await edited.clone().text());const revision=(await edited.json()).revisionId;assert.notEqual(revision,product.revisionId);
  assert.equal((await h.call('/catalog/products/'+product.productId)).status,404);
  assert.equal((await h.call('/merchant/products/'+product.productId+'/submit','POST',{},h.merchant)).status,200);
  assert.equal((await h.call('/reviews/'+revision,'PUT',{status:'已通过',note:'复核修改'},h.admin)).status,200);
  assert.equal((await (await h.call('/catalog/products/'+product.productId)).json()).data.name,'已发布商品再编辑');
 }finally{await h.close();}
});

test('normalized_last_stock_unit_cannot_be_oversold_and_same_checkout_is_reused',async()=>{
 const h=await start();try{
  const writer=new Database(h.target);writer.prepare('UPDATE products SET stock_available=1 WHERE id=?').run('P1');writer.close();
  const other=await h.login('U2','USER');const addresses=[];
  for(const token of [h.user,other]){const response=await h.call('/me/addresses','POST',{recipient:'测试',phone:'13800000001',region:'测试地区',detail:'测试地址'},token);assert.equal(response.status,201);addresses.push((await response.json()).id);}
  const requests=[h.user,other].map((token,i)=>h.call('/checkout','POST',{addressId:addresses[i],items:[{productId:'P1',quantity:1}]},token,'last-unit-'+i));
  const responses=await Promise.all(requests);assert.equal(responses.filter(r=>r.status===201).length,1);assert.ok(responses.some(r=>r.status>=400));
  const winner=responses.findIndex(r=>r.status===201),token=[h.user,other][winner],group=(await responses[winner].json()).checkoutGroupId;
  const replay=await Promise.all([1,2,3].map(()=>h.call('/checkout','POST',{addressId:addresses[winner],items:[{productId:'P1',quantity:1}]},token,'last-unit-'+winner)));
  for(const response of replay){assert.equal(response.status,200);assert.equal((await response.json()).checkoutGroupId,group);}
  const reader=new Database(h.target,{readonly:true});try{const stock=reader.prepare('SELECT stock_available,stock_reserved FROM products WHERE id=?').get('P1') as any;assert.deepEqual(stock,{stock_available:0,stock_reserved:1});assert.equal((reader.prepare("SELECT COUNT(*) n FROM checkout_groups WHERE idempotency_key LIKE 'last-unit-%'").get() as any).n,1);}finally{reader.close();}
 }finally{await h.close();}
});

test('normalized_merchant_ship_and_return_refund_preserve_stock_and_amount',async()=>{
 const h=await start();try{
  const ship=await h.call('/merchant/orders/O1/ship','POST',{},h.merchant);assert.equal(ship.status,200,await ship.text());
  const create=await h.call('/service/after-sales','POST',{orderId:'O1',type:'RETURN_REFUND',reason:'退货',clientId:'return-case',items:[{orderItemId:'I1',quantity:1,amountCents:3000}]},h.user);assert.equal(create.status,201,await create.clone().text());const id=(await create.json()).data.id;
  for(const [token,payload] of [[h.merchant,{action:'merchant-review',approve:true,note:'同意',returnAddress:'退货地址'}],[h.admin,{action:'approve',note:'审核通过'}],[h.user,{action:'return',carrier:'演示快递',trackingNo:'DEMO-RETURN'}],[h.merchant,{action:'receive'}],[h.admin,{action:'refund'}]] as const){const r=await h.call('/service/after-sales/'+id,'POST',payload,token);assert.equal(r.status,200,await r.text());}
  const reader=new Database(h.target,{readonly:true});try{assert.equal((reader.prepare("SELECT SUM(available_delta) n FROM stock_movements WHERE type='REFUND_RETURN' AND order_id=?").get('O1') as any).n,1);assert.equal((reader.prepare('SELECT amount_cents FROM refunds WHERE after_sale_id=?').get(id) as any).amount_cents,3000);assert.deepEqual(reader.pragma('foreign_key_check'),[]);}finally{reader.close();}
 }finally{await h.close();}
});

test('normalized_community_posts_comments_likes_and_completed_order_ratings',async()=>{
 const h=await start();try{
  const created=await h.call('/community/posts','POST',{title:'测试分享',content:'迁移后仍可发布',images:[]},h.merchant);assert.equal(created.status,201,await created.clone().text());const id=(await created.json()).id;
  assert.equal((await h.call('/community/posts/'+id+'/like','PUT',{},h.user)).status,200);
  assert.equal((await h.call('/community/posts/'+id+'/comments','POST',{content:'测试评论'},h.user)).status,201);
  const detail=await h.call('/community/posts/'+id,'GET',undefined,h.user);assert.equal(detail.status,200,await detail.clone().text());const post=(await detail.json()).data;assert.equal(post.likeCount,1);assert.equal(post.comments.length,1);assert.equal(post.images_json,'[]');
  for(let i=0;i<4;i++)assert.equal((await h.call('/merchant/orders/O1/advance','POST',{},h.merchant)).status,200);
  assert.equal((await h.call('/me/orders/O1/confirm','POST',{},h.user)).status,200);
  const rated=await h.call('/me/ratings','POST',{orderItemId:'I1',stars:5,content:'迁移后评价'},h.user);assert.equal(rated.status,201,await rated.text());
  const ratings=await h.call('/catalog/products/P1/ratings');assert.equal(ratings.status,200);assert.equal((await ratings.json()).data[0].content,'迁移后评价');
 }finally{await h.close();}
});

test('normalized_admin_order_management_preserves_version_conflict',async()=>{
 const h=await start();try{
  const payload={action:'ship',note:'管理员模拟发货',version:1};
  const result=await h.call('/orders/O1/manage','PUT',payload,h.admin);assert.equal(result.status,200,await result.text());
  assert.equal((await h.call('/orders/O1/manage','PUT',payload,h.admin)).status,409);
  const detail=(await (await h.call('/orders/O1/details','GET',undefined,h.admin)).json()).data;assert.equal(detail.status,'SHIPPED');assert.equal(detail.version,2);
 }finally{await h.close();}
});

test('normalized_admin_can_maintain_product_and_community_content',async()=>{
 const h=await start();try{
  const product=await h.call('/products/P1/content','PUT',{revisionId:'R1',name:'维护后的商品',summary:'完整简介',ageRange:'3-6岁',coverUrl:'',images:[],tableOfContents:['重复章节','重复章节']},h.admin);assert.equal(product.status,200,await product.text());
  const catalog=(await (await h.call('/catalog/products/P1')).json()).data;assert.equal(catalog.name,'维护后的商品');assert.deepEqual(catalog.tableOfContents,['重复章节','重复章节']);
  const created=await h.call('/community','POST',{title:'后台分享',content:'后台发布的正文',images:[],status:'公开'},h.admin);assert.equal(created.status,201,await created.clone().text());const id=(await created.json()).data.id;
  const edited=await h.call('/community/'+id+'/details','PUT',{title:'后台修改',content:'编辑后的正文',images:[],status:'公开'},h.admin);assert.equal(edited.status,200,await edited.clone().text());assert.equal((await edited.json()).data.content,'编辑后的正文');
 }finally{await h.close();}
});

test('normalized_seed_is_repeatable_without_resetting_existing_business_data',async()=>{
 const h=await start();try{
  const run=()=>execFileSync(process.execPath,['--import','tsx','scripts/seed.ts'],{env:{...process.env,DATABASE_PATH:h.target,UPLOAD_DIR:h.fixture.uploadDir},windowsHide:true,stdio:'pipe'});
  run();let reader=new Database(h.target,{readonly:true});let baseline;try{
   assert.equal((reader.prepare('SELECT nickname FROM users WHERE id=?').get('U1') as any).nickname,'测试U1');
   assert.equal((reader.prepare('SELECT total_cents FROM orders WHERE id=?').get('O1') as any).total_cents,6000);
   assert.equal((reader.prepare('SELECT detail FROM order_address_snapshots WHERE order_id=?').get('O1') as any).detail,'成交时地址');
   assert.deepEqual(reader.pragma('foreign_key_check'),[]);baseline=captureBusinessBaseline(reader,'normalized');
  }finally{reader.close();}
  run();reader=new Database(h.target,{readonly:true});try{assert.deepEqual(compareBusinessBaselines(baseline,captureBusinessBaseline(reader,'normalized')),{equal:true,differences:[]});}finally{reader.close();}
 }finally{await h.close();}
});

test('normalized_notices_keep_unknown_time_reads_and_personal_hide',async()=>{
 const h=await start();try{
  const list=async(token:string)=>(await (await h.call('/me/notifications','GET',undefined,token)).json()).data;
  assert.equal((await list(h.user)).find((n:any)=>n.id==='N1').is_read,1);
  const created=await h.call('/notices','POST',{title:'广播测试',content:'通知正文',audience:'全部用户'},h.admin);assert.equal(created.status,201,await created.clone().text());const id=(await created.json()).data.id;
  assert.equal((await list(h.user)).find((n:any)=>n.id===id).is_read,0);
  assert.equal((await h.call('/me/notifications/'+id+'/read','POST',{},h.user)).status,200);
  assert.equal((await list(h.user)).find((n:any)=>n.id===id).is_read,1);
  const other=await h.login('U2','USER');assert.equal((await list(other)).find((n:any)=>n.id===id).is_read,0);
  assert.equal((await h.call('/me/notifications/'+id,'DELETE',undefined,h.user)).status,200);
  assert.ok(!(await list(h.user)).some((n:any)=>n.id===id));assert.ok((await list(other)).some((n:any)=>n.id===id));
 }finally{await h.close();}
});

test('normalized_chat_read_cursor_stays_in_its_conversation_and_feedback_is_private',async()=>{
 const h=await start();try{
  const post=async(path:string,body:unknown,token=h.user)=>{const r=await h.call(path,'POST',body,token);assert.ok(r.ok,await r.clone().text());return r.json();};
  const first=(await post('/service/conversations',{merchantId:'M1'})).data.id;
  const second=(await post('/service/conversations',{merchantId:'M2'})).data.id;
  await post('/service/conversations/'+first,{action:'send',content:'第一条咨询',clientId:'chat-1'});
  await post('/service/conversations/'+second,{action:'send',content:'另一店铺咨询',clientId:'chat-2'});
  await post('/service/conversations/'+first,{action:'send',content:'补充咨询',clientId:'chat-3'});
  const detail=async(id:string,token=h.user)=>(await (await h.call('/service/conversations/'+id,'GET',undefined,token)).json()).data;
  const messages=(await detail(first)).messages,foreign=(await detail(second)).messages[0].id;
  await post('/service/conversations/'+first,{action:'read',lastMessageId:foreign},h.merchant);
  const reader=new Database(h.target,{readonly:true});try{
   assert.equal((reader.prepare("SELECT last_message_id FROM conversation_reads WHERE conversation_id=? AND reader_role='MERCHANT'").get(first) as any).last_message_id,messages[0].id,'游标必须落在本会话消息，不能保存其他会话的消息编号');
   assert.equal(inspectNormalization(reader).canProceed,true);
  }finally{reader.close();}
  assert.equal((await (await h.call('/service/conversations','GET',undefined,h.merchant)).json()).data[0].unread,1);
  await post('/service/conversations/'+first,{action:'read'},h.merchant);
  await post('/service/conversations/'+first,{action:'send',content:'商家回复',clientId:'chat-reply'},h.merchant);
  assert.equal((await detail(first)).messages.at(-1).content,'商家回复');
  const other=await h.login('U2','USER');assert.equal((await h.call('/service/conversations/'+first,'GET',undefined,other)).status,403);
  const feedback=(await post('/service/feedback',{title:'迁移验收',type:'功能问题',content:'测试反馈',clientId:'feedback-roundtrip'})).data.id;
  assert.equal((await h.call('/service/feedback/'+feedback,'GET',undefined,other)).status,403);
  await post('/service/admin/feedback/'+feedback,{action:'reply',content:'平台回复',clientId:'feedback-admin'},h.admin);
  await post('/service/admin/feedback/'+feedback,{action:'resolve'},h.admin);
  const result=await h.call('/service/feedback/'+feedback,'GET',undefined,h.user);assert.equal(result.status,200);const data=(await result.json()).data;assert.equal(data.status,'RESOLVED');assert.equal(data.replies.at(-1).content,'平台回复');
 }finally{await h.close();}
});

test('merchant_stock_only_update_keeps_publication_and_reservations',async()=>{
 const h=await start();try{
  const reader=new Database(h.target);
  reader.prepare("UPDATE products SET stock_reserved=2 WHERE id='P1'").run();
  const before=reader.prepare("SELECT * FROM products WHERE id='P1'").get() as any;
  const versions=reader.prepare("SELECT COUNT(*) n FROM product_revisions WHERE product_id='P1'").get() as any;
  const r=await h.call('/merchant/products/P1/stock','POST',{stock:18,expectedStock:10},h.merchant);if(r.status!==200)reader.close();assert.equal(r.status,200,await r.clone().text());
  const after=reader.prepare("SELECT * FROM products WHERE id='P1'").get() as any;
  assert.equal(after.stock_available,18);assert.equal(after.stock_reserved,2);assert.equal(after.status,before.status);assert.equal(after.published_revision_id,before.published_revision_id);
  assert.equal((reader.prepare("SELECT COUNT(*) n FROM product_revisions WHERE product_id='P1'").get() as any).n,versions.n);
  assert.equal((reader.prepare("SELECT available_delta FROM stock_movements WHERE product_id='P1' AND type='ADJUST'").get() as any).available_delta,8);
  reader.close();
  assert.equal((await h.call('/merchant/products/P1/stock','POST',{stock:20,expectedStock:10},h.merchant)).status,409);
  assert.equal((await h.call('/merchant/products/P2/stock','POST',{stock:20,expectedStock:10},h.merchant)).status,404);
  assert.equal((await h.call('/merchant/products/P1/stock','POST',{stock:20,expectedStock:18},h.user)).status,403);
  for(const stock of [-1,1.5,2147483648,'12'])assert.equal((await h.call('/merchant/products/P1/stock','POST',{stock,expectedStock:18},h.merchant)).status,400);
 }finally{await h.close();}
});

