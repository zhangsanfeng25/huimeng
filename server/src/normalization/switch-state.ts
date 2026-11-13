import {existsSync,readFileSync} from 'node:fs';
import {resolve} from 'node:path';
export function assertNoInterruptedSwitch(sourcePath:string,probeToken?:string):boolean{
 const path=resolve(sourcePath)+'.normalization-switch.json';if(!existsSync(path)){if(probeToken)throw Error('维护探针缺少有效状态单');return false;}
 const journal=JSON.parse(readFileSync(path,'utf8'));
 if(journal.format==='huimeng-normalization-switch-v1'&&journal.sourcePath===resolve(sourcePath)&&journal.stage==='VALIDATING'&&probeToken&&probeToken===journal.probeToken)return true;
 if(journal.format!=='huimeng-normalization-switch-v1'||journal.sourcePath!==resolve(sourcePath)||!['COMPLETE','ROLLED_BACK'].includes(journal.stage))throw Error('检测到未完成的数据库维护切换，请先恢复；拒绝日常启动');
 if(probeToken)throw Error('维护探针不在停写验收阶段');
 return false;
}
