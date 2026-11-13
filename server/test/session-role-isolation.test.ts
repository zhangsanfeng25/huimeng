import assert from 'node:assert/strict';
import {mkdtempSync} from 'node:fs';
import type {AddressInfo} from 'node:net';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import test, {after} from 'node:test';
import express from 'express';

const fixture = mkdtempSync(join(tmpdir(), 'huimeng-session-roles-'));
process.env.DATABASE_PATH = join(fixture, 'test.db');
process.env.UPLOAD_DIR = join(fixture, 'uploads');
const {db} = await import('../src/core/db.js');
await import('../scripts/migrate.js');
await import('../scripts/seed.js');
const {authRouter} = await import('../src/modules/auth.js');
const {shopRouter} = await import('../src/modules/shop.js');
const {syncRouter} = await import('../src/modules/sync.js');
const {customerServicesRouter} = await import('../src/modules/customer-services.js');
const {communityRouter} = await import('../src/modules/community.js');
const {canonicalHash} = await import('../src/sync/receipts.js');

const app = express();
app.use(express.json());
app.use('/auth', authRouter);
app.use(shopRouter, syncRouter, customerServicesRouter, communityRouter);
const server = app.listen(0, '127.0.0.1');
await new Promise<void>(resolve => server.once('listening', resolve));
const origin = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
after(() => { server.closeAllConnections(); server.close(); db.close(); });

function request(path: string, token = '', body?: object, serviceRole?: string) {
  const headers: Record<string, string> = {'Content-Type': 'application/json'};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (serviceRole) headers['X-Service-Role'] = serviceRole;
  return fetch(origin + path, {headers, method: body ? 'POST' : 'GET', body: body && JSON.stringify(body)});
}

const tokens = new Map<string,string>();
async function login(role: 'USER' | 'MERCHANT') {
  const existing=tokens.get(role);
  if(existing)return existing;
  const response = await request('/auth/login', '', {username: 'merchant_a', password: 'demo12345', role});
  assert.equal(response.status, 200);
  const token=(await response.json()).token as string;
  tokens.set(role,token);
  return token;
}

test('dual-role accounts can access only the selected role through REST', async () => {
  const buyer = await login('USER');
  const merchant = await login('MERCHANT');
  assert.equal((await request('/merchant/orders', buyer)).status, 403);
  assert.equal((await request('/me/orders', merchant)).status, 403);
  assert.equal((await request('/merchant/orders', merchant)).status, 200);
  assert.equal((await request('/me/orders', buyer)).status, 200);
});

test('legacy role switch and service role headers cannot change session identity', async () => {
  const buyer = await login('USER');
  assert.equal((await request('/auth/switch-role', buyer, {role: 'MERCHANT'})).status, 403);
  assert.equal((await request('/service/conversations', buyer, undefined, 'MERCHANT')).status, 403);
  assert.equal((await (await request('/auth/me', buyer)).json()).user.activeRole, 'USER');
});

test('a merchant snapshot cannot be continued with a buyer token', async () => {
  const merchant = await login('MERCHANT');
  const buyer = await login('USER');
  const page = await request('/sync/snapshots/merchantProducts?limit=1', merchant);
  assert.equal(page.status, 200);
  const {snapshotId} = await page.json();
  const response = await request(`/sync/snapshots/merchantProducts?limit=1&snapshotId=${snapshotId}`, buyer);
  assert.ok([403, 409].includes(response.status));
});

test('sync does not elevate a buyer or replay another role’s receipt', async () => {
  const merchant = await login('MERCHANT');
  const buyer = await login('USER');
  const payload = {shopName: '身份测试店铺', description: '这段说明只用于测试商家身份的同步。'};
  const envelope = {protocolVersion: 1, clientId: 'role-test', operationId: 'role-test-save',
    action: 'merchant.profile', payload, payloadHash: canonicalHash(payload)};
  assert.equal((await request('/sync/operations', merchant, envelope)).status, 200);
  assert.equal((await request('/sync/operations', buyer, envelope)).status, 403);
});

test('order and after-sale notices follow the role, including legacy order notices', async () => {
  const merchant = await login('MERCHANT');
  const buyer = await login('USER');
  const owner = (db.prepare("SELECT id FROM users WHERE username='merchant_a'").get() as {id:string}).id;
  const notice = db.prepare("INSERT INTO notifications(id,user_id,audience,title,content,status,created_at) VALUES(?,?,?,?,?,'SENT',?)");
  const timestamp=new Date().toISOString();
  notice.run('legacy-merchant-notice',owner,'订单消息','收到新订单','测试商家订单',timestamp);
  notice.run('buyer-sale-notice',owner,'用户售后消息','售后进度更新','测试买家售后',timestamp);
  const ids = async (path:string, token:string, field:string) =>
    (await (await request(path,token)).json())[field].map((row:{id:string})=>row.id);
  assert.ok((await ids('/me/notifications',merchant,'data')).includes('legacy-merchant-notice'));
  assert.ok(!(await ids('/me/notifications',merchant,'data')).includes('buyer-sale-notice'));
  assert.ok(!(await ids('/sync/snapshots/notifications',buyer,'items')).includes('legacy-merchant-notice'));
  assert.ok((await ids('/sync/snapshots/notifications',buyer,'items')).includes('buyer-sale-notice'));
  assert.equal((await request('/me/notifications/legacy-merchant-notice/read',buyer,{})).status,404);
  assert.equal((await request('/me/notifications/legacy-merchant-notice/read',merchant,{})).status,200);
});
