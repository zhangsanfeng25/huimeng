import test, {after} from 'node:test';
import assert from 'node:assert/strict';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {join} from 'node:path';
import {createNormalizationFixture} from './helpers/normalization-fixture.js';
import {buildNormalizedCopy} from '../src/normalization/build-target.js';

if(!process.env.RECEIPT_TEST_SCHEMA){
 for(const schema of ['legacy','normalized'])test(`automatic receipt: ${schema}`,()=>{
  const env={...process.env,RECEIPT_TEST_SCHEMA:schema};delete env.NODE_TEST_CONTEXT;
  const child=spawnSync(process.execPath,['--import','tsx','--test',fileURLToPath(import.meta.url)],{env,encoding:'utf8'});
  assert.equal(child.status,0,child.stdout+child.stderr);
 });
}else{
 const fixture=await createNormalizationFixture({through:'011',case:'standard'});
 let target=fixture.databasePath;
 if(process.env.RECEIPT_TEST_SCHEMA==='normalized'){
  target=join(fixture.uploadDir,'..','normalized.db');
  await buildNormalizedCopy({sourcePath:fixture.databasePath,targetPath:target,manifestPath:join(fixture.uploadDir,'..','manifest.json'),uploadDir:fixture.uploadDir});
 }
 process.env.DATABASE_PATH=target;
 const {db,readTables,normalizedSchema}=await import('../src/core/db.js');
 const lifecycle=await import('../src/core/auto-receipt.js');
 after(async()=>{db.close();await fixture.dispose();});
 test('deadline, after-sale blocking, full refund, and idempotent completion',()=>{
  const stamp='2026-11-24T00:00:00.000Z';
  assert.equal(typeof lifecycle.confirmOverdueOrders,'function');
  const shipping=(id:string,date:string|null,status='OUT_FOR_DELIVERY')=>db.prepare(`UPDATE orders SET ${normalizedSchema?'':"status='SHIPPED',"}logistics_status=?,shipped_at=? WHERE id=?`).run(status,date,id);
  shipping('O1','2026-10-25T00:00:00.001Z');shipping('O2',null);
  assert.equal(lifecycle.confirmOverdueOrders(stamp),0,'one millisecond before deadline and missing shipment date stay open');
  shipping('O1','2026-10-25T00:00:00.000Z','IN_TRANSIT');
  assert.equal(lifecycle.confirmOverdueOrders(stamp),0,'cannot complete before delivery stage');
  shipping('O1','2026-10-25T00:00:00.000Z');
  db.prepare(normalizedSchema?"INSERT INTO after_sales(id,order_id,type,amount_cents,reason,status,created_at,updated_at) VALUES('AS-auto','O1','REFUND_ONLY',6000,'test','MERCHANT_REVIEW',?,?)":"INSERT INTO after_sales(id,order_id,buyer_id,merchant_id,type,amount_cents,reason,status,client_id,created_at,updated_at) VALUES('AS-auto','O1','U1','M1','REFUND_ONLY',6000,'test','MERCHANT_REVIEW','auto-test',?,?)").run(stamp,stamp);
  assert.equal(lifecycle.confirmOverdueOrders(stamp),0,'active after-sale pauses confirmation');
  db.prepare("UPDATE after_sales SET status='REJECTED' WHERE id='AS-auto'").run();
  const before=(db.prepare('SELECT COUNT(*) n FROM notifications').get() as {n:number}).n;
  assert.equal(lifecycle.confirmOverdueOrders(stamp),1,'deadline is inclusive, resumes after rejection');
  assert.equal((db.prepare(`SELECT status FROM ${readTables.orders} WHERE id='O1'`).get() as {status:string}).status,'COMPLETED');
  assert.equal((db.prepare("SELECT COUNT(*) n FROM order_events WHERE order_id='O1' AND action='AUTO_CONFIRM'").get() as {n:number}).n,1);
  assert.equal((db.prepare('SELECT COUNT(*) n FROM notifications').get() as {n:number}).n,before+2);
  assert.equal(lifecycle.confirmOverdueOrders(stamp),0,'repeated sweep does not duplicate events or notices');
  shipping('O1','2026-10-25T00:00:00.000Z');
  db.prepare("UPDATE after_sales SET status='COMPLETED' WHERE id='AS-auto'").run();
  db.prepare(normalizedSchema?"INSERT INTO refunds(id,after_sale_id,amount_cents,method,status,created_at) VALUES('RF-auto','AS-auto',6000,'DEMO','COMPLETED',?)":"INSERT INTO refunds(id,after_sale_id,order_id,amount_cents,method,status,created_at) VALUES('RF-auto','AS-auto','O1',6000,'DEMO','COMPLETED',?)").run(stamp);
  assert.equal(lifecycle.confirmOverdueOrders(stamp),0,'fully refunded orders stay excluded');
  assert.deepEqual(db.pragma('foreign_key_check'),[]);
 });
}
