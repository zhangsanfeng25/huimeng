import test,{after} from 'node:test';import assert from 'node:assert/strict';
import {mkdtempSync,readdirSync,readFileSync} from 'node:fs';import {join} from 'node:path';import {tmpdir} from 'node:os';
process.env.DATABASE_PATH=join(mkdtempSync(join(tmpdir(),'huimeng-legacy-sale-')),'db');
const {db,now}=await import('../src/core/db.js');
for(const file of readdirSync('migrations').filter(f=>f.endsWith('.sql')&&f<'011'))db.exec(readFileSync(join('migrations',file),'utf8'));
await import('../scripts/seed.js');
after(()=>db.close());
test('legacy full-order cases, refunds and foreign keys survive partial migration',()=>{
 const order=db.prepare('SELECT * FROM orders LIMIT 1').get() as any;
 db.prepare("INSERT INTO after_sales(id,order_id,buyer_id,merchant_id,type,amount_cents,reason,status,client_id,created_at,updated_at) VALUES('legacy',?,?,?,'REFUND_ONLY',?,'旧整单申请','COMPLETED','legacy',?,?)").run(order.id,order.user_id,order.merchant_id,order.total_cents,now(),now());
 db.prepare("INSERT INTO refunds(id,after_sale_id,order_id,amount_cents,method,status,created_at) VALUES('legacy-refund','legacy',?,?,'DEMO','COMPLETED',?)").run(order.id,order.total_cents,now());
 const previous=db.prepare('SELECT * FROM refunds').all();
 db.transaction(()=>db.exec(readFileSync('migrations/011_partial_after_sales.sql','utf8')))();
 assert.deepEqual(db.prepare('SELECT * FROM refunds').all(),previous);
 const rows=db.prepare("SELECT x.*,i.quantity original,i.subtotal_cents subtotal FROM after_sale_items x JOIN order_items i ON i.id=x.order_item_id WHERE x.after_sale_id='legacy'").all() as any[];
 assert.ok(rows.length>0);for(const row of rows){assert.equal(row.quantity,row.original);assert.equal(row.restock_quantity,row.original);assert.equal(row.amount_cents,row.subtotal);}
 assert.deepEqual(db.pragma('foreign_key_check'),[]);
});
