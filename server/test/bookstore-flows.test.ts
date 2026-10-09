import test, { after } from 'node:test';
import assert from 'node:assert/strict';
import express from 'express';
import { mkdtempSync, rmSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';

const fixture = mkdtempSync(join(tmpdir(), 'huimeng-bookstore-'));
process.env.DATABASE_PATH = join(fixture, 'test.db');
const { db } = await import('../src/core/db.js');
await import('../scripts/migrate.js');
await import('../scripts/seed.js');
const { authRouter } = await import('../src/modules/auth.js');
const { shopRouter } = await import('../src/modules/shop.js');
const app = express();
app.use(express.json()); app.use('/auth', authRouter); app.use(shopRouter);
app.use((error: Error, _req: express.Request, res: express.Response, _next: express.NextFunction) => res.status(400).json({message: error.message}));
const server = app.listen(0, '127.0.0.1');
await new Promise<void>(resolve => server.once('listening', resolve));
const address = server.address(); assert.ok(address && typeof address !== 'string');
const base = `http://127.0.0.1:${address.port}`;
after(async () => { await new Promise<void>(resolve => server.close(() => resolve())); db.close(); rmSync(fixture, { recursive: true, force: true }); });
async function login(username: string) { const response = await fetch(`${base}/auth/login`, {method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username,password:'demo12345',role:'USER'})}); return (await response.json() as {token:string}).token; }
const token = await login('xingxing'), other = await login('xiaoxiong');
test('未验证身份的密码找回不能直接修改账号密码',async()=>{
  await fetch(base+'/auth/register',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'recovery-test',nickname:'找回验证测试',password:'original123'})});
  const before=db.prepare("SELECT password_hash FROM users WHERE username='recovery-test'").get();
  assert.ok(before);
  const response=await fetch(base+'/auth/forgot-password',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({account:'recovery-test',newPassword:'replacement123'})});
  assert.equal(response.status,501);assert.deepEqual(db.prepare("SELECT password_hash FROM users WHERE username='recovery-test'").get(),before);
});
async function call(path: string, method='GET', body?: unknown, actor=token, key?: string) { return fetch(base+path,{method,headers:{Authorization:`Bearer ${actor}`,'Content-Type':'application/json',...(key?{'Idempotency-Key':key}:{})},body:body===undefined?undefined:JSON.stringify(body)}); }

