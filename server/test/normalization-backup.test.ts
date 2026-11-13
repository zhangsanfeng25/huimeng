import test from 'node:test';
import assert from 'node:assert/strict';
import Database from 'better-sqlite3';
import {spawnSync} from 'node:child_process';
import {join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {readFileSync,writeFileSync,mkdirSync,symlinkSync,unlinkSync} from 'node:fs';
import {createNormalizationFixture} from './helpers/normalization-fixture.js';
import {captureBusinessBaseline} from '../src/normalization/baseline.js';
import {compareBusinessBaselines} from '../src/normalization/compare.js';
const manager=fileURLToPath(new URL('../../html/launcher/data-manager.mjs',import.meta.url));
async function media(){const name='../src/normalization/media.js';const m=await import(name).catch(()=>null);assert.equal(typeof m?.captureMediaManifest,'function','缺少迁移媒体清单校验');return m.captureMediaManifest as (dir:string)=>Array<{path:string;size:number;sha256:string}>;}
function command(action:string,args:string[],fixture:{databasePath:string;uploadDir:string},backupRoot:string,success=true){
 const result=spawnSync(process.execPath,[manager,action,...args],{cwd:fileURLToPath(new URL('../',import.meta.url)),env:{...process.env,DATABASE_PATH:fixture.databasePath,UPLOAD_DIR:fixture.uploadDir,BACKUP_DIR:backupRoot,PORT:'49197'},encoding:'utf8',windowsHide:true});
 if(success)assert.equal(result.status,0,result.stderr);else assert.notEqual(result.status,0,'损坏备份应拒绝');
 return success?JSON.parse(result.stdout):result.stderr;
}
test('restore_preserves_business_and_uploads',async()=>{
 const captureMedia=await media(),source=await createNormalizationFixture({through:'011',case:'cross-shop-partial'}),destination=await createNormalizationFixture({through:'011',case:'standard'});
 const original=new Database(source.databasePath);original.prepare("INSERT INTO sessions(token_hash,user_id,active_role,expires_at,created_at) VALUES('test-session','U1','USER','2030-01-01','2026-10-24')").run();
 original.prepare("INSERT INTO sync_receipts(user_id,operation_id,client_id,payload_hash,result_json,created_at) VALUES('U1','test-op','device','hash','{\"ok\":true}','2026-10-24')").run();
 const before=captureBusinessBaseline(original,'legacy'),mediaBefore=captureMedia(source.uploadDir);original.close();
 try{
  const backups=join(source.uploadDir,'..','backups'),backup=command('backup',[],source,backups);
  assert.equal(command('validate',['--source',backup.path],source,backups).valid,true);
  const restored=command('restore',['--source',backup.path,'--service-stopped'],destination,join(destination.uploadDir,'..','backups'));
  assert.equal(restored.restored,true);assert.ok(restored.safetyBackup);
  const db=new Database(destination.databasePath,{readonly:true});try{
   const after=captureBusinessBaseline(db,'legacy');assert.equal(before.resources.sessions.count,1);assert.equal(after.resources.sessions.count,0,'恢复工具按原规则撤销会话');
   delete before.resources.sessions;delete after.resources.sessions;
   assert.equal(compareBusinessBaselines(before,after).equal,true);
   assert.deepEqual(captureMedia(destination.uploadDir),mediaBefore);
  }finally{db.close();}
 }finally{await source.dispose();await destination.dispose();}
});
test('tampered_backup_or_missing_media_leaves_destination_unchanged',async()=>{
 await media();const source=await createNormalizationFixture({through:'011',case:'standard'}),destination=await createNormalizationFixture({through:'011',case:'cross-shop-partial'});
 try{
  const db=new Database(destination.databasePath,{readonly:true}),before=captureBusinessBaseline(db,'legacy');db.close();
  const backups=join(source.uploadDir,'..','backups');
  for(const mode of ['database','missing-media','extra-media','traversal','symlink']){
   const backup=command('backup',[],source,backups),manifestPath=join(backup.path,'manifest.json'),manifest=JSON.parse(readFileSync(manifestPath,'utf8'));
   if(mode==='database')writeFileSync(join(backup.path,'database.sqlite'),Buffer.from('broken'));
   if(mode==='missing-media')unlinkSync(join(backup.path,'uploads','a.png'));
   if(mode==='extra-media')writeFileSync(join(backup.path,'uploads','extra.png'),'extra');
   if(mode==='traversal'){manifest.uploads[0].path='../outside.png';writeFileSync(manifestPath,JSON.stringify(manifest));}
   if(mode==='symlink'){const link=join(backup.path,'uploads','linked');symlinkSync(source.uploadDir,link,'junction');}
   command('restore',['--source',backup.path,'--service-stopped'],destination,join(destination.uploadDir,'..','backups'),false);
   const current=new Database(destination.databasePath,{readonly:true});try{assert.equal(compareBusinessBaselines(before,captureBusinessBaseline(current,'legacy')).equal,true);}finally{current.close();}
  }
 }finally{await source.dispose();await destination.dispose();}
});
test('media_manifest_preserves_bytes_and_rejects_links',async()=>{
 const capture=await media(),fixture=await createNormalizationFixture({through:'011',case:'standard'});
 try{
  const rows=capture(fixture.uploadDir);assert.equal(rows.length,1);assert.equal(rows[0].path,'a.png');assert.match(rows[0].sha256,/^[a-f0-9]{64}$/);
  writeFileSync(join(fixture.uploadDir,'a.png'),'changed');assert.notEqual(capture(fixture.uploadDir)[0].sha256,rows[0].sha256);
  const outside=join(fixture.uploadDir,'..','outside');mkdirSync(outside);const link=join(fixture.uploadDir,'link');symlinkSync(outside,link,'junction');assert.throws(()=>capture(fixture.uploadDir),/symbolic|符号链接/);unlinkSync(link);
 }finally{await fixture.dispose();}
});
