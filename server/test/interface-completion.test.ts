import test, { after } from 'node:test';
import assert from 'node:assert/strict';
import express from 'express';
import { mkdtempSync, rmSync, mkdirSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
const fixture=mkdtempSync(join(tmpdir(),'huimeng-interfaces-'));
process.env.DATABASE_PATH=join(fixture,'test.db');process.env.UPLOAD_DIR=join(fixture,'uploads');mkdirSync(process.env.UPLOAD_DIR);
const {db}=await import('../src/core/db.js');await import('../scripts/migrate.js');await import('../scripts/seed.js');
const {authRouter}=await import('../src/modules/auth.js');const {shopRouter}=await import('../src/modules/shop.js');const {adminRouter}=await import('../src/modules/admin.js');const {communityRouter}=await import('../src/modules/community.js');
const app=express();app.use(express.json());app.use('/auth',authRouter);app.use(shopRouter);app.use(communityRouter);app.use(adminRouter);
app.use((error:Error,_req:express.Request,res:express.Response,_next:express.NextFunction)=>res.status(400).json({message:error.message}));
const server=app.listen(0,'127.0.0.1');await new Promise<void>(r=>server.once('listening',r));const addr=server.address();assert.ok(addr&&typeof addr!=='string');const base=`http://127.0.0.1:${addr.port}`;
after(async()=>{server.closeAllConnections();await new Promise<void>(r=>server.close(()=>r()));db.close();rmSync(fixture,{recursive:true,force:true});});
async function login(username:string,password='demo12345',role='USER'){const response=await fetch(base+'/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username,password,role})});assert.equal(response.status,200);return (await response.json()).token as string;}
const user=await login('xingxing'),other=await login('xiaoxiong'),merchant=await login('merchant_a','demo12345','MERCHANT'),admin=await login('admin','huimeng123','ADMIN');
async function call(path:string,method='GET',body?:unknown,token=user){return fetch(base+path,{method,headers:{Authorization:`Bearer ${token}`,'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body)});}
const application={shopName:'共读小屋',contactName:'陈老师',contactPhone:'13800002001',businessAddress:'辽宁省大连市阅读街20号',description:'提供儿童绘本与亲子共读服务。',acceptedTerms:true};
let applicationId='';
let approvedMerchant='';
test('申请表校验后入库、待审核无商家权限、重复提交不创建第二个店铺',async()=>{
  assert.equal((await call('/me/merchant-application','POST',{...application,contactPhone:'abc'})).status,400);
  assert.equal((await call('/me/merchant-application','POST',{...application,acceptedTerms:false})).status,400);
  const created=await call('/me/merchant-application','POST',application);assert.equal(created.status,201);applicationId=(await created.json()).data.id;
  assert.equal((await call('/me/merchant-application','POST',application)).status,409);
  const {data}=await (await call('/me/merchant-application')).json();assert.equal(data.status,'PENDING');assert.equal(data.contactName,'陈老师');
  assert.equal((await call('/merchant/products')).status,403);
  const list=await (await call('/merchants','GET',undefined,admin)).json();assert.ok(list.data.some((r:any)=>r.id===applicationId&&r.approval==='待审核'));
  const details=await (await call('/merchants/'+applicationId+'/details','GET',undefined,admin)).json();assert.equal(details.data.application.contactPhone,'13800002001');
  assert.equal((await call('/me/merchant-application','GET',undefined,other)).status,200);assert.equal((await (await call('/me/merchant-application','GET',undefined,other)).json()).data,null);
});
test('审核拒绝非法状态及无理由驳回，通过后需要以商家身份重新登录',async()=>{
  assert.equal((await call('/merchants/'+applicationId,'PUT',{approval:'伪造',status:'营业中'},admin)).status,400);
  assert.equal((await call('/merchants/'+applicationId,'PUT',{approval:'已驳回',status:'待审核'},admin)).status,400);
  assert.equal((await call('/merchants/'+applicationId,'PUT',{approval:'已通过',status:'营业中'},other)).status,403);
  assert.equal((await call('/merchants/'+applicationId,'PUT',{shop:application.shopName,approval:'已通过',status:'营业中'},admin)).status,200);
  assert.ok((await (await call('/auth/me')).json()).user.roles.includes('MERCHANT'));
  assert.equal((await call('/auth/switch-role','POST',{role:'MERCHANT'})).status,403);
  assert.equal((await call('/merchant/products')).status,403);
  assert.equal((await call('/me/merchant-application','POST',application)).status,409);
  assert.equal((await call('/auth/switch-role','POST',{role:'ADMIN'})).status,403);
  assert.equal((await call('/auth/switch-role','POST',{role:'MERCHANT'},other)).status,403);
  approvedMerchant=await login('xingxing','demo12345','MERCHANT');
  assert.equal((await call('/merchant/products','GET',undefined,approvedMerchant)).status,200);
});
test('商家停用后旧会话不能继续管理，恢复营业重新获得权限',async()=>{
  assert.equal((await call('/merchants/'+applicationId,'PUT',{approval:'已通过',status:'已停用'},admin)).status,200);
  assert.equal((await call('/merchant/products','GET',undefined,approvedMerchant)).status,401);
  assert.ok(!(await (await call('/auth/me')).json()).user.roles.includes('MERCHANT'));
  assert.equal((await call('/merchants/'+applicationId,'PUT',{approval:'已通过',status:'营业中'},admin)).status,200);
  assert.equal((await call('/merchant/products','GET',undefined,approvedMerchant)).status,401);
  approvedMerchant=await login('xingxing','demo12345','MERCHANT');
});
test('商家管理列表携带版本，旧编辑表单不能覆盖新审核结果',async()=>{
  const list=await (await call('/merchants','GET',undefined,admin)).json();const row=list.data.find((r:any)=>r.id===applicationId);assert.ok(row.updatedAt);
  assert.equal((await call('/merchants/'+applicationId,'PUT',{approval:'已驳回',status:'待审核',note:'过期表单',expectedUpdatedAt:'2000-01-01T00:00:00.000Z'},admin)).status,409);
  assert.ok((await (await call('/auth/me')).json()).user.roles.includes('MERCHANT'));
});
test('驳回后显示原因，同一申请可修正重新提交且仍无权限',async()=>{
  const created=await call('/me/merchant-application','POST',{...application,shopName:'另一家书屋'},other);assert.equal(created.status,201);const id=(await created.json()).data.id;
  assert.equal((await call('/merchants/'+id,'PUT',{approval:'已驳回',status:'待审核',note:'请补充详细经营地址'},admin)).status,200);
  assert.equal((await (await call('/me/merchant-application','GET',undefined,other)).json()).data.rejectionReason,'请补充详细经营地址');
  const resubmitted=await call('/me/merchant-application','POST',{...application,businessAddress:'辽宁省大连市阅读街21号'},other);assert.equal(resubmitted.status,201);assert.equal((await resubmitted.json()).data.id,id);
  assert.equal((await call('/merchant/products','GET',undefined,other)).status,403);
});
test('申请与审核事件发送到已连接的实时流',async()=>{
  const abort=new AbortController();const response=await fetch(base+'/me/events',{headers:{Authorization:`Bearer ${other}`},signal:abort.signal});assert.equal(response.status,200);
  const reader=response.body!.getReader();let text=new TextDecoder().decode((await reader.read()).value);assert.match(text,/event: ready/);
  const {data}=await (await call('/me/merchant-application','GET',undefined,other)).json();await call('/merchants/'+data.id,'PUT',{approval:'已通过',status:'营业中'},admin);
  const timeout=setTimeout(()=>abort.abort(),2500);try{while(!text.includes('"resource":"merchants"'))text+=new TextDecoder().decode((await reader.read()).value);assert.match(text,/event: update/);}finally{clearTimeout(timeout);abort.abort();}
});
test('商家图片只接受自己的上传，编辑文字保留封面、详情图片和音频',async()=>{
  const jpeg=Buffer.alloc(150);jpeg[0]=255;jpeg[1]=216;jpeg[148]=255;jpeg[149]=217;
  const uploaded=await fetch(base+'/community/uploads',{method:'POST',headers:{Authorization:`Bearer ${merchant}`,'Content-Type':'image/jpeg'},body:jpeg});assert.equal(uploaded.status,201);const url=(await uploaded.json()).url;
  const body={name:'图文测试绘本',summary:'保留图文的绘本',ageRange:'3–6岁',priceCents:2900,stock:10,categoryId:(db.prepare('SELECT id FROM categories LIMIT 1').get() as any).id,coverUrl:url,detailImages:[url],audioUrl:'/public/sample.mp3'};
  const created=await call('/merchant/products','POST',body,merchant);assert.equal(created.status,201);const {productId}=await created.json();
  assert.equal((await call('/merchant/products','POST',body,approvedMerchant)).status,400);
  const {coverUrl:omitCover,detailImages:omitImages,audioUrl:omitAudio,...textOnly}=body;void omitCover;void omitImages;void omitAudio;
  assert.equal((await call('/merchant/products/'+productId,'PUT',{...textOnly,summary:'仅修改文字'},merchant)).status,200);
  const {data}=await (await call('/merchant/products/'+productId,'GET',undefined,merchant)).json();assert.equal(data.coverUrl,url);assert.deepEqual(data.detailImages,[url]);assert.equal(data.audioUrl,'/public/sample.mp3');
  assert.equal((await call('/merchant/products/'+productId+'/submit','POST',{},merchant)).status,200);
  assert.equal((await call('/merchant/products/'+productId,'PUT',body,merchant)).status,409);
  assert.equal((await call('/merchant/products/'+productId,'GET',undefined,approvedMerchant)).status,404);
});
test('评价页仅分页展示公开且未删除的评价，未上架商品不能查询评价',async()=>{
  const row=db.prepare('SELECT product_id id FROM ratings LIMIT 1').get() as any;assert.ok(row);
  const response=await call('/catalog/products/'+row.id+'/ratings?page=1&pageSize=1');assert.equal(response.status,200);const result=await response.json();assert.equal(result.data.length,1);assert.ok(result.total>=1);assert.ok(result.data[0].createdAt);
  db.prepare("UPDATE ratings SET status='HIDDEN' WHERE product_id=?").run(row.id);
  const empty=await (await call('/catalog/products/'+row.id+'/ratings')).json();assert.equal(empty.total,0);assert.deepEqual(empty.data,[]);
  assert.equal((await call('/catalog/products/'+row.id+'/ratings?page=0')).status,400);
  db.prepare("UPDATE products SET status='OFFLINE' WHERE id=?").run(row.id);assert.equal((await call('/catalog/products/'+row.id+'/ratings')).status,404);
});
