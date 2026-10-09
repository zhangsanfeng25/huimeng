import test, { after } from 'node:test';
import assert from 'node:assert/strict';
import express from 'express';
import { mkdtempSync, rmSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
const fixture=mkdtempSync(join(tmpdir(),'huimeng-admin-'));
process.env.DATABASE_PATH=join(fixture,'test.db');process.env.UPLOAD_DIR=join(fixture,'uploads');
const {db}=await import('../src/core/db.js');await import('../scripts/migrate.js');await import('../scripts/seed.js');
const {authRouter}=await import('../src/modules/auth.js');const {adminRouter}=await import('../src/modules/admin.js');const {shopRouter}=await import('../src/modules/shop.js');
const app=express();app.use(express.json());app.use('/auth',authRouter);app.use(shopRouter);app.use(adminRouter);
const server=app.listen(0,'127.0.0.1');await new Promise<void>(r=>server.once('listening',r));const address=server.address();assert.ok(address&&typeof address!=='string');const base=`http://127.0.0.1:${address.port}`;
after(async()=>{await new Promise<void>(r=>server.close(()=>r()));db.close();rmSync(fixture,{recursive:true,force:true});});
async function login(username:string,password:string,role:string){return (await (await fetch(base+'/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username,password,role})})).json()).token as string;}
const admin=await login('admin','huimeng123','ADMIN'),user=await login('xingxing','demo12345','USER');
async function call(path:string,method='GET',body?:unknown,token=admin){return fetch(base+path,{method,headers:{Authorization:`Bearer ${token}`,'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body)});}

test('工作台每日新增商品及注册用户来自真实创建日期',async()=>{
  const today=new Date();const day=`${today.getFullYear()}-${String(today.getMonth()+1).padStart(2,'0')}-${String(today.getDate()).padStart(2,'0')}`;
  db.prepare("UPDATE products SET created_at=? WHERE id=(SELECT id FROM products LIMIT 1)").run(day+'T08:00:00Z');
  db.prepare("UPDATE users SET created_at=? WHERE id='U10001'").run(day+'T08:00:00Z');
  const result=await (await call('/dashboard')).json();const point=result.trend.at(-1);
  assert.equal(point.products,(db.prepare('SELECT COUNT(*) count FROM products WHERE substr(created_at,1,10)=?').get(day) as any).count);
  assert.equal(point.users,(db.prepare("SELECT COUNT(*) count FROM users WHERE substr(created_at,1,10)=? AND EXISTS(SELECT 1 FROM user_roles r WHERE r.user_id=users.id AND role='USER')").get(day) as any).count);
  assert.equal(result.trend.length,7);assert.equal((await (await call('/dashboard?days=30')).json()).trend.length,30);
});
test('商家关联内容包括收到的帖子评论及商品评价',async()=>{const post=db.prepare("SELECT id FROM community_posts WHERE author_user_id='MUSER01' LIMIT 1").get() as any;assert.ok(post);db.prepare("INSERT INTO comments(id,post_id,user_id,content,status,created_at) VALUES('received-comment',?,'U10001','商家收到的评论','PUBLIC',?)").run(post.id,new Date().toISOString());const merchant=await (await call('/merchants/M20001/details')).json();assert.ok(merchant.data.commentList.some((v:any)=>v.id==='received-comment'));const rating=db.prepare('SELECT r.id,p.merchant_id FROM ratings r JOIN products p ON p.id=r.product_id LIMIT 1').get() as any;assert.ok(rating);const received=await (await call('/merchants/'+rating.merchant_id+'/details')).json();assert.ok(received.data.ratingList.some((v:any)=>v.id===rating.id));});
test('订单处理遵循状态迁移、版本检查并写入事件',async()=>{const row=db.prepare('SELECT id,version FROM orders LIMIT 1').get() as any;assert.ok(row);db.prepare("UPDATE orders SET status='PAID',logistics_status='PREPARING' WHERE id=?").run(row.id);assert.equal((await call('/orders/'+row.id,'PUT',{status:'已完成'})).status,400);assert.equal((await call('/orders/'+row.id+'/manage','PUT',{action:'ship',note:'管理员处理发货',version:row.version})).status,200);assert.equal((await call('/orders/'+row.id+'/manage','PUT',{action:'transit',note:'重复旧版本',version:row.version})).status,409);assert.ok(db.prepare("SELECT 1 FROM order_events WHERE order_id=? AND action='ship'").get(row.id));assert.equal((await call('/orders/'+row.id+'/manage','PUT',{action:'cancel',note:'不能取消已付款',version:row.version+1})).status,409);});
test('账户详情返回本人关联列表，不混入其他账户内容',async()=>{const {data}=await (await call('/users/U10001/details')).json();assert.ok(Array.isArray(data.orderList));assert.ok(Array.isArray(data.postList));assert.ok(Array.isArray(data.commentList));assert.ok(data.postList.every((v:any)=>v.authorId==='U10001'));assert.ok(data.orderList.every((v:any)=>v.userId==='U10001'));});
test('商家详情按店铺和负责人返回商品及互动内容',async()=>{const r=await call('/merchants/M20001/details');assert.equal(r.status,200);const {data}=await r.json();assert.equal(data.ownerId,'MUSER01');assert.equal(data.productList.length,10);assert.ok(data.productList.every((v:any)=>v.merchantId==='M20001'));assert.ok(Array.isArray(data.commentList));assert.equal((await call('/merchants/M20001/details','GET',undefined,user)).status,403);});
test('分类预设与公开目录一致且拒绝错误类型',async()=>{const {data}=await (await call('/category-options')).json();assert.ok(Object.keys(data).length>=5);const directory=await (await call('/catalog/categories')).json();assert.ok(directory.data.length>=25);assert.equal((await call('/categories','POST',{name:'新分类',parent:'不存在',sort:1,status:'已启用'})).status,400);});
test('评价删除保留订单关联，重复删除返回404',async()=>{const row=db.prepare('SELECT id,order_item_id FROM ratings LIMIT 1').get() as any;assert.ok(row);assert.equal((await call('/ratings/'+row.id,'DELETE',undefined,user)).status,403);assert.equal((await call('/ratings/'+row.id,'DELETE')).status,200);assert.ok(db.prepare('SELECT order_item_id FROM ratings WHERE id=?').get(row.id));const {data}=await (await call('/ratings')).json();assert.ok(!data.some((v:any)=>v.id===row.id));assert.equal((await call('/ratings/'+row.id,'DELETE')).status,404);});
test('帖子图片兼容封面，评论删除不影响其他帖子',async()=>{db.prepare("UPDATE community_posts SET cover_url='/public/test.jpg',images_json='[]' WHERE id='TDEMO01'").run();const {data}=await (await call('/community/TDEMO01/details')).json();assert.deepEqual(data.images,['/public/test.jpg']);const stamp=new Date().toISOString();db.prepare("INSERT INTO comments(id,post_id,user_id,content,status,created_at) VALUES('admin-delete-test','TDEMO01','U10001','评论','PUBLIC',?)").run(stamp);assert.equal((await call('/community/TDEMO01/comments/admin-delete-test','DELETE')).status,200);assert.equal((await call('/community/TDEMO01/comments/admin-delete-test','DELETE')).status,404);});
