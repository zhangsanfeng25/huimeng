import Database from 'better-sqlite3';
import {inspectNormalization} from '../src/normalization/preflight.js';
import {buildNormalizedCopy} from '../src/normalization/build-target.js';
import {cutoverNormalizedDatabase,recoverInterruptedSwitch} from '../src/normalization/cutover.js';
const [action,...args]=process.argv.slice(2);
function option(name:string){const index=args.indexOf(name);if(index<0||!args[index+1]||args[index+1].startsWith('--'))throw Error('必须显式指定 '+name);return args[index+1];}
async function main(){
 const sourcePath=option('--database');
 if(action==='check'){const db=new Database(sourcePath,{readonly:true,fileMustExist:true});try{const report=inspectNormalization(db);console.log(JSON.stringify(report,null,2));if(!report.canProceed)process.exitCode=1;}finally{db.close();}return;}
 if(action==='rehearse'){const result=await buildNormalizedCopy({sourcePath,targetPath:option('--target'),manifestPath:option('--manifest'),uploadDir:option('--uploads')});console.log(JSON.stringify(result,null,2));return;}
 if(action==='cutover'){const result=await cutoverNormalizedDatabase({workspaceRoot:option('--root'),sourcePath,targetPath:option('--target'),manifestPath:option('--manifest'),uploadDir:option('--uploads'),assurancePath:option('--assurance'),servicePort:Number(option('--port'))});console.log(JSON.stringify(result,null,2));return;}
 if(action==='recover'){console.log(JSON.stringify(recoverInterruptedSwitch({workspaceRoot:option('--root'),sourcePath}),null,2));return;}
 throw Error('请选择 check、rehearse、cutover 或 recover；日常启动不会自动执行 cutover');
}
main().catch(error=>{console.error(error instanceof Error?error.message:String(error));process.exitCode=1;});
