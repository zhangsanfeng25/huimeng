import test from 'node:test';
import assert from 'node:assert/strict';
import Database from 'better-sqlite3';
import {spawnSync} from 'node:child_process';
import {join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {readFileSync} from 'node:fs';
import {createNormalizationFixture} from './helpers/normalization-fixture.js';
import {buildNormalizedCopy} from '../src/normalization/build-target.js';
import {captureBusinessBaseline} from '../src/normalization/baseline.js';
import {compareBusinessBaselines} from '../src/normalization/compare.js';
import {captureMediaManifest} from '../src/normalization/media.js';
const manager=fileURLToPath(new URL('../../html/launcher/data-manager.mjs',import.meta.url));
function command(action:string,args:string[],database:string,uploads:string){const result=spawnSync(process.execPath,[manager,action,...args],{env:{...process.env,DATABASE_PATH:database,UPLOAD_DIR:uploads,BACKUP_DIR:join(uploads,'..','backups'),PORT:'49197'},encoding:'utf8',windowsHide:true});assert.equal(result.status,0,result.stderr);return JSON.parse(result.stdout);}

test('normalized_export_includes_partial_items_ordered_relations_and_legacy_fields',async()=>{
 const f=await createNormalizationFixture({through:'011',case:'cross-shop-partial'}),target=join(f.uploadDir,'..','target.db');
 try{
  await buildNormalizedCopy({sourcePath:f.databasePath,targetPath:target,manifestPath:join(f.uploadDir,'..','manifest.json'),uploadDir:f.uploadDir});
  const db=new Database(target);db.prepare("UPDATE community_posts SET title='=1+1' WHERE id='POST1'").run();db.close();
  const exported=command('export',['--output',join(f.uploadDir,'..','exports')],target,f.uploadDir),data=JSON.parse(readFileSync(join(exported.path,'data.json'),'utf8'));
  assert.equal(data.after_sale_items.length,2,'导出遗漏部分售后明细');
  assert.equal(data.orders.find((o:any)=>o.id==='O1').user_id,'U1');assert.equal(data.order_items.find((o:any)=>o.id==='I1').subtotal_cents,6000);
  assert.equal(data.after_sales[0].buyer_id,'U1');assert.equal(data.refunds[0].order_id,'O1');
  assert.deepEqual(data.product_revision_images.filter((r:any)=>r.revision_id==='R1').map((r:any)=>r.url),['/public/a.png','/public/a.png','/public/b.png']);
  assert.equal(data.order_address_snapshots.length,2);assert.equal(data.notification_read_receipts[0].read_at,null);
  assert.equal(data.notifications.find((n:any)=>n.id==='N1').is_read,1);assert.equal(data.notifications.find((n:any)=>n.id==='N1').read_at,null);
  assert.ok(!Object.keys(data.users[0]).some(k=>/password|token|secret/i.test(k)));assert.ok(readFileSync(join(exported.path,'community_posts.csv'),'utf8').includes("'=1+1"));
 }finally{await f.dispose();}
});

test('normalized_full_backup_restores_to_third_database_with_business_and_media_equal',async()=>{
 const f=await createNormalizationFixture({through:'011',case:'cross-shop-partial'}),restored=await createNormalizationFixture({through:'011',case:'standard'}),target=join(f.uploadDir,'..','target.db');
 try{
  await buildNormalizedCopy({sourcePath:f.databasePath,targetPath:target,manifestPath:join(f.uploadDir,'..','manifest.json'),uploadDir:f.uploadDir});
  const db=new Database(target,{readonly:true}),before=captureBusinessBaseline(db,'normalized');db.close();const media=captureMediaManifest(f.uploadDir);
  const backup=command('backup',[],target,f.uploadDir);assert.equal(command('validate',['--source',backup.path],target,f.uploadDir).valid,true);
  assert.equal(command('restore',['--source',backup.path,'--service-stopped'],restored.databasePath,restored.uploadDir).restored,true);
  const after=new Database(restored.databasePath,{readonly:true});try{assert.equal(compareBusinessBaselines(before,captureBusinessBaseline(after,'normalized')).equal,true);assert.deepEqual(after.pragma('foreign_key_check'),[]);}finally{after.close();}
  assert.deepEqual(captureMediaManifest(restored.uploadDir),media);
 }finally{await f.dispose();await restored.dispose();}
});
