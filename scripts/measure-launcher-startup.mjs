import { spawn } from 'node:child_process';
import { mkdtempSync, writeFileSync, mkdirSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { performance } from 'node:perf_hooks';

const root=resolve(process.argv[2]||'.'), output=resolve(process.argv[3]||'output/startup.json');
const mode=process.argv[4]||'legacy', port=Number(process.argv[5]||31995), runs=Number(process.argv[6]||3);
const fixture=mkdtempSync(join(tmpdir(),'huimeng-startup-'));
const env={...process.env,DATABASE_PATH:join(fixture,'test.db'),UPLOAD_DIR:join(fixture,'uploads'),PORT:String(port),HOST:'127.0.0.1'};
const prepare=join(root,'html/launcher/prepare.mjs');
const execute=(args,cwd=root)=>new Promise((ok,fail)=>{
  const child=spawn(process.execPath,args,{cwd,env,windowsHide:true,stdio:['ignore','pipe','pipe']});let logs='';
  child.stdout.on('data',x=>logs+=x);child.stderr.on('data',x=>logs+=x);
  child.once('error',fail);child.once('close',code=>code===0?ok(logs):fail(new Error(logs)));
});
try {await fetch(`http://127.0.0.1:${port}/api/v1/health`);throw new Error('Measurement port already in use');}
catch(e){if(e.message==='Measurement port already in use')throw e;}
async function measure(){
  const start=performance.now();
  if(mode==='legacy'){await execute([prepare,'--build','server']);await execute([prepare]);await execute([prepare,'--build','html']);}
  else await execute([prepare,'--prepare']);
  const preparedMs=performance.now()-start;
  const child=spawn(process.execPath,['dist/main.js'],{cwd:join(root,'server'),env,windowsHide:true,stdio:['ignore','pipe','pipe']});let logs='';
  child.stderr.on('data',x=>logs+=x);child.stdout.on('data',()=>{});
  try{
    let apiReadyMs,webReadyMs;
    while(performance.now()-start<60000){
      if(child.exitCode!==null)throw new Error('Service exited: '+logs);
      try{
        const response=await fetch(`http://127.0.0.1:${port}/api/v1/health`,{signal:AbortSignal.timeout(500)});
        if(response.ok&&(await response.json()).service==='huimeng-api'){
          apiReadyMs??=performance.now()-start;
          const page=await fetch(`http://127.0.0.1:${port}/login`);
          if(page.ok&&(await page.text()).includes('<html')){webReadyMs=performance.now()-start;return {preparedMs,apiReadyMs,webReadyMs};}
        }
      }catch{}
      await new Promise(r=>setTimeout(r,50));
    }
    throw new Error('Startup readiness timeout');
  }finally{const closed=new Promise(r=>child.once('close',r));child.kill();await closed;}
}
const priming=await measure(),warm=[];for(let i=0;i<runs;i++)warm.push(await measure());
const median=warm.map(x=>x.webReadyMs).sort((a,b)=>a-b)[Math.floor(runs/2)];
mkdirSync(resolve(output,'..'),{recursive:true});
writeFileSync(output,JSON.stringify({environment:{node:process.version,platform:process.platform,root,mode},cold:null,priming,warm,median,fixture,limitations:['Measures preparation plus API/login HTML readiness, excluding WinForms startup and browser rendering.','Cold dependency installation not measured; existing artifacts retained.','Isolated measurement fixture retained for inspection.']},null,2));
console.log(JSON.stringify({mode,median,warm,output}));
