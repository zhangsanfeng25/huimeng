import test from 'node:test';
import assert from 'node:assert/strict';
import Database from 'better-sqlite3';
import {createHash} from 'node:crypto';
import {createServer} from 'node:net';
import {spawnSync} from 'node:child_process';
import {cpSync,readFileSync,writeFileSync} from 'node:fs';
import {dirname,join} from 'node:path';
import {createNormalizationFixture} from './helpers/normalization-fixture.js';
import {buildNormalizedCopy} from '../src/normalization/build-target.js';
const digest=(path:string)=>createHash('sha256').update(readFileSync(path)).digest('hex');
async function api(){const path='../src/normalization/cutover.js';const module=await import(path).catch(()=>null);assert.equal(typeof module?.cutoverNormalizedDatabase,'function','缺少受控维护切换入口');assert.equal(typeof module?.recoverInterruptedSwitch,'function','缺少中断恢复入口');return module;}
async function setup(){
 const f=await createNormalizationFixture({through:'011',case:'cross-shop-partial'}),root=dirname(f.databasePath),targetPath=join(root,'target.db'),manifestPath=join(root,'candidate.json');
 const manifest=await buildNormalizedCopy({sourcePath:f.databasePath,targetPath,manifestPath,uploadDir:f.uploadDir});
 for(const [database,media,from] of [['restored-source.db','restored-source-media',f.databasePath],['restored-target.db','restored-target-media',targetPath]]){cpSync(from,join(root,database));cpSync(f.uploadDir,join(root,media),{recursive:true});}
 const artifact=join(root,'reviewed-evidence.log');writeFileSync(artifact,'isolated fixture verification evidence');
 const gates=Object.fromEntries(['backupRestore','businessData','httpRoles','legacySync','browser','android','build','failureRecovery'].map(name=>[name,{result:'passed',path:artifact,sha256:digest(artifact)}]));
 const assurance={format:'huimeng-normalization-assurance-v1',sourceDigest:manifest.sourceDigest,targetDigest:manifest.targetDigest,sourceCode:manifest.sourceCode,buildCode:manifest.buildCode,mediaDigest:manifest.mediaDigest,gates,sourceRestore:{databasePath:join(root,'restored-source.db'),uploadDir:join(root,'restored-source-media')},targetRestore:{databasePath:join(root,'restored-target.db'),uploadDir:join(root,'restored-target-media')}};
 const assurancePath=join(root,'assurance.json');writeFileSync(assurancePath,JSON.stringify(assurance));
 const portGuard=createServer();await new Promise<void>(resolve=>portGuard.listen(0,'127.0.0.1',resolve));const servicePort=(portGuard.address() as {port:number}).port;await new Promise<void>(resolve=>portGuard.close(()=>resolve()));
 return {f,root,targetPath,manifestPath,assurance,assurancePath,options:{workspaceRoot:root,sourcePath:f.databasePath,targetPath,manifestPath,uploadDir:f.uploadDir,assurancePath,servicePort},dispose:()=>f.dispose()};
}

test('stale_manifest_or_running_service_blocks_cutover_without_moving_source',async()=>{
 const {cutoverNormalizedDatabase}=await api();
 for(const mode of ['missing-assurance','source','candidate','media','code','failed-gate','tampered-evidence','service','path']){
  const h=await setup(),before=digest(h.f.databasePath);let server:ReturnType<typeof createServer>|undefined;
  try{
   if(mode==='missing-assurance')h.options.assurancePath=join(h.root,'missing.json');
   if(mode==='source'){const db=new Database(h.f.databasePath);db.prepare("UPDATE orders SET updated_at='changed'").run();db.close();}
   if(mode==='candidate'){const db=new Database(h.targetPath);db.prepare('UPDATE orders SET total_cents=total_cents+1').run();db.close();}
   if(mode==='media')writeFileSync(join(h.f.uploadDir,'a.png'),'changed');
   if(mode==='code'){h.assurance.sourceCode='stale';writeFileSync(h.assurancePath,JSON.stringify(h.assurance));}
   if(mode==='failed-gate'){h.assurance.gates.android.result='pending';writeFileSync(h.assurancePath,JSON.stringify(h.assurance));}
   if(mode==='tampered-evidence')writeFileSync(join(h.root,'reviewed-evidence.log'),'changed');
   if(mode==='service'){server=createServer();await new Promise<void>(resolve=>server!.listen(0,'127.0.0.1',resolve));h.options.servicePort=(server.address() as {port:number}).port;}
   if(mode==='path')h.options.targetPath=join(h.root,'..','outside.db');
   const afterSetup=digest(h.f.databasePath);await assert.rejects(()=>cutoverNormalizedDatabase(h.options));assert.equal(digest(h.f.databasePath),afterSetup);
   if(mode!=='source')assert.equal(afterSetup,before);
  }finally{if(server)await new Promise<void>(resolve=>server!.close(()=>resolve()));await h.dispose();}
 }
});

