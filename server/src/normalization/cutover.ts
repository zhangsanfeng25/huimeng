import Database from 'better-sqlite3';
import {createHash,randomUUID} from 'node:crypto';
import {copyFileSync,existsSync,readFileSync,renameSync,unlinkSync,writeFileSync,constants} from 'node:fs';
import {createConnection} from 'node:net';
import {resolve} from 'node:path';
import {containedPath} from './paths.js';
import {codeFingerprint,projectRoot} from './fingerprint.js';
import {verifiedBuildCode} from './build-artifacts.js';
import {probeInstalledService,type ServiceReadiness} from './service-probe.js';
import {captureBusinessBaseline} from './baseline.js';
import {compareBusinessBaselines} from './compare.js';
import {captureMediaManifest} from './media.js';
import {inspectNormalization} from './preflight.js';
import {assertSupportedSchema} from './schema-guard.js';
import {assertNoInterruptedSwitch} from './switch-state.js';
import type {BusinessBaseline,MigrationManifest,SchemaPhase} from './types.js';
const digest=(path:string)=>createHash('sha256').update(readFileSync(path)).digest('hex');
const mediaDigest=(path:string)=>createHash('sha256').update(JSON.stringify(captureMediaManifest(path))).digest('hex');
const stages=['PREPARED','RETAINING','INSTALLING','VALIDATING','COMPLETE','ROLLED_BACK'] as const;
type Journal={format:'huimeng-normalization-switch-v1';sourcePath:string;retainedSource:string;stagedCandidate:string;sourceDigest:string;targetDigest:string;stage:typeof stages[number];probeToken:string;readiness?:ServiceReadiness};
type Options={workspaceRoot:string;sourcePath:string;targetPath:string;manifestPath:string;uploadDir:string;assurancePath:string;servicePort:number;checkpoint?:(stage:'staged'|'retained'|'installed'|'validated')=>void};
function writeJournal(path:string,value:Journal){const temporary=path+'.'+randomUUID()+'.tmp';try{writeFileSync(temporary,JSON.stringify(value,null,2),{flag:'wx'});renameSync(temporary,path);}finally{if(existsSync(temporary))unlinkSync(temporary);}}
function maintenanceLock(root:string,sourcePath:string):()=>void{
 const path=containedPath(root,sourcePath+'.normalization-lock.sqlite'),lock=new Database(path);
 try{lock.pragma('busy_timeout=0');lock.exec('BEGIN EXCLUSIVE');}catch{lock.close();throw Error('已有数据库维护正在进行，拒绝并发切换或恢复');}
 return ()=>{try{lock.exec('ROLLBACK');}finally{lock.close();}};
}
function baseline(path:string,phase:SchemaPhase){const db=new Database(path,{readonly:true,fileMustExist:true});try{const report=inspectNormalization(db);if(!report.canProceed||report.phase!==phase)throw Error('维护库预检失败');return captureBusinessBaseline(db,phase);}finally{db.close();}}
function restoredMatches(before:BusinessBaseline,path:string,phase:SchemaPhase){const restored=baseline(path,phase);if(restored.resources.sessions.count!==0)throw Error('恢复演练未撤销会话');const original=structuredClone(before);delete original.resources.sessions;delete restored.resources.sessions;if(!compareBusinessBaselines(original,restored).equal)throw Error('恢复演练业务基线不一致');}
async function serviceRunning(port:number){if(!Number.isInteger(port)||port<1||port>65535)throw Error('必须指定真实服务端口');return new Promise<boolean>(resolveResult=>{const socket=createConnection({host:'127.0.0.1',port});let done=false;const finish=(result:boolean)=>{if(done)return;done=true;socket.destroy();resolveResult(result);};socket.setTimeout(1000);socket.once('connect',()=>finish(true));socket.once('timeout',()=>finish(true));socket.once('error',(error:NodeJS.ErrnoException)=>{if(error.code==='ECONNREFUSED')finish(false);else finish(true);});});}
function recoverUnlocked(options:{workspaceRoot:string;sourcePath:string}){
 const sourcePath=containedPath(options.workspaceRoot,options.sourcePath),journalPath=containedPath(options.workspaceRoot,sourcePath+'.normalization-switch.json');
 if(!existsSync(journalPath))throw Error('没有可恢复的维护状态');
 const journal=JSON.parse(readFileSync(journalPath,'utf8')) as Journal;
 if(journal.format!=='huimeng-normalization-switch-v1'||journal.sourcePath!==sourcePath||!stages.includes(journal.stage)||!/^[a-f0-9]{64}$/.test(journal.sourceDigest)||!/^[a-f0-9]{64}$/.test(journal.targetDigest))throw Error('维护状态不合法');
 containedPath(options.workspaceRoot,journal.retainedSource);containedPath(options.workspaceRoot,journal.stagedCandidate);
 const token=journal.retainedSource.slice((sourcePath+'.legacy-').length);
 if(!journal.retainedSource.startsWith(sourcePath+'.legacy-')||!(/^[a-f0-9-]{36}$/).test(token)||journal.stagedCandidate!==sourcePath+'.candidate-'+token)throw Error('维护状态不是此数据库创建的文件');
 if(new Set([sourcePath,journal.retainedSource,journal.stagedCandidate,journalPath]).size!==4)throw Error('维护状态路径重叠');
 if(journal.stage==='COMPLETE')throw Error('切换已开放使用，不能自动回退而丢失新数据');
 if(journal.stage==='ROLLED_BACK')return {restored:true};
 if(existsSync(journal.retainedSource)){
  if(digest(journal.retainedSource)!==journal.sourceDigest)throw Error('保留旧库已变化，请人工维护');
  if(existsSync(sourcePath)){
   const current=digest(sourcePath);if(current!==journal.targetDigest&&current!==journal.sourceDigest)throw Error('安装库已有新写入，禁止自动覆盖');
   if(current===journal.targetDigest)unlinkSync(sourcePath);
  }
  if(!existsSync(sourcePath))renameSync(journal.retainedSource,sourcePath);
 }else if(!existsSync(sourcePath)||digest(sourcePath)!==journal.sourceDigest)throw Error('无法定位一致的旧库，请人工维护');
 if(existsSync(journal.stagedCandidate)){if(digest(journal.stagedCandidate)!==journal.targetDigest)throw Error('暂存库已变化，拒绝删除');unlinkSync(journal.stagedCandidate);}
 journal.stage='ROLLED_BACK';writeJournal(journalPath,journal);return {restored:true};
}
export function recoverInterruptedSwitch(options:{workspaceRoot:string;sourcePath:string}){
 const sourcePath=containedPath(options.workspaceRoot,options.sourcePath),release=maintenanceLock(options.workspaceRoot,sourcePath);
 try{return recoverUnlocked({...options,sourcePath});}finally{release();}
}
/** The assurance file is a reviewed evidence index, not a boolean bypass. */
async function cutoverUnlocked(options:Options){
 const root=resolve(options.workspaceRoot),sourcePath=containedPath(root,options.sourcePath),targetPath=containedPath(root,options.targetPath),manifestPath=containedPath(root,options.manifestPath),uploadDir=containedPath(root,options.uploadDir),assurancePath=containedPath(root,options.assurancePath);
 if(new Set([sourcePath,targetPath,manifestPath,assurancePath]).size!==4)throw Error('维护输入路径重叠');
 assertNoInterruptedSwitch(sourcePath);
 const journalPath=containedPath(root,sourcePath+'.normalization-switch.json');
 if(existsSync(journalPath)&&(JSON.parse(readFileSync(journalPath,'utf8')) as Journal).stage==='COMPLETE')throw Error('此数据库已经完成切换');
 if(await serviceRunning(options.servicePort))throw Error('控制服务仍在运行，请停止所有写入');
 for(const database of [sourcePath,targetPath])for(const suffix of ['-wal','-shm','-journal'])if(existsSync(database+suffix))throw Error('数据库仍有连接或未完成 checkpoint，请先关闭并重新演练');
 const manifest=JSON.parse(readFileSync(manifestPath,'utf8')) as MigrationManifest,assurance=JSON.parse(readFileSync(assurancePath,'utf8'));
 if(manifest.format!=='huimeng-normalization-v1'||manifest.sourcePhase!=='legacy'||manifest.targetPhase!=='normalized'||manifest.comparison?.equal!==true||manifest.preflight?.canProceed!==true)throw Error('迁移清单未通过');
 if(digest(sourcePath)!==manifest.sourceDigest||digest(targetPath)!==manifest.targetDigest||mediaDigest(uploadDir)!==manifest.mediaDigest||codeFingerprint()!==manifest.sourceCode)throw Error('源库、候选库、媒体或代码已变化，请重新演练');
 if(!manifest.buildCode||verifiedBuildCode(projectRoot(),manifest.sourceCode)!==manifest.buildCode)throw Error('实际构建未绑定当前源码与候选，请重新完整构建并验收');
 if(assurance.format!=='huimeng-normalization-assurance-v1'||['sourceDigest','targetDigest','sourceCode','buildCode','mediaDigest'].some(key=>assurance[key]!==manifest[key as keyof MigrationManifest]))throw Error('验收证据与候选版本不一致');
 for(const name of ['backupRestore','businessData','httpRoles','legacySync','browser','android','build','failureRecovery']){const gate=assurance.gates?.[name];if(gate?.result!=='passed'||digest(containedPath(root,gate.path))!==gate.sha256)throw Error('缺少或过期的验收证据：'+name);}
 const source=baseline(sourcePath,'legacy'),target=baseline(targetPath,'normalized');
 if(!compareBusinessBaselines(source,target).equal)throw Error('最终新旧业务基线不同');
 const restorePaths=new Set([sourcePath,targetPath]);
 for(const [name,phase,value] of [['sourceRestore','legacy',source],['targetRestore','normalized',target]] as const){
  const restore=assurance[name],database=containedPath(root,restore.databasePath),media=containedPath(root,restore.uploadDir);
  if(restorePaths.has(database)||media===uploadDir)throw Error('恢复演练必须使用独立副本');restorePaths.add(database);
  restoredMatches(value,database,phase);if(mediaDigest(media)!==manifest.mediaDigest)throw Error('恢复演练媒体不一致');
 }
 const lock=new Database(sourcePath,{fileMustExist:true});try{lock.pragma('busy_timeout=1000');lock.exec('BEGIN EXCLUSIVE; COMMIT;');}finally{lock.close();}
 if(await serviceRunning(options.servicePort))throw Error('停写后服务状态发生变化');
 assertNoInterruptedSwitch(sourcePath);
 if(digest(sourcePath)!==manifest.sourceDigest)throw Error('停写后源库发生变化');
 const token=randomUUID(),journal:Journal={format:'huimeng-normalization-switch-v1',sourcePath,retainedSource:sourcePath+'.legacy-'+token,stagedCandidate:sourcePath+'.candidate-'+token,sourceDigest:manifest.sourceDigest,targetDigest:manifest.targetDigest,stage:'PREPARED',probeToken:randomUUID()};
 writeJournal(journalPath,journal);
 try{
  copyFileSync(targetPath,journal.stagedCandidate,constants.COPYFILE_EXCL);if(digest(journal.stagedCandidate)!==manifest.targetDigest)throw Error('暂存库校验失败');options.checkpoint?.('staged');
  journal.stage='RETAINING';writeJournal(journalPath,journal);renameSync(sourcePath,journal.retainedSource);options.checkpoint?.('retained');
  journal.stage='INSTALLING';writeJournal(journalPath,journal);renameSync(journal.stagedCandidate,sourcePath);options.checkpoint?.('installed');
  journal.stage='VALIDATING';writeJournal(journalPath,journal);const db=new Database(sourcePath,{readonly:true,fileMustExist:true});try{assertSupportedSchema(db);if(!compareBusinessBaselines(target,captureBusinessBaseline(db,'normalized')).equal)throw Error('安装后业务校验失败');}finally{db.close();}
  journal.readiness=await probeInstalledService({sourcePath,uploadDir,port:options.servicePort,probeToken:journal.probeToken});
  options.checkpoint?.('validated');
  if(digest(sourcePath)!==manifest.targetDigest||digest(journal.retainedSource)!==manifest.sourceDigest||mediaDigest(uploadDir)!==manifest.mediaDigest||codeFingerprint()!==manifest.sourceCode||verifiedBuildCode(projectRoot(),manifest.sourceCode)!==manifest.buildCode)throw Error('安装校验期间数据库、媒体或代码构建发生变化');
  journal.stage='COMPLETE';writeJournal(journalPath,journal);
  return {completed:true,restoreVerified:true,readiness:journal.readiness,retainedSource:journal.retainedSource,journalPath};
 }catch(error){try{recoverUnlocked({workspaceRoot:root,sourcePath});}catch(recovery){throw new AggregateError([error,recovery],'切换中断且无法自动恢复，请保持停写并人工维护');}throw error;}
}
export async function cutoverNormalizedDatabase(options:Options){
 const sourcePath=containedPath(options.workspaceRoot,options.sourcePath),release=maintenanceLock(options.workspaceRoot,sourcePath);
 try{return await cutoverUnlocked({...options,sourcePath});}finally{release();}
}