test('搜索关键词和价格排序同时生效，价格升降序可切换', async () => {
  for (const sort of ['price_asc','price_desc']) {
    const response = await call(`/catalog/products?q=${encodeURIComponent('晚安')}&sort=${sort}`);
    const {data} = await response.json() as {data:Array<{name:string;priceCents:number}>};
    assert.ok(data.length>=2); assert.ok(data.every(row=>row.name.includes('晚安')));
    const prices=data.map(row=>row.priceCents); assert.deepEqual(prices,[...prices].sort((a,b)=>sort==='price_asc'?a-b:b-a));
  }
});
test('适龄筛选排除不匹配商品并支持年龄区间重叠', async () => {
  db.prepare("UPDATE product_revisions SET age_range='0–2岁' WHERE id='PR0001'").run();
  const {data} = await (await call('/catalog/products?age=6-9')).json() as {data:Array<{id:string}>};
  assert.ok(data.some(row=>row.id==='P0002')); assert.ok(!data.some(row=>row.id==='P0001'));
});
test('主题筛选返回实际相关绘本并允许零结果', async () => {
  const {data} = await (await call('/catalog/products?theme=bedtime')).json() as {data:Array<{name:string}>};
  assert.ok(data.length>0); assert.ok(data.every(row=>/晚安|睡前/.test(row.name)));
  const empty=await (await call('/catalog/products?age=30-40')).json() as {data:unknown[]}; assert.equal(empty.data.length,0);
});
test('目录分页总数正确，非法排序被拒绝', async () => {
  const result=await (await call('/catalog/products?page=1&pageSize=3')).json() as {data:unknown[];total:number;hasMore:boolean};
  assert.equal(result.data.length,3); assert.ok(result.total>3); assert.equal(result.hasMore,true);
  assert.equal((await call('/catalog/products?sort=sql-injection')).status,400);
});
test('购物车可从服务端读取数量并拒绝超库存和下架商品', async () => {
  assert.equal((await call('/me/cart/P0001','PUT',{quantity:2,selected:true})).status,200);
  const response=await call('/me/cart'); assert.equal(response.status,200);
  const {data}=await response.json() as {data:Array<{id:string;quantity:number}>}; assert.equal(data.find(row=>row.id==='P0001')?.quantity,2);
  assert.equal((await call('/me/cart/P0001','PUT',{quantity:99})).status,409);
  db.prepare("UPDATE products SET stock_available=0 WHERE id='P0001'").run();
  try {
    assert.equal((await call('/me/cart/P0001','PUT',{quantity:2,selected:false})).status,200);
    assert.equal((await call('/me/cart/P0001','PUT',{quantity:2,selected:true})).status,409);
  } finally { db.prepare("UPDATE products SET stock_available=40 WHERE id='P0001'").run(); }
  db.prepare("UPDATE products SET status='OFFLINE' WHERE id='P0003'").run();
  assert.equal((await call('/me/cart/P0003','PUT',{quantity:1})).status,409);
});
test('地址修改不增加记录，不能修改或删除其他账户地址', async () => {
  const body={recipient:'新的收货人',phone:'13800002001',region:'辽宁省大连市',detail:'新地址 20 号',isDefault:true};
  const before=(db.prepare("SELECT COUNT(*) count FROM addresses WHERE user_id='U10001'").get() as {count:number}).count;
  assert.equal((await call('/me/addresses/ADDR001','PUT',body)).status,200);
  assert.equal((db.prepare("SELECT COUNT(*) count FROM addresses WHERE user_id='U10001'").get() as {count:number}).count,before);
  assert.equal((db.prepare("SELECT recipient FROM addresses WHERE id='ADDR001'").get() as {recipient:string}).recipient,body.recipient);
  assert.equal((await call('/me/addresses/ADDR001','PUT',body,other)).status,404);
  assert.equal((await call('/me/addresses/ADDR001','DELETE',undefined,other)).status,404);
});
test('删除默认地址后由剩余地址承接默认状态', async () => {
  const created=await (await call('/me/addresses','POST',{recipient:'备用',phone:'13800002001',region:'辽宁省',detail:'备用地址',isDefault:false})).json() as {id:string};
  assert.equal((await call('/me/addresses/ADDR001','DELETE')).status,200);
  const {data}=await (await call('/me/addresses')).json() as {data:Array<{id:string;is_default:number}>};
  assert.equal(data.find(row=>row.id===created.id)?.is_default,1);
});
test('取消多店待付款结算组仅释放一次库存且保持权限隔离', async () => {
  const {data:addresses}=await (await call('/me/addresses')).json() as {data:Array<{id:string}>};
  const before=db.prepare("SELECT stock_available available,stock_reserved reserved FROM products WHERE id='P0002'").get() as {available:number;reserved:number};
  const createdResponse=await call('/checkout','POST',{addressId:addresses[0].id,items:[{productId:'P0001',quantity:1},{productId:'P0002',quantity:2}]},token,'cancel-test'); assert.equal(createdResponse.status,201);
  const created=await createdResponse.json() as {checkoutGroupId:string;orderIds:string[]};
  assert.equal((await call(`/me/orders/${created.orderIds[0]}/cancel`,'POST',{},other)).status,404);
  assert.equal((await call(`/me/orders/${created.orderIds[0]}/cancel`,'POST',{})).status,200);
  assert.equal((await call(`/me/orders/${created.orderIds[0]}/cancel`,'POST',{})).status,200);
  assert.deepEqual(db.prepare("SELECT stock_available available,stock_reserved reserved FROM products WHERE id='P0002'").get(),before);
  const orders=db.prepare('SELECT status FROM orders WHERE checkout_group_id=?').all(created.checkoutGroupId) as Array<{status:string}>; assert.ok(orders.every(order=>order.status==='CANCELLED'));
  assert.equal((await call(`/checkout/${created.checkoutGroupId}/pay`,'POST',{method:'DEMO_BALANCE'},token,'cancel-pay')).status,400);
});
test('过期订单读取时关闭且不能再次释放库存', async () => {
  const {data:addresses}=await (await call('/me/addresses')).json() as {data:Array<{id:string}>};
  const before=db.prepare("SELECT stock_available available,stock_reserved reserved FROM products WHERE id='P0002'").get();
  const created=await (await call('/checkout','POST',{addressId:addresses[0].id,items:[{productId:'P0002',quantity:1}]},token,'expire-test')).json() as {checkoutGroupId:string};
  db.prepare("UPDATE checkout_groups SET expires_at='2000-01-01T00:00:00.000Z' WHERE id=?").run(created.checkoutGroupId);
  const result=await (await call(`/checkout/${created.checkoutGroupId}`)).json() as {data:{status:string}}; assert.equal(result.data.status,'CLOSED');
  await call(`/checkout/${created.checkoutGroupId}`); assert.deepEqual(db.prepare("SELECT stock_available available,stock_reserved reserved FROM products WHERE id='P0002'").get(),before);
});
