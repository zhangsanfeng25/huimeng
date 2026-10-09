import test, { after } from 'node:test';
import assert from 'node:assert/strict';
import express from 'express';
import { mkdtempSync, rmSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';

const fixture = mkdtempSync(join(tmpdir(), 'huimeng-avatars-'));
process.env.DATABASE_PATH = join(fixture, 'test.db');
const { db } = await import('../src/core/db.js');
await import('../scripts/migrate.js');
await import('../scripts/seed.js');
const { authRouter } = await import('../src/modules/auth.js');
const { communityRouter } = await import('../src/modules/community.js');
const app = express();
app.use(express.json());
app.use('/auth', authRouter);
app.use(communityRouter);
const server = app.listen(0, '127.0.0.1');
await new Promise<void>(resolve => server.once('listening', resolve));
const address = server.address();
assert.ok(address && typeof address !== 'string');
const base = `http://127.0.0.1:${address.port}`;
after(async () => {
  await new Promise<void>(resolve => server.close(() => resolve()));
  db.close();
  rmSync(fixture, { recursive: true, force: true });
});
const login = await (await fetch(base + '/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: 'xingxing', password: 'demo12345', role: 'USER' }) })).json();
const headers = { Authorization: `Bearer ${login.token}`, 'Content-Type': 'application/json' };
const paths = ['sprout', 'whale', 'house', 'fox', 'cat'].map(name => `/public/default-avatars/${name}.png`);
const save = (avatarUrl: string) => fetch(base + '/auth/me/avatar', { method: 'PUT', headers, body: JSON.stringify({ avatarUrl }) });

test('默认头像目录提供五个不同选项，匿名不可更改头像', async () => {
  const response = await fetch(base + '/auth/default-avatars', { headers });
  assert.equal(response.status, 200);
  const { data } = await response.json();
  assert.deepEqual(data.map((v: { avatarUrl: string }) => v.avatarUrl), paths);
  assert.equal((await fetch(base + '/auth/me/avatar', { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ avatarUrl: paths[0] }) })).status, 401);
});

test('五种默认头像均可保存，个人资料和社区作者随之更新', async () => {
  for (const avatarUrl of paths) {
    const response = await save(avatarUrl);
    assert.equal(response.status, 200);
    assert.equal((await response.json()).avatarUrl, avatarUrl);
    assert.equal((await (await fetch(base + '/auth/me', { headers })).json()).user.avatarUrl, avatarUrl);
    assert.equal((await (await fetch(base + '/community/users/U10001', { headers })).json()).data.user.avatarUrl, avatarUrl);
  }
});

test('拒绝伪造默认头像、路径穿越及他人的上传图片，保留本人上传能力', async () => {
  for (const path of ['/public/default-avatars/missing.png', '/public/default-avatars/../cat.png', 'https://example.com/avatar.png']) assert.equal((await save(path)).status, 400);
  const own = '/public/community-aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.jpg';
  const other = '/public/community-bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb.jpg';
  db.prepare('INSERT INTO community_uploads(path,user_id,created_at) VALUES(?,?,?)').run(own, 'U10001', new Date().toISOString());
  db.prepare('INSERT INTO community_uploads(path,user_id,created_at) VALUES(?,?,?)').run(other, 'U10002', new Date().toISOString());
  assert.equal((await save(other)).status, 403);
  assert.equal((await save(own)).status, 200);
});

test('新注册用户获得使用次数最少的默认头像', async () => {
  db.prepare('UPDATE users SET avatar_url=? WHERE id<>?').run(paths[0], 'A0001');
  const response = await fetch(base + '/auth/register', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: 'avatar_new', nickname: '新读者', password: 'test12345' }) });
  assert.equal(response.status, 201);
  const user = db.prepare('SELECT avatar_url FROM users WHERE username=?').get('avatar_new') as { avatar_url: string };
  assert.equal(user.avatar_url, paths[1]);
});
