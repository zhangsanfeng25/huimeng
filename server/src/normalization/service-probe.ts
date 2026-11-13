import {spawn} from 'node:child_process';
import {join} from 'node:path';
import {projectRoot} from './fingerprint.js';
export type ServiceReadiness={health:true;staticAssets:number;writeBlocked:true};
/** Start the actual server entry in a read-only maintenance mode, then close it. */
export async function probeInstalledService(options:{sourcePath:string;uploadDir:string;port:number;probeToken:string}):Promise<ServiceReadiness>{
 const root=projectRoot(),sourceMode=import.meta.url.endsWith('.ts'),entry=join(root,'server',sourceMode?'src/main.ts':'dist/main.js');
 const child=spawn(process.execPath,[...(sourceMode?['--import','tsx']:[]),entry],{cwd:join(root,'server'),env:{...process.env,DATABASE_PATH:options.sourcePath,UPLOAD_DIR:options.uploadDir,PORT:String(options.port),HOST:'127.0.0.1',HUIMENG_MAINTENANCE_PROBE:options.probeToken},stdio:['ignore','ignore','pipe'],windowsHide:true});
 let exited=false,startError:Error|undefined;
 const closed=new Promise<void>(resolve=>{child.once('exit',()=>{exited=true;resolve();});child.once('error',error=>{startError=error;exited=true;resolve();});});
 // Drain stderr so a startup error cannot block the child on a full pipe.
 child.stderr?.on('data',()=>{});
 const base='http://127.0.0.1:'+options.port;
 try{
  let healthy=false;const deadline=Date.now()+15000;
  while(Date.now()<deadline){
   if(exited)throw Error('停写验收服务未能启动'+(startError?': '+startError.message:''));
   try{const response=await fetch(base+'/api/v1/health',{signal:AbortSignal.timeout(800)});if(response.ok){const value=await response.json();healthy=value.status==='ok'&&value.maintenanceReadOnly===true&&value.probeId===options.probeToken;if(healthy)break;}}catch{/* Startup may still be loading. */}
   await new Promise(resolve=>setTimeout(resolve,100));
  }
  if(!healthy)throw Error('实际服务健康检查未通过，保持停写');
  for(const [path,method] of [['/api/v1/me/orders','GET'],['/api/v1/auth/login','POST']] as const){const response=await fetch(base+path,{method,signal:AbortSignal.timeout(2000)});if(response.status!==503)throw Error('维护探针未正确阻止业务读写');}
  const response=await fetch(base+'/',{signal:AbortSignal.timeout(3000)});if(!response.ok)throw Error('管理网站入口不可用');const html=await response.text();
  const assets=[...new Set([...html.matchAll(/(?:src|href)=["'](\/assets\/[^"']+\.(?:js|css))["']/g)].map(match=>match[1]))];
  if(!assets.length)throw Error('管理网站缺少构建资源');
  for(const asset of assets){const result=await fetch(base+asset,{signal:AbortSignal.timeout(3000)});if(!result.ok||(await result.arrayBuffer()).byteLength===0)throw Error('管理网站静态资源不可用');}
  return {health:true,staticAssets:assets.length,writeBlocked:true};
 }finally{
  if(!exited)child.kill();
  await Promise.race([closed,new Promise((_,reject)=>setTimeout(()=>reject(Error('维护探针未正常退出，请保持停写')),3000))]);
 }
}
