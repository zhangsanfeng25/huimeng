import test from 'node:test';
import assert from 'node:assert/strict';
import Database from 'better-sqlite3';
import {createNormalizationFixture} from './helpers/normalization-fixture.js';
async function functions(){const p='../src/normalization/projections.js',r='../src/normalization/relations.js';const views=await import(p).catch(()=>null),relations=await import(r).catch(()=>null);assert.equal(typeof views?.installCompatibilityViews,'function','缺少兼容读模型');assert.equal(typeof relations?.replaceOrderedValues,'function','缺少有序关系维护');return {...views,...relations};}
test('legacy_compatibility_views_preserve_every_original_field',async()=>{
 const {installCompatibilityViews}=await functions(),fixture=await createNormalizationFixture({through:'011',case:'cross-shop-partial'}),db=new Database(fixture.databasePath);
 try{installCompatibilityViews(db,'legacy');for(const name of ['orders','order_items','after_sales','refunds','ratings'])assert.deepEqual(db.prepare('SELECT * FROM compat_'+name).all(),db.prepare('SELECT * FROM '+name).all());}finally{db.close();await fixture.dispose();}
});
test('ordered_relations_preserve_duplicates_and_rollback_invalid_updates',async()=>{
 const {readOrderedValues,replaceOrderedValues}=await functions(),fixture=await createNormalizationFixture({through:'011',case:'standard'}),db=new Database(fixture.databasePath);
 try{
  const before=readOrderedValues(db,'product_revision_images','R1');assert.deepEqual(before,['/public/a.png','/public/a.png','/public/b.png']);
  assert.throws(()=>replaceOrderedValues(db,'unknown','R1',[]));assert.throws(()=>replaceOrderedValues(db,'product_revision_images','R1',['']));assert.deepEqual(readOrderedValues(db,'product_revision_images','R1'),before);
  replaceOrderedValues(db,'product_revision_images','R1',['/public/b.png','/public/a.png','/public/b.png']);assert.deepEqual(readOrderedValues(db,'product_revision_images','R1'),['/public/b.png','/public/a.png','/public/b.png']);
  replaceOrderedValues(db,'product_revision_images','R1',[]);assert.deepEqual(readOrderedValues(db,'product_revision_images','R1'),[]);
 }finally{db.close();await fixture.dispose();}
});
test('normalized_order_projection_preserves_status_amount_and_address_key_presence',async()=>{
 const {installCompatibilityViews,readOrderedValues,replaceOrderedValues}=await functions(),db=new Database(':memory:');
 try{
  db.exec(`CREATE TABLE checkout_groups(id TEXT PRIMARY KEY,user_id TEXT,expires_at TEXT);
   CREATE TABLE orders(id TEXT PRIMARY KEY,checkout_group_id TEXT,merchant_id TEXT,total_cents INTEGER,logistics_status TEXT);
   CREATE TABLE order_address_snapshots(order_id TEXT PRIMARY KEY,field_mask INTEGER,source_address_id TEXT,source_user_id TEXT,recipient TEXT,phone TEXT,region TEXT,detail TEXT,source_is_default INTEGER,source_created_at TEXT,source_updated_at TEXT,source_version INTEGER);
   CREATE TABLE order_items(id TEXT PRIMARY KEY,order_id TEXT,product_id TEXT,unit_price_cents INTEGER,quantity INTEGER);
   CREATE TABLE after_sales(id TEXT PRIMARY KEY,order_id TEXT);
   CREATE TABLE after_sale_requests(after_sale_id TEXT PRIMARY KEY,buyer_id TEXT,client_id TEXT);
   CREATE TABLE after_sale_images(after_sale_id TEXT,position INTEGER,url TEXT);
   CREATE TABLE refunds(id TEXT PRIMARY KEY,after_sale_id TEXT);
   CREATE TABLE ratings(id TEXT PRIMARY KEY,order_item_id TEXT);
   INSERT INTO checkout_groups VALUES('G','U','2030');INSERT INTO orders VALUES('O','G','M',6000,'READY_TO_SHIP');
   INSERT INTO order_address_snapshots(order_id,field_mask,recipient,phone) VALUES('O',12,'历史收货人',NULL);
   INSERT INTO order_items VALUES('I','O','P',3000,2);
   INSERT INTO after_sales VALUES('AS','O');INSERT INTO after_sale_requests VALUES('AS','U','client');
   INSERT INTO refunds VALUES('RF','AS');INSERT INTO ratings VALUES('RATE','I');`);
  installCompatibilityViews(db,'normalized');const order=db.prepare('SELECT * FROM compat_orders').get() as any;
  assert.equal(order.status,'PAID');assert.equal(order.user_id,'U');assert.equal(order.expires_at,'2030');assert.deepEqual(JSON.parse(order.address_snapshot_json),{recipient:'历史收货人',phone:null});
  assert.equal((db.prepare('SELECT * FROM compat_order_items').get() as any).subtotal_cents,6000);
  assert.equal((db.prepare('SELECT * FROM compat_after_sales').get() as any).merchant_id,'M');
  assert.equal((db.prepare('SELECT * FROM compat_refunds').get() as any).order_id,'O');
  assert.equal((db.prepare('SELECT * FROM compat_ratings').get() as any).user_id,'U');
  replaceOrderedValues(db,'after_sale_images','AS',['/a','/b','/a']);assert.deepEqual(readOrderedValues(db,'after_sale_images','AS'),['/a','/b','/a']);
 }finally{db.close();}
});
