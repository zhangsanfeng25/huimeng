import test from 'node:test';
import assert from 'node:assert/strict';
import Database from 'better-sqlite3';
import {readFileSync} from 'node:fs';
import {createHash} from 'node:crypto';
import {createNormalizationFixture} from './helpers/normalization-fixture.js';

async function inspector(){
 const name='../src/normalization/preflight.js';
 const module=await import(name).catch(()=>null);
 assert.equal(typeof module?.inspectNormalization,'function','缺少迁移只读预检');
 return module.inspectNormalization as (db:Database.Database)=>any;
}
const digest=(file:string)=>createHash('sha256').update(readFileSync(file)).digest('hex');
test('preflight_accepts_consistent_legacy_database_without_writing',async()=>{
 const inspect=await inspector(),fixture=await createNormalizationFixture({through:'011',case:'cross-shop-partial'});
 const before=digest(fixture.databasePath),db=new Database(fixture.databasePath,{readonly:true});
 try{const report=inspect(db);assert.equal(report.canProceed,true,JSON.stringify(report));assert.equal(report.phase,'legacy');assert.equal(report.integrityOk,true);assert.equal(report.foreignKeysOk,true);}finally{db.close();assert.equal(digest(fixture.databasePath),before);await fixture.dispose();}
});

test('preflight_blocks_010_refund_that_cannot_use_historical_full_order_backfill',async()=>{
 const inspect=await inspector(),fixture=await createNormalizationFixture({through:'010',case:'cross-shop-partial'}),db=new Database(fixture.databasePath,{readonly:true});
 try{const report=inspect(db);assert.equal(report.canProceed,false);assert.ok(report.issues.some((i:any)=>i.code==='SALE_TOTAL_MISMATCH'));}finally{db.close();await fixture.dispose();}
});
test('preflight_rejects_inconsistent_ownership_without_writing',async()=>{
 const inspect=await inspector();
 for(const [sql,code] of [["UPDATE orders SET user_id='U2' WHERE id='O1'",'ORDER_BUYER_MISMATCH'],["UPDATE after_sales SET merchant_id='M2' WHERE id='AS1'",'SALE_OWNER_MISMATCH']]){
  const fixture=await createNormalizationFixture({through:'011',case:'cross-shop-partial'}),writer=new Database(fixture.databasePath);writer.exec(sql);writer.close();
  const before=digest(fixture.databasePath),db=new Database(fixture.databasePath,{readonly:true});
  try{const report=inspect(db);assert.equal(report.canProceed,false);assert.ok(report.issues.some((v:any)=>v.code===code));assert.ok(!JSON.stringify(report).includes('13800000000'));}finally{db.close();assert.equal(digest(fixture.databasePath),before);await fixture.dispose();}
 }
});
test('preflight_rejects_bad_json_unknown_address_keys_and_invalid_status',async()=>{
 const inspect=await inspector();
 for(const [sql,code] of [["UPDATE product_revisions SET detail_images_json='not-json' WHERE id='R1'",'INVALID_JSON_ARRAY'],["UPDATE product_revisions SET detail_images_json='[123]' WHERE id='R1'",'INVALID_JSON_ARRAY'],["UPDATE orders SET address_snapshot_json=json_set(address_snapshot_json,'$.unknown','value') WHERE id='O1'",'UNKNOWN_ADDRESS_KEYS'],["UPDATE orders SET status='COMPLETED' WHERE id='O1'",'ORDER_STATUS_MISMATCH']]){
  const fixture=await createNormalizationFixture({through:'011',case:'standard'}),db=new Database(fixture.databasePath);db.exec(sql);
  try{const report=inspect(db);assert.equal(report.canProceed,false);assert.ok(report.issues.some((v:any)=>v.code===code),JSON.stringify(report));}finally{db.close();await fixture.dispose();}
 }
});
test('preflight_checks_amounts_revision_membership_and_foreign_keys',async()=>{
 const inspect=await inspector();
 for(const [sql,code] of [["UPDATE order_items SET subtotal_cents=5999 WHERE id='I1'",'ITEM_SUBTOTAL_MISMATCH'],["UPDATE products SET published_revision_id='R2' WHERE id='P1'",'PUBLISHED_REVISION_MISMATCH'],["PRAGMA foreign_keys=OFF; UPDATE order_items SET product_id='missing' WHERE id='I1'",'FOREIGN_KEY_ERROR']]){
  const fixture=await createNormalizationFixture({through:'011',case:'standard'}),db=new Database(fixture.databasePath);db.exec(sql);
  try{const report=inspect(db);assert.equal(report.canProceed,false);assert.ok(report.issues.some((v:any)=>v.code===code),JSON.stringify(report));}finally{db.close();await fixture.dispose();}
 }
});
