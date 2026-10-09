import test, { after } from 'node:test';
import assert from 'node:assert/strict';
import express from 'express';
import { mkdtempSync, rmSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';

const fixture = mkdtempSync(join(tmpdir(), 'huimeng-profile-'));
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
  await new Promise<void>((resolve, reject) => server.close(error => error ? reject(error) : resolve()));
  db.close();
  rmSync(fixture, { recursive: true, force: true });
});
const login = await fetch(`${base}/auth/login`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: 'xingxing', password: 'demo12345', role: 'USER' }) });
const { token } = await login.json() as { token: string };
assert.ok(token);
const headers = { Authorization: `Bearer ${token}` };
const author = db.prepare('SELECT id,nickname FROM users WHERE username=?').get('xingxing') as { id: string; nickname: string };
db.prepare('UPDATE users SET avatar_url=? WHERE id=?').run('/public/profile-test.jpg', author.id);
db.prepare("INSERT INTO community_posts(id,author_user_id,author_role,title,content,status,created_at,updated_at) VALUES(?,?,'USER',?,?,'HIDDEN',?,?)").run('profile-hidden', author.id, '隐藏帖子', '不应出现在主页', new Date().toISOString(), new Date().toISOString());

test('社区列表和详情返回可跳转的作者 ID 与头像', async () => {
  const response = await fetch(`${base}/community/posts`, { headers });
  const { data } = await response.json() as { data: Array<Record<string, unknown>> };
  const post = data.find(row => row.authorName === author.nickname);
  assert.ok(post);
  assert.equal(post.authorId, author.id);
  assert.equal(post.avatarUrl, '/public/profile-test.jpg');
  const detail = await (await fetch(`${base}/community/posts/${post.id}`, { headers })).json() as { data: Record<string, unknown> };
  assert.equal(detail.data.authorId, author.id);
});

test('作者主页只返回本人公开帖子与公开资料', async () => {
  const response = await fetch(`${base}/community/users/${author.id}`, { headers });
  assert.equal(response.status, 200);
  const { data } = await response.json() as { data: { user: Record<string, unknown>; posts: Array<Record<string, unknown>>; postCount: number; likeCount: number } };
  assert.equal(data.user.id, author.id);
  assert.equal(data.user.nickname, author.nickname);
  assert.equal(data.user.avatarUrl, '/public/profile-test.jpg');
  assert.deepEqual(Object.keys(data.user).sort(), ['avatarUrl', 'id', 'nickname']);
  assert.ok(data.posts.length > 0);
  assert.equal(data.postCount, data.posts.length);
  assert.equal(data.likeCount, data.posts.reduce((sum, post) => sum + Number(post.likeCount), 0));
  assert.ok(data.posts.every(post => post.authorId === author.id && post.id !== 'profile-hidden'));
  for (let index = 1; index < data.posts.length; index++) assert.ok(String(data.posts[index - 1].createdAt) >= String(data.posts[index].createdAt));
});

test('不存在或停用的用户不可访问，匿名请求需要登录', async () => {
  assert.equal((await fetch(`${base}/community/users/missing`, { headers })).status, 404);
  assert.equal((await fetch(`${base}/community/users/${author.id}`)).status, 401);
  const other = db.prepare('SELECT id FROM users WHERE username=?').get('merchant_a') as { id: string };
  db.prepare("UPDATE users SET status='DISABLED' WHERE id=?").run(other.id);
  assert.equal((await fetch(`${base}/community/users/${other.id}`, { headers })).status, 404);
});
