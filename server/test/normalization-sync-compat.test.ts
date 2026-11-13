import test from 'node:test';
import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {createNormalizationFixture} from './helpers/normalization-fixture.js';
import {buildNormalizedCopy} from '../src/normalization/build-target.js';
for(const mode of ['replay','snapshots','orders'])test('old_android_sync_contract_after_normalization_'+mode,async()=>{
 const f=await createNormalizationFixture({through:'011',case:'standard'}),statePath=join(f.uploadDir,'..','old-queue.json'),targetPath=join(f.uploadDir,'..','target.db');
 const run=(database:string,stage:string)=>{try{execFileSync(process.execPath,['--import','tsx',fileURLToPath(new URL('./helpers/normalization-sync-worker.ts',import.meta.url)),stage,statePath],{env:{...process.env,DATABASE_PATH:database,UPLOAD_DIR:f.uploadDir},windowsHide:true,stdio:'pipe'});}catch(error:any){assert.fail(error.stderr?.toString()||error.message);}};
 try{
  run(f.databasePath,'prepare');
  await buildNormalizedCopy({sourcePath:f.databasePath,targetPath,manifestPath:join(f.uploadDir,'..','manifest.json'),uploadDir:f.uploadDir});
  run(targetPath,mode);
 }finally{await f.dispose();}
});
