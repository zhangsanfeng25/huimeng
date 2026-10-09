import test, { after } from 'node:test';
import assert from 'node:assert/strict';
import express from 'express';
import { mkdtempSync, rmSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';

const fixture = mkdtempSync(join(tmpdir(), 'huimeng-account-'));
process.env.DATABASE_PATH = join(fixture, 'test.db');
const { db } = await import('../src/core/db.js');
await import('../scripts/migrate.js');
await import('../scripts/seed.js');
const { authRouter } = await import('../src/modules/auth.js');
const app = express(); app.use(express.json()); app.use('/auth', authRouter);
const server = app.listen(0, '127.0.0.1');
await new Promise<void>(resolve => server.once('listening', resolve));
const address = server.address(); assert.ok(address && typeof address !== 'string');
const base = `http://127.0.0.1:${address.port}/auth`;
after(async () => { await new Promise<void>(resolve => server.close(() => resolve())); db.close(); rmSync(fixture, { recursive: true, force: true }); });
async function login(password = 'demo12345', username = 'xingxing') {
  return fetch(`${base}/login`, { method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({username,password,role:'USER'}) });
}
const { token } = await (await login()).json() as {token:string};
async function save(value: Record<string,unknown>) {
  return fetch(`${base}/me`, {method:'PUT', headers:{'Content-Type':'application/json',Authorization:`Bearer ${token}`}, body:JSON.stringify(value)});
}
test('错误旧密码或不完整密码不应部分保存资料', async () => {
  for (const fields of [{currentPassword:'wrongpass',newPassword:'updated123'}, {currentPassword:'demo12345'}, {newPassword:'updated123'}]) {
    assert.equal((await save({username:'changed_reader',nickname:'已修改',...fields})).status,400);
    assert.equal((db.prepare('SELECT username FROM users WHERE id=?').get('U10001') as {username:string}).username,'xingxing');
  }
});
test('只保存资料保留会话，用户名冲突不能更改密码', async () => {
  assert.equal((await save({username:'xingxing',nickname:'新的昵称'})).status,200);
  assert.equal((await save({username:'xiaoxiong',nickname:'冲突昵称',currentPassword:'demo12345',newPassword:'updated123'})).status,409);
  assert.equal((await fetch(`${base}/me`,{headers:{Authorization:`Bearer ${token}`}})).status,200);
  assert.equal((await login()).status,200);
});
test('同次保存资料与密码成功并撤销全部旧会话', async () => {
  const response=await save({username:'changed_reader',nickname:'已修改',currentPassword:'demo12345',newPassword:'updated123'});
  assert.equal(response.status,200);
  assert.equal((await response.json() as {passwordChanged:boolean}).passwordChanged,true);
  assert.equal((await fetch(`${base}/me`,{headers:{Authorization:`Bearer ${token}`}})).status,401);
  assert.equal((await login('demo12345','changed_reader')).status,401);
  assert.equal((await login('updated123','changed_reader')).status,200);
});
