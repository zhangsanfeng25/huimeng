import assert from 'node:assert/strict';
import { spawn, spawnSync } from 'node:child_process';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createServer } from 'node:net';

const cwd = fileURLToPath(new URL('..', import.meta.url));
const fixture = mkdtempSync(join(tmpdir(), 'huimeng-sync-'));
const socket = createServer();
await new Promise(resolve => socket.listen(0, '127.0.0.1', resolve));
const port = socket.address().port;
await new Promise(resolve => socket.close(resolve));
const env = { ...process.env, PORT: String(port), DATABASE_PATH: join(fixture, 'test.db'), UPLOAD_DIR: join(fixture, 'uploads') };
let child;
const abort = new AbortController();
try {
  for (const file of ['dist/scripts/migrate.js', 'dist/scripts/seed.js']) {
    const result = spawnSync(process.execPath, [file], { cwd, env, encoding: 'utf8' });
    assert.equal(result.status, 0, result.stderr);
  }
  child = spawn(process.execPath, ['dist/main.js'], { cwd, env, stdio: ['ignore', 'pipe', 'pipe'], windowsHide: true });
  let output = '';
  child.stdout.on('data', chunk => output += chunk);
  child.stderr.on('data', chunk => output += chunk);
  const base = `http://127.0.0.1:${port}/api/v1`;
  let ready = false;
  for (let i = 0; i < 50; i++) {
    try { ready = (await (await fetch(`${base}/health`)).json()).status === 'ok'; } catch {}
    if (ready) break;
    await new Promise(resolve => setTimeout(resolve, 100));
  }
  assert.ok(ready, output);
  async function call(path, token, method = 'GET', body) {
    const response = await fetch(base + path, { method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) }, body: body ? JSON.stringify(body) : undefined });
    const data = await response.json();
    assert.ok(response.ok, `${method} ${path}: ${response.status} ${JSON.stringify(data)}`);
    return data;
  }
  const login = async (username, role, password = 'demo12345') => (await call('/auth/login', '', 'POST', { username, password, role })).token;
  const admin = await login('admin', 'ADMIN', 'huimeng123');
  const merchant = await login('merchant_a', 'MERCHANT');
  const user = await login('xingxing', 'USER');
  const stream = await fetch(`${base}/events?token=${admin}`, { signal: abort.signal });
  assert.ok(stream.ok);
  const received = [];
  const reading = (async () => {
    let pending = '';
    for await (const chunk of stream.body) {
      pending += Buffer.from(chunk).toString('utf8');
      let end;
      while ((end = pending.indexOf('\n\n')) >= 0) {
        const block = pending.slice(0, end); pending = pending.slice(end + 2);
        if (block.startsWith('event: update')) received.push(JSON.parse(block.split('data: ')[1]));
      }
    }
  })().catch(error => { if (!abort.signal.aborted) throw error; });
  async function expectUpdate(resource, action, mutate, check) {
    received.length = 0;
    const result = await mutate();
    for (let i = 0; i < 30 && !received.some(event => event.resource === resource && event.action === action); i++) await new Promise(resolve => setTimeout(resolve, 50));
    assert.ok(received.some(event => event.resource === resource && event.action === action), `Missing live update: ${resource}/${action}`);
    await check(result);
  }
  await expectUpdate('users', 'create', () => call('/auth/register', '', 'POST', { username: 'sync_test', nickname: '同步新用户', password: 'test12345' }), async result => {
    assert.ok((await call('/users', admin)).data.some(row => row.id === result.id));
  });
  await expectUpdate('users', 'update', () => call('/auth/me/avatar', user, 'PUT', { avatarUrl: '/public/default-avatars/fox.png' }), async () => {
    assert.equal((await call('/users/U10001/details', admin)).data.avatarUrl, '/public/default-avatars/fox.png');
    assert.equal((await call('/users', admin)).data.find(row => row.id === 'U10001').avatarUrl, '/public/default-avatars/fox.png');
    assert.ok(received.some(event => event.resource === 'community'));
  });
  const category = (await call('/categories', admin)).data[0].id;
  const draft = { name: '同步绘本', summary: '测试手机新增同步', categoryId: category, ageRange: '3-6岁', priceCents: 1200, stock: 9 };
  let productId;
  await expectUpdate('products', 'create', () => call('/merchant/products', merchant, 'POST', draft), async result => {
    productId = result.productId;
    assert.ok((await call('/products', admin)).data.some(row => row.id === productId && row.name === '同步绘本'));
  });
  await expectUpdate('products', 'update', () => call(`/merchant/products/${productId}`, merchant, 'PUT', { ...draft, name: '已修改的同步绘本' }), async () => {
    assert.ok((await call('/products', admin)).data.some(row => row.id === productId && row.name === '已修改的同步绘本'));
  });
  const submission=await call(`/merchant/products/${productId}/submit`,merchant,'POST');
  const review=await call(`/reviews/${submission.revisionId}/details`,admin);
  assert.equal(review.data.name,'已修改的同步绘本');assert.equal(review.data.summary,draft.summary);assert.equal(review.data.approval,'PENDING');assert.equal(review.data.version,review.data.latestVersion);
  const rejected=await fetch(base+`/reviews/${submission.revisionId}`,{method:'PUT',headers:{'Content-Type':'application/json',Authorization:`Bearer ${admin}`},body:JSON.stringify({status:'已驳回',note:''})});assert.equal(rejected.status,400);
  await call(`/reviews/${submission.revisionId}`,admin,'PUT',{status:'已通过',note:'核对图文和价格'});
  const repeated=await fetch(base+`/reviews/${submission.revisionId}`,{method:'PUT',headers:{'Content-Type':'application/json',Authorization:`Bearer ${admin}`},body:JSON.stringify({status:'已通过'})});assert.equal(repeated.status,409);
  assert.equal((await call(`/products/${productId}/details`,admin)).data.name,review.data.name);
  const missing=await fetch(`http://127.0.0.1:${port}/public/missing.jpg`);assert.equal(missing.status,404);
  const forged=await fetch(base+`/products/${productId}/content`,{method:'PUT',headers:{'Content-Type':'application/json',Authorization:`Bearer ${admin}`},body:JSON.stringify({...review.data,coverUrl:'/public/not-owned.jpg',images:[]})});assert.equal(forged.status,400);
  console.log('Admin details passed: exact pending revision, rejection reason, duplicate approval, unauthorized media and missing image 404.');
  let post;
  await expectUpdate('community', 'create', () => call('/community/posts', user, 'POST', { title: '同步帖子', content: '来自手机接口的内容' }), async result => {
    post = result.id;
    assert.ok((await call('/community', admin)).data.some(row => row.id === post));
  });
  await call(`/community/posts/${post}/like`, user, 'DELETE');
  await expectUpdate('community', 'like', () => call(`/community/posts/${post}/like`, user, 'PUT'), async () => {
    assert.ok((await call(`/community/${post}/details`, admin)).data);
  });
  abort.abort(); await reading;
  console.log('Live sync passed: registration, merchant create/edit, community likes; isolated database.');
} finally {
  abort.abort();
  if (child && child.exitCode === null) {
    const stopped = new Promise(resolve => child.once('close', resolve));
    child.kill(); await stopped;
  }
  rmSync(fixture, { recursive: true, force: true });
}
