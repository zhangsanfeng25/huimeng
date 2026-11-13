import test from 'node:test';
import assert from 'node:assert/strict';
import Database from 'better-sqlite3';
import {join} from 'node:path';
import {createNormalizationFixture} from './helpers/normalization-fixture.js';
import {buildNormalizedCopy} from '../src/normalization/build-target.js';
async function guard(){const path='../src/normalization/schema-guard.js';const m=await import(path).catch(()=>null);assert.equal(typeof m?.assertSupportedSchema,'function','缺少启动结构保护');return m.assertSupportedSchema as (db:Database.Database,options?:{allowLegacyUpgrade?:boolean})=>string;}
test('ordinary_startup_cannot_accept_empty_or_half_normalized_schema',async()=>{
 const check=await guard(),empty=new Database(':memory:');try{assert.throws(()=>check(empty),/维护|初始化/);assert.equal(check(empty,{allowLegacyUpgrade:true}),'empty');}finally{empty.close();}
 const f=await createNormalizationFixture({through:'011',case:'standard'}),targetPath=join(f.uploadDir,'..','candidate.db');
 try{
  let db=new Database(f.databasePath);try{assert.equal(check(db),'legacy');db.exec('CREATE TABLE product_revision_images(revision_id TEXT)');assert.throws(()=>check(db),/维护|未完成/);db.exec('DROP TABLE product_revision_images');}finally{db.close();}
  await buildNormalizedCopy({sourcePath:f.databasePath,targetPath,manifestPath:join(f.uploadDir,'..','manifest.json'),uploadDir:f.uploadDir});
  db=new Database(targetPath);try{assert.equal(check(db),'normalized');db.exec('DROP TABLE notification_read_receipts');assert.throws(()=>check(db),/维护|未完成/);}finally{db.close();}
 }finally{await f.dispose();}
});
test('old_010_requires_explicit_legacy_upgrade_and_preserves_source',async()=>{
 const check=await guard(),f=await createNormalizationFixture({through:'010',case:'standard'}),db=new Database(f.databasePath);
 try{assert.throws(()=>check(db),/维护|升级/);assert.equal(check(db,{allowLegacyUpgrade:true}),'legacy');assert.equal((db.prepare('SELECT COUNT(*) n FROM users').get() as any).n,5);}finally{db.close();await f.dispose();}
});
