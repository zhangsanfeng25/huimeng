import test from 'node:test';
import assert from 'node:assert/strict';
import Database from 'better-sqlite3';
import {createNormalizationFixture} from './helpers/normalization-fixture.js';

async function functions(){
 const baselineName='../src/normalization/baseline.js',compareName='../src/normalization/compare.js';
 const baseline=await import(baselineName).catch(()=>null),compare=await import(compareName).catch(()=>null);
 assert.equal(typeof baseline?.captureBusinessBaseline,'function','缺少业务数据保留基线');
 assert.equal(typeof compare?.compareBusinessBaselines,'function','缺少新旧业务数据对照');
 return {capture:baseline.captureBusinessBaseline,compare:compare.compareBusinessBaselines};
}
test('baseline_detects_changed_money_identity_event_and_image_order',async()=>{
 const {capture,compare}=await functions(),fixture=await createNormalizationFixture({through:'011',case:'cross-shop-partial'}),db=new Database(fixture.databasePath);
 try{
  const before=capture(db,'legacy');assert.equal(compare(before,capture(db,'legacy')).equal,true);
  for(const sql of ["UPDATE order_items SET unit_price_cents=unit_price_cents+1 WHERE id='I1'","UPDATE orders SET id='changed-order' WHERE id='O1'","UPDATE order_events SET note='changed' WHERE order_id='O1'","UPDATE product_revisions SET detail_images_json='[\"/public/b.png\",\"/public/a.png\",\"/public/a.png\"]' WHERE id='R1'","DELETE FROM after_sale_items WHERE after_sale_id='AS1'"]){
   // Keep FK enforcement enabled; the deliberately broken identity exists only
   // inside a deferred savepoint that is rolled back before constraints are checked.
   db.exec('SAVEPOINT mutate; PRAGMA defer_foreign_keys=ON');db.exec(sql);assert.equal(compare(before,capture(db,'legacy')).equal,false,sql);db.exec('ROLLBACK TO mutate; RELEASE mutate');
  }
  assert.ok(!JSON.stringify(before).includes('13800000000'));assert.ok(!JSON.stringify(before).includes('fixture-hash'));
 }finally{db.close();await fixture.dispose();}
});
test('baseline_canonicalizes_only_known_json_and_preserves_array_order',async()=>{
 const {capture,compare}=await functions(),fixture=await createNormalizationFixture({through:'011',case:'standard'}),db=new Database(fixture.databasePath);
 try{const before=capture(db,'legacy');db.exec("UPDATE product_revisions SET detail_images_json='[ \"/public/a.png\", \"/public/a.png\", \"/public/b.png\" ]' WHERE id='R1'");assert.equal(compare(before,capture(db,'legacy')).equal,true);db.exec("UPDATE community_posts SET content=' 测试正文' WHERE id='POST1'");assert.equal(compare(before,capture(db,'legacy')).equal,false);}finally{db.close();await fixture.dispose();}
});
