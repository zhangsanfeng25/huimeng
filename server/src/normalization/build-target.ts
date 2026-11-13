import Database from 'better-sqlite3';
import {createHash} from 'node:crypto';
import {existsSync,lstatSync,readFileSync,writeFileSync,unlinkSync,mkdirSync} from 'node:fs';
import {dirname,resolve} from 'node:path';
import {inspectNormalization} from './preflight.js';
import {captureBusinessBaseline,legacySaleItemRows} from './baseline.js';
import {compareBusinessBaselines} from './compare.js';
import {captureMediaManifest} from './media.js';
import {addressFields,installCompatibilityViews} from './projections.js';
import {replaceOrderedValues} from './relations.js';
import {notificationReadFacts} from './notice-facts.js';
import {codeFingerprint,projectRoot} from './fingerprint.js';
import {verifiedBuildCode} from './build-artifacts.js';
import {assertNoSymbolicAncestors} from './paths.js';
import type {MigrationManifest,OrderedRelation} from './types.js';
type Options={sourcePath:string;targetPath:string;manifestPath:string;uploadDir:string;checkpoint?:(stage:'schema'|'copy'|'validation'|'manifest')=>void};
const digest=(path:string)=>createHash('sha256').update(readFileSync(path)).digest('hex');
const quote=(name:string)=>'"'+name.replaceAll('"','""')+'"';
const removed:Record<string,string[]>={orders:['user_id','expires_at','status','address_snapshot_json'],order_items:['subtotal_cents'],after_sales:['buyer_id','merchant_id','client_id','images_json'],refunds:['order_id'],ratings:['user_id','product_id'],product_revisions:['detail_images_json','contents_json'],community_posts:['images_json'],notifications:['is_read','read_at'],notification_user_state:['read_at']};
export async function buildNormalizedCopy(options:Options):Promise<MigrationManifest>{
 const sourceCode=codeFingerprint();
 let buildCode:string|null=null;try{buildCode=verifiedBuildCode(projectRoot(),sourceCode);}catch{/* An isolated data rehearsal is allowed; cutover requires a verified build. */}
 const sourcePath=resolve(options.sourcePath),targetPath=resolve(options.targetPath),manifestPath=resolve(options.manifestPath);
 if(!options.uploadDir)throw Error('必须显式指定媒体目录');
 for(const path of [sourcePath,targetPath,manifestPath,options.uploadDir])assertNoSymbolicAncestors(path);
 if(sourcePath===targetPath||sourcePath===manifestPath||targetPath===manifestPath)throw Error('源、目标与清单路径必须不同');
 if(existsSync(targetPath)||existsSync(manifestPath))throw Error('目标或清单已存在，拒绝覆盖');
 if(lstatSync(sourcePath).isSymbolicLink())throw Error('源数据库不能是符号链接');
 const sourceDigest=digest(sourcePath),source=new Database(sourcePath,{readonly:true,fileMustExist:true});
 let target:Database.Database|undefined,createdTarget=false,createdManifest=false;
 try{
  const snapshot=source.transaction(()=>{
   const preflight=inspectNormalization(source);if(!preflight.canProceed||preflight.phase!=='legacy')throw Error('源库预检未通过或不是可迁移旧结构');
   const baseline=captureBusinessBaseline(source,'legacy');
   const tables=source.prepare("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name").all() as Array<{name:string}>;
   const rows=Object.fromEntries(tables.map(t=>[t.name,source.prepare(`SELECT * FROM ${quote(t.name)}`).all() as Array<Record<string,any>>]));
   if(!rows.after_sale_items)rows.after_sale_items=legacySaleItemRows(source);
   return {preflight,baseline,rows,noticeFacts:notificationReadFacts(source,'legacy')};
  })();
  const media=captureMediaManifest(options.uploadDir);
  mkdirSync(dirname(targetPath),{recursive:true});target=new Database(targetPath);createdTarget=true;target.pragma('foreign_keys=ON');
  const schema=readFileSync(new URL('./target-schema.sql',import.meta.url),'utf8');
  const destination=target;
  const comparison=destination.transaction(()=>{
   destination.exec(schema);destination.pragma('defer_foreign_keys=ON');options.checkpoint?.('schema');
   for(const [table,rows] of Object.entries(snapshot.rows)){
    const columns=(destination.prepare(`PRAGMA table_info(${quote(table)})`).all() as Array<{name:string}>).map(c=>c.name);
    if(!columns.length)throw Error('目标缺少源表 '+table);
    for(const row of rows){
     const keys=Object.keys(row).filter(k=>columns.includes(k));
     if(Object.keys(row).some(k=>!columns.includes(k)&&!(removed[table]||[]).includes(k)))throw Error('发现未映射字段 '+table);
     destination.prepare(`INSERT INTO ${quote(table)}(${keys.map(quote).join(',')}) VALUES(${keys.map(()=>'?').join(',')})`).run(...keys.map(k=>row[k]));
    }
   }
   for(const row of snapshot.noticeFacts)destination.prepare('INSERT INTO notification_read_receipts(notification_id,user_id,read_at) VALUES(?,?,?)').run(row.notification_id,row.user_id,row.read_at);
   for(const row of snapshot.rows.orders){
    const address=JSON.parse(row.address_snapshot_json);let mask=0;const values=addressFields.map(([key],i)=>{if(Object.hasOwn(address,key)){mask|=2**i;return address[key];}return null;});
    destination.prepare(`INSERT INTO order_address_snapshots(order_id,field_mask,${addressFields.map(([,field])=>field).join(',')}) VALUES(?,?,${values.map(()=>'?').join(',')})`).run(row.id,mask,...values);
   }
   for(const row of snapshot.rows.after_sales)destination.prepare('INSERT INTO after_sale_requests(buyer_id,client_id,after_sale_id) VALUES(?,?,?)').run(row.buyer_id,row.client_id,row.id);
   for(const [table,field,relation] of [['product_revisions','detail_images_json','product_revision_images'],['product_revisions','contents_json','product_revision_contents'],['community_posts','images_json','community_post_images'],['after_sales','images_json','after_sale_images']])for(const row of snapshot.rows[table])replaceOrderedValues(destination,relation as OrderedRelation,row.id,JSON.parse(row[field]));
   for(const [table,seq] of Object.entries(snapshot.baseline.counters)){
    const current=destination.prepare('SELECT seq FROM sqlite_sequence WHERE name=?').get(table) as {seq:number}|undefined;
    if(current)destination.prepare('UPDATE sqlite_sequence SET seq=? WHERE name=?').run(Math.max(current.seq,seq),table);
    else destination.prepare('INSERT INTO sqlite_sequence(name,seq) VALUES(?,?)').run(table,seq);
   }
   destination.prepare("INSERT OR IGNORE INTO schema_migrations(version,applied_at) VALUES('011_partial_after_sales',?)").run(new Date().toISOString());
   options.checkpoint?.('copy');installCompatibilityViews(destination,'normalized');
   if(destination.pragma('integrity_check',{simple:true})!=='ok'||(destination.pragma('foreign_key_check') as unknown[]).length)throw Error('目标完整性或外键检查失败');
   const compared=compareBusinessBaselines(snapshot.baseline,captureBusinessBaseline(destination,'normalized'));
   if(!compared.equal)throw Error('新旧业务基线不一致：'+JSON.stringify(compared.differences));
   options.checkpoint?.('validation');return compared;
  })();
  destination.close();target=undefined;
  if(digest(sourcePath)!==sourceDigest||!compareBusinessBaselines(snapshot.baseline,source.transaction(()=>captureBusinessBaseline(source,'legacy'))()).equal)throw Error('演练期间源数据库发生变化');
  if(JSON.stringify(media)!==JSON.stringify(captureMediaManifest(options.uploadDir)))throw Error('演练期间媒体发生变化');
  if(sourceCode!==codeFingerprint())throw Error('演练期间代码发生变化');
  const manifest:MigrationManifest={format:'huimeng-normalization-v1',sourcePhase:'legacy',targetPhase:'normalized',sourceDigest,targetDigest:digest(targetPath),mediaDigest:createHash('sha256').update(JSON.stringify(media)).digest('hex'),sourceCode,buildCode,preflight:snapshot.preflight,comparison,restoreVerified:false};
  options.checkpoint?.('manifest');mkdirSync(dirname(manifestPath),{recursive:true});writeFileSync(manifestPath,JSON.stringify(manifest,null,2),{flag:'wx'});createdManifest=true;
  return manifest;
 }catch(error){
  target?.close();target=undefined;
  if(createdManifest)unlinkSync(manifestPath);
  if(createdTarget)for(const suffix of ['','-journal','-wal','-shm'])if(existsSync(targetPath+suffix))unlinkSync(targetPath+suffix);
  throw error;
 }finally{source.close();}
}
