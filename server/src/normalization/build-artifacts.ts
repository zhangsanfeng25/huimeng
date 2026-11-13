import {createHash} from 'node:crypto';
import {existsSync,readFileSync,writeFileSync} from 'node:fs';
import {join} from 'node:path';
import {captureMediaManifest} from './media.js';
function outputCode(root:string):string{
 for(const path of ['server/dist/main.js','server/dist/scripts/normalize.js','html/dist/index.html'])if(!existsSync(join(root,path)))throw Error('构建产物不完整：'+path);
 const hash=createHash('sha256');
 for(const directory of ['server/dist','html/dist'])hash.update(directory).update(JSON.stringify(captureMediaManifest(join(root,directory)).filter(file=>!['normalization-build.json','launcher-build.json'].includes(file.path))));
 return hash.digest('hex');
}
export function stampBuild(root:string,sourceCode:string):string{
 const buildCode=outputCode(root);writeFileSync(join(root,'server/dist/normalization-build.json'),JSON.stringify({format:'huimeng-normalization-build-v1',sourceCode,buildCode},null,2));return buildCode;
}
export function verifiedBuildCode(root:string,sourceCode:string):string{
 const stamp=JSON.parse(readFileSync(join(root,'server/dist/normalization-build.json'),'utf8'));
 if(stamp.format!=='huimeng-normalization-build-v1'||stamp.sourceCode!==sourceCode)throw Error('源码与构建标记不一致，请重新完整构建');
 const current=outputCode(root);if(stamp.buildCode!==current)throw Error('构建产物发生变化，请重新完整构建并验收');return current;
}
