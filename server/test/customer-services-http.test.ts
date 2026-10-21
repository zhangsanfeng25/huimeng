import test,{after} from 'node:test';import assert from 'node:assert/strict';import {mkdtempSync} from 'node:fs';import {tmpdir} from 'node:os';import {join} from 'node:path';import express from 'express';
const dir=mkdtempSync(join(tmpdir(),'huimeng-service-http-'));process.env.DATABASE_PATH=join(dir,'db');process.env.UPLOAD_DIR=join(dir,'uploads');
const {db}=await import('../src/core/db.js');await import('../scripts/migrate.js');await import('../scripts/seed.js');
const {authRouter}=await import('../src/modules/auth.js');const {customerServicesRouter}=await import('../src/modules/customer-services.js');const {shopRouter}=await import('../src/modules/shop.js');
const app=express();app.use(express.json());app.use('/api/v1/auth',authRouter);app.use('/api/v1',shopRouter);app.use('/api/v1',customerServicesRouter);
const server=app.listen(0,'127.0.0.1');await new Promise<void>(resolve=>server.once('listening',resolve));const base=`http://127.0.0.1:${(server.address() as import('node:net').AddressInfo).port}/api/v1/`;
after(()=>{server.closeAllConnections();server.close();db.close();});
async function req(path:string,token='',body?:unknown,role?:string){const r=await fetch(base+path,{method:body?'POST':'GET',headers:{'Content-Type':'application/json',...(token?{Authorization:`Bearer ${token}`}:{ }),...(role?{'X-Service-Role':role}:{})},...(body?{body:JSON.stringify(body)}:{})});return {status:r.status,data:await r.json()};}
async function login(username:string,role='USER'){return (await req('auth/login','',{username,password:username==='admin'?'huimeng123':'demo12345',role})).data.token as string;}
test('HTTP roles, private record ownership and no false successful mutations',async()=>{
 const buyer=await login('xingxing'),other=await login('xiaoxiong'),merchant=await login('merchant_a','MERCHANT'),admin=await login('admin','ADMIN');
 assert.equal((await req('service/feedback')).status,401);
 const created=await req('service/feedback',buyer,{title:'HTTP验收',type:'功能问题',content:'验证完整接口',clientId:'http-feedback'});assert.equal(created.status,201);const fid=created.data.data.id;
 assert.equal((await req(`service/feedback/${fid}`,other)).status,403);
 assert.equal((await req(`service/admin/feedback/${fid}`,buyer)).status,403);
 assert.equal((await req(`service/admin/feedback/${fid}`,admin,{action:'reply',content:'管理员已回复',clientId:'http-reply'})).status,200);
 assert.equal((await req(`service/feedback/${fid}`,buyer)).data.data.replies[0].content,'管理员已回复');
 assert.equal((await req('service/conversations',buyer,{merchantId:'M20001'},'MERCHANT')).status,403);
 const cv=await req('service/conversations',buyer,{merchantId:'M20001'});assert.equal(cv.status,201);const cid=cv.data.data.id;
 assert.equal((await req(`service/conversations/${cid}`,merchant,undefined,'MERCHANT')).status,200);
 assert.equal((await req(`service/conversations/${cid}`,merchant,undefined,'USER')).status,403);
 assert.equal((await req(`service/conversations/${cid}`,buyer,{action:'send',content:'文字消息',clientId:'http-msg'})).status,200);
 const messages=(await req(`service/conversations/${cid}`,merchant,undefined,'MERCHANT')).data.data.messages;assert.equal(messages.length,1);
 const outsider=(await req('service/conversations',other,{merchantId:'M20001'})).data.data.id;assert.notEqual(outsider,cid);assert.equal((await req(`service/conversations/${cid}`,other)).status,403);
 assert.equal((await req('service/order-after-sale/DEMO_ORDER_SHIPPED',other)).status,403);
 assert.equal((await req(`service/admin/feedback/${fid}`,buyer,{action:'resolve'})).status,403);
});
test('private events are scoped to participants and never include message bodies',async()=>{
 const buyer=await login('xingxing'),other=await login('xiaoxiong');
 const cid=(await req('service/conversations',buyer,{merchantId:'M20001'})).data.data.id;
 const aAbort=new AbortController(),bAbort=new AbortController();
 const a=await fetch(base+'service/events',{headers:{Authorization:`Bearer ${buyer}`},signal:aAbort.signal});const b=await fetch(base+'service/events',{headers:{Authorization:`Bearer ${other}`},signal:bAbort.signal});
 const ar=a.body!.getReader(),br=b.body!.getReader();try{
  await ar.read();await br.read();
  await req(`service/conversations/${cid}`,buyer,{action:'send',content:'绝不能广播正文',clientId:'http-private-event'});
  const event=new TextDecoder().decode((await ar.read()).value);assert.match(event,/conversations/);assert.doesNotMatch(event,/绝不能|正文|conversation_id/);
  const foreign=await Promise.race([br.read().then(()=>true),new Promise<boolean>(r=>setTimeout(()=>r(false),120))]);assert.equal(foreign,false);
 }finally{aAbort.abort();bAbort.abort();}
});
