import test from 'node:test';
import assert from 'node:assert/strict';
import Database from 'better-sqlite3';
import {join} from 'node:path';
import {existsSync,readFileSync,symlinkSync} from 'node:fs';
import {createHash} from 'node:crypto';
import {createNormalizationFixture} from './helpers/normalization-fixture.js';
import {captureBusinessBaseline} from '../src/normalization/baseline.js';
import {compareBusinessBaselines} from '../src/normalization/compare.js';
async function builder(){const name='../src/normalization/build-target.js';const m=await import(name).catch(()=>null);assert.equal(typeof m?.buildNormalizedCopy,'function','缺少隔离目标库构建器');return m.buildNormalizedCopy as (o:any)=>Promise<any>;}
const hash=(p:string)=>createHash('sha256').update(readFileSync(p)).digest('hex');

test('candidate_builder_rejects_junction_ancestors_before_creating_database',async()=>{
 const build=await builder(),fixture=await createNormalizationFixture({through:'011',case:'standard'}),alias=join(fixture.uploadDir,'..','alias');let created=false;
 try{symlinkSync(fixture.uploadDir,alias,'junction');await assert.rejects(()=>build({sourcePath:fixture.databasePath,targetPath:join(alias,'candidate.db'),manifestPath:join(fixture.uploadDir,'..','manifest.json'),uploadDir:fixture.uploadDir,checkpoint:()=>{created=true;}}));assert.equal(created,false,'应在创建候选结构之前拒绝路径别名');assert.ok(!existsSync(join(fixture.uploadDir,'candidate.db')));}finally{await fixture.dispose();}
});
test('migration_preserves_all_ids_snapshots_and_ordered_duplicates',async()=>{
 const build=await builder(),fixture=await createNormalizationFixture({through:'011',case:'cross-shop-partial'}),targetPath=join(fixture.uploadDir,'..','target.db'),manifestPath=join(fixture.uploadDir,'..','manifest.json');
 const source=new Database(fixture.databasePath,{readonly:true}),before=captureBusinessBaseline(source,'legacy');source.close();const sourceHash=hash(fixture.databasePath);
 try{
  const manifest=await build({sourcePath:fixture.databasePath,targetPath,manifestPath,uploadDir:fixture.uploadDir});assert.equal(manifest.comparison.equal,true);assert.equal(manifest.restoreVerified,false);
  const target=new Database(targetPath,{readonly:true});try{
   assert.equal(compareBusinessBaselines(before,captureBusinessBaseline(target,'normalized')).equal,true);
   assert.deepEqual(target.pragma('foreign_key_check'),[]);assert.equal(target.pragma('integrity_check',{simple:true}),'ok');
   assert.equal((target.prepare('SELECT COUNT(*) n FROM product_revision_images').get() as any).n,6);
   assert.ok(!(target.prepare('PRAGMA table_info(orders)').all() as any[]).some(c=>c.name==='user_id'));
  }finally{target.close();}
  assert.equal(hash(fixture.databasePath),sourceHash);await assert.rejects(build({sourcePath:fixture.databasePath,targetPath,manifestPath,uploadDir:fixture.uploadDir}),/存在|exist/);
 }finally{await fixture.dispose();}
});
test('failed_copy_never_changes_source_or_publishes_manifest',async()=>{
 const build=await builder();
 for(const stage of ['schema','copy','validation','manifest']){
  const fixture=await createNormalizationFixture({through:'011',case:'standard'}),targetPath=join(fixture.uploadDir,'..','target.db'),manifestPath=join(fixture.uploadDir,'..','manifest.json'),before=hash(fixture.databasePath);
  try{await assert.rejects(build({sourcePath:fixture.databasePath,targetPath,manifestPath,uploadDir:fixture.uploadDir,checkpoint:(current:string)=>{if(current===stage)throw Error('injected '+stage);}}),/injected/);assert.equal(hash(fixture.databasePath),before);assert.equal(existsSync(manifestPath),false);assert.equal(existsSync(targetPath),false);}finally{await fixture.dispose();}
 }
});
test('invalid_source_or_same_path_cannot_be_migrated',async()=>{
 const build=await builder(),fixture=await createNormalizationFixture({through:'011',case:'standard'}),manifestPath=join(fixture.uploadDir,'..','manifest.json');
 try{await assert.rejects(build({sourcePath:fixture.databasePath,targetPath:fixture.databasePath,manifestPath,uploadDir:fixture.uploadDir}));const db=new Database(fixture.databasePath);db.exec("UPDATE orders SET total_cents=1 WHERE id='O1'");db.close();await assert.rejects(build({sourcePath:fixture.databasePath,targetPath:join(fixture.uploadDir,'..','target.db'),manifestPath,uploadDir:fixture.uploadDir}),/预检|preflight/);assert.equal(existsSync(manifestPath),false);}finally{await fixture.dispose();}
});
test('deleted_autoincrement_ids_are_not_reused_after_copy',async()=>{
 const build=await builder(),fixture=await createNormalizationFixture({through:'011',case:'standard'}),targetPath=join(fixture.uploadDir,'..','target.db');
 const source=new Database(fixture.databasePath);source.exec("INSERT INTO order_events(id,order_id,actor_id,action,to_status,created_at) VALUES(99,'O1','U1','FIXTURE','PREPARING','2026-10-24');DELETE FROM order_events WHERE id=99;");source.close();
 try{await build({sourcePath:fixture.databasePath,targetPath,manifestPath:join(fixture.uploadDir,'..','manifest.json'),uploadDir:fixture.uploadDir});const target=new Database(targetPath);try{const inserted=target.prepare("INSERT INTO order_events(order_id,actor_id,action,to_status,created_at) VALUES('O1','U1','FIXTURE','PREPARING','2026-10-24')").run();assert.ok(Number(inserted.lastInsertRowid)>99,'不能复用历史已删除事件编号');}finally{target.close();}}finally{await fixture.dispose();}
});
test('old_010_and_011_databases_upgrade_without_reseeding',async()=>{
 const build=await builder();
 for(const through of ['010','011'] as const){const fixture=await createNormalizationFixture({through,case:'standard'}),targetPath=join(fixture.uploadDir,'..','target.db'),before=hash(fixture.databasePath);
  try{await build({sourcePath:fixture.databasePath,targetPath,manifestPath:join(fixture.uploadDir,'..','manifest.json'),uploadDir:fixture.uploadDir});const target=new Database(targetPath,{readonly:true});try{assert.equal((target.prepare('SELECT COUNT(*) n FROM users').get() as any).n,5);assert.equal((target.prepare('SELECT COUNT(*) n FROM products').get() as any).n,2);assert.ok(target.prepare("SELECT 1 FROM schema_migrations WHERE version='011_partial_after_sales'").get());}finally{target.close();}assert.equal(hash(fixture.databasePath),before);}finally{await fixture.dispose();}
 }
});
