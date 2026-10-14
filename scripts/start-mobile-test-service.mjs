import {spawn,spawnSync} from 'node:child_process';
import {mkdirSync,writeFileSync} from 'node:fs';
import {resolve,join,sep} from 'node:path';
import {fileURLToPath} from 'node:url';

// Explicit fixture path prevents mobile integration tests from using the real database.
const root=resolve(fileURLToPath(new URL('..',import.meta.url)));
const index=process.argv.indexOf('--fixture');
if(index<0||!process.argv[index+1])throw Error('Provide --fixture output/<test-directory>');
const fixture=resolve(root,process.argv[index+1]);
if(!fixture.startsWith(join(root,'output')+sep))throw Error('Test fixture must be inside output');
mkdirSync(fixture,{recursive:true});
const env={...process.env,PORT:'3000',HOST:'127.0.0.1',DATABASE_PATH:join(fixture,'test.db'),UPLOAD_DIR:join(fixture,'uploads')};
for(const step of ['migrate','seed']){
 const result=spawnSync(process.execPath,[`dist/scripts/${step}.js`],{cwd:join(root,'server'),env,windowsHide:true,stdio:'inherit'});
 if(result.status!==0)throw Error('Fixture preparation failed');
}
const server=spawn(process.execPath,['dist/main.js'],{cwd:join(root,'server'),env,windowsHide:true,stdio:'inherit'});
writeFileSync(join(fixture,'owner.json'),JSON.stringify({runnerPid:process.pid,serverPid:server.pid,fixture}));
for(const signal of ['SIGINT','SIGTERM'])process.on(signal,()=>server.kill());
server.on('exit',code=>process.exit(code||0));
