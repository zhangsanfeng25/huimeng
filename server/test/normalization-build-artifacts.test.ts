import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdirSync,writeFileSync,unlinkSync} from 'node:fs';
import {join} from 'node:path';
import {createNormalizationFixture} from './helpers/normalization-fixture.js';
test('verified_build_detects_stale_source_modified_service_and_missing_static_asset',async()=>{
 const modulePath='../src/normalization/build-artifacts.js',m=await import(modulePath).catch(()=>null);assert.equal(typeof m?.stampBuild,'function','缺少实际构建产物绑定');assert.equal(typeof m?.verifiedBuildCode,'function');
 const f=await createNormalizationFixture({through:'011',case:'standard'}),root=join(f.uploadDir,'..','build');
 try{
  mkdirSync(join(root,'server/dist/scripts'),{recursive:true});mkdirSync(join(root,'html/dist/assets'),{recursive:true});writeFileSync(join(root,'server/dist/scripts/normalize.js'),'verified maintenance');
  const service=join(root,'server/dist/main.js'),asset=join(root,'html/dist/assets/main.js');writeFileSync(service,'verified service');writeFileSync(asset,'verified javascript');writeFileSync(join(root,'html/dist/index.html'),'<div id="root"></div>');
  m.stampBuild(root,'source-v1');const before=m.verifiedBuildCode(root,'source-v1');assert.match(before,/^[a-f0-9]{64}$/);
  assert.throws(()=>m.verifiedBuildCode(root,'source-v2'),/构建|源码/);
  writeFileSync(service,'changed service');assert.throws(()=>m.verifiedBuildCode(root,'source-v1'),/构建|产物/);
  writeFileSync(service,'verified service');assert.equal(m.verifiedBuildCode(root,'source-v1'),before);
  unlinkSync(asset);assert.throws(()=>m.verifiedBuildCode(root,'source-v1'),/构建|产物/);
 }finally{await f.dispose();}
});
