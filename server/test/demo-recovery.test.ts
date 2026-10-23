import test,{after} from 'node:test';import assert from 'node:assert/strict';import {mkdtempSync} from 'node:fs';import {tmpdir} from 'node:os';import {join} from 'node:path';import express from 'express';
process.env.DATABASE_PATH=join(mkdtempSync(join(tmpdir(),'huimeng-recovery-')),'db');
const {db}=await import('../src/core/db.js');await import('../scripts/migrate.js');await import('../scripts/seed.js');
const {authRouter}=await import('../src/modules/auth.js');const {config}=await import('../src/config.js');const {verifyPassword}=await import('../src/core/security.js');
const app=express();app.use(express.json());app.use(authRouter);const server=app.listen(0,'127.0.0.1');await new Promise<void>(r=>server.once('listening',r));
const base=`http://127.0.0.1:${(server.address() as import('node:net').AddressInfo).port}`;
async function reset(account:string,newPassword='new-demo-password'){const r=await fetch(base+'/forgot-password',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({account,newPassword})});return {status:r.status,data:await r.json()};}
after(()=>{server.closeAllConnections();server.close();db.close();});
test('demo recovery matches stored contact, changes password and invalidates sessions',async()=>{
 config.demoMode=true;db.prepare('UPDATE users SET email=?,phone=? WHERE id=?').run('reader@example.test','13900000001','U10001');
 db.prepare("INSERT INTO sessions(token_hash,user_id,active_role,expires_at,created_at) VALUES('old-token','U10001','USER','2099-01-01','2026-10-23')").run();
 const result=await reset('13900000001');assert.equal(result.status,200);assert.equal(result.data.userId,'U10001');
 assert.equal(verifyPassword('new-demo-password',(db.prepare('SELECT password_hash h FROM users WHERE id=?').get('U10001') as any).h),true);
 assert.equal((db.prepare('SELECT COUNT(*) n FROM sessions WHERE user_id=?').get('U10001') as any).n,0);
 assert.equal((await reset('reader@example.test')).status,200);
 assert.equal((await reset('xingxing')).status,404);assert.equal((await reset('missing@example.test')).status,404);
});
test('demo reset is disabled outside demo mode and rejects invalid input',async()=>{
 config.demoMode=false;assert.equal((await reset('13900000001')).status,403);config.demoMode=true;
 assert.equal((await reset('13900000001','short')).status,400);
 db.prepare("UPDATE users SET status='DISABLED' WHERE id='U10001'").run();assert.equal((await reset('13900000001')).status,404);
});
