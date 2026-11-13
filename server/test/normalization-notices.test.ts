import test from 'node:test';
import assert from 'node:assert/strict';
import Database from 'better-sqlite3';
import {join} from 'node:path';
import {createNormalizationFixture} from './helpers/normalization-fixture.js';
import {buildNormalizedCopy} from '../src/normalization/build-target.js';
import {captureBusinessBaseline} from '../src/normalization/baseline.js';
import {compareBusinessBaselines} from '../src/normalization/compare.js';

test('notification_read_facts_merge_without_inventing_unknown_times',async()=>{
 const f=await createNormalizationFixture({through:'011',case:'legacy-notice'}),source=new Database(f.databasePath),targetPath=join(f.uploadDir,'..','normalized.db');
 try{
  source.exec(`INSERT INTO notifications(id,user_id,audience,title,content,is_read,created_at,read_at) VALUES('N2','U2','指定账号','测试','测试',1,'2026-10-24T01:00:00Z','2026-10-24T01:01:00Z');
   INSERT INTO notifications(id,audience,title,content,created_at) VALUES('N3','全部用户','测试','测试','2026-10-24T01:00:00Z');
   INSERT INTO notification_user_state(notification_id,user_id,read_at) VALUES('N2','U2','2026-10-24T01:02:00Z');
   INSERT INTO notification_reads(notification_id,user_id,read_at) VALUES('N2','U2','2026-10-24T01:03:00Z'),('N3','U2','2026-10-24T01:04:00Z');
   INSERT INTO notification_user_state(notification_id,user_id,deleted_at) VALUES('N3','U1','2026-10-24T01:05:00Z');`);
  const baseline=captureBusinessBaseline(source,'legacy');source.close();
  await buildNormalizedCopy({sourcePath:f.databasePath,targetPath,manifestPath:join(f.uploadDir,'..','manifest.json'),uploadDir:f.uploadDir});
  const target=new Database(targetPath);try{
   assert.ok(!(target.pragma('table_info(notifications)') as any[]).some(c=>['is_read','read_at'].includes(c.name)),'通知还保留重复已读字段');
   assert.ok(!(target.pragma('table_info(notification_user_state)') as any[]).some(c=>c.name==='read_at'));
   assert.deepEqual(target.prepare('SELECT * FROM notification_read_receipts ORDER BY notification_id,user_id').all(),[
    {notification_id:'N1',user_id:'U1',read_at:null},
    {notification_id:'N2',user_id:'U2',read_at:'2026-10-24T01:02:00Z'},
    {notification_id:'N3',user_id:'U2',read_at:'2026-10-24T01:04:00Z'}]);
   assert.equal(compareBusinessBaselines(baseline,captureBusinessBaseline(target,'normalized')).equal,true);
   target.prepare("DELETE FROM notification_read_receipts WHERE notification_id='N1'").run();
   assert.equal(compareBusinessBaselines(baseline,captureBusinessBaseline(target,'normalized')).equal,false,'已读事实丢失必须被发现');
  }finally{target.close();}
 }finally{if(source.open)source.close();await f.dispose();}
});