test('interrupted_switch_rolls_back_and_success_retains_old_database',async()=>{
 const {cutoverNormalizedDatabase}=await api();
 for(const stage of ['staged','retained','installed','validated','success']){
  const h=await setup(),before=digest(h.f.databasePath);
  try{
   if(stage==='success'){
    const result=await cutoverNormalizedDatabase(h.options);assert.equal(result.completed,true);assert.equal(result.readiness.health,true);assert.equal(result.readiness.writeBlocked,true);assert.ok(result.readiness.staticAssets>0);assert.equal(digest(result.retainedSource),before);
    const db=new Database(h.f.databasePath,{readonly:true});try{assert.ok(!(db.pragma('table_info(orders)') as any[]).some(c=>c.name==='user_id'));assert.deepEqual(db.pragma('foreign_key_check'),[]);}finally{db.close();}
    await assert.rejects(()=>cutoverNormalizedDatabase(h.options));
   }else{await assert.rejects(()=>cutoverNormalizedDatabase({...h.options,checkpoint:(value:string)=>{if(value===stage)throw Error('injected interruption');}}),/injected interruption/);assert.equal(digest(h.f.databasePath),before);}
  }finally{await h.dispose();}
 }
});

test('hard_process_exit_blocks_startup_then_recovers_each_switch_stage',async()=>{
 const {recoverInterruptedSwitch}=await api();
 for(const stage of ['staged','retained','installed','validated']){
  const h=await setup(),before=digest(h.f.databasePath);
  try{
   const script=`import {cutoverNormalizedDatabase} from './src/normalization/cutover.ts';await cutoverNormalizedDatabase({...JSON.parse(process.argv[1]),checkpoint:value=>{if(value===${JSON.stringify(stage)})process.exit(92);}});`;
   const result=spawnSync(process.execPath,['--import','tsx','--input-type=module','-e',script,JSON.stringify(h.options)],{encoding:'utf8',windowsHide:true});assert.equal(result.status,92,result.stderr);
   const startup=spawnSync(process.execPath,['--import','tsx','--input-type=module','-e',"await import('./src/core/db.ts');"],{env:{...process.env,DATABASE_PATH:h.f.databasePath},encoding:'utf8',windowsHide:true});
   assert.notEqual(startup.status,0,'未完成维护时日常启动必须在打开数据库前停止');assert.match(startup.stderr,/未完成|维护/);
   assert.equal(recoverInterruptedSwitch({workspaceRoot:h.root,sourcePath:h.f.databasePath}).restored,true);assert.equal(digest(h.f.databasePath),before);
  }finally{await h.dispose();}
 }
});

test('installed_service_must_pass_readonly_health_before_activation',async()=>{
 const {cutoverNormalizedDatabase}=await api(),h=await setup(),before=digest(h.f.databasePath);let server:ReturnType<typeof createServer>|undefined;const sockets=new Set<import('node:net').Socket>();
 try{
  await assert.rejects(()=>cutoverNormalizedDatabase({...h.options,checkpoint:(stage:string)=>{if(stage==='installed'){server=createServer(socket=>{sockets.add(socket);socket.once('close',()=>sockets.delete(socket));socket.end('HTTP/1.1 503 Service Unavailable\r\nConnection: close\r\nContent-Length: 0\r\n\r\n');});server.listen(h.options.servicePort,'127.0.0.1');}}}));
  assert.equal(digest(h.f.databasePath),before,'实际服务验证失败必须保持可恢复且回到旧库');
 }finally{for(const socket of sockets)socket.destroy();if(server)await new Promise<void>(resolve=>server!.close(()=>resolve()));await h.dispose();}
});

test('concurrent_cutover_cannot_replace_completed_state_or_lose_rollback',async()=>{
 const {cutoverNormalizedDatabase}=await api(),h=await setup();
 try{
  const results=await Promise.allSettled([cutoverNormalizedDatabase(h.options),cutoverNormalizedDatabase(h.options)]);
  assert.equal(results.filter(r=>r.status==='fulfilled').length,1);
  assert.equal(JSON.parse(readFileSync(h.f.databasePath+'.normalization-switch.json','utf8')).stage,'COMPLETE');
  const db=new Database(h.f.databasePath,{readonly:true});try{assert.deepEqual(db.pragma('foreign_key_check'),[]);}finally{db.close();}
 }finally{await h.dispose();}
});
