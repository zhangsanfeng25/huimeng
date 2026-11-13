import {createHash} from 'node:crypto';
import {existsSync,readFileSync} from 'node:fs';
import {dirname,join,parse,resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {captureMediaManifest} from './media.js';
export function projectRoot():string{
 let path=dirname(fileURLToPath(import.meta.url));
 while(path!==parse(path).root){if(existsSync(join(path,'server/package.json'))&&existsSync(join(path,'html/package.json')))return path;path=dirname(path);}
 throw Error('无法定位当前代码根目录');
}
export function codeFingerprint(root=projectRoot()):string{
 const hash=createHash('sha256');
 for(const name of ['package.json','package-lock.json','server/package.json','server/tsconfig.json','html/package.json','html/tsconfig.json','html/index.html','html/vite.config.ts','android-app/app/build.gradle.kts'])hash.update(name).update(readFileSync(resolve(root,name)));
 for(const name of ['server/src','server/scripts','server/migrations','server/assets','html/src','html/launcher','html/public','html/scripts','scripts','android-app/app/src/main'])hash.update(name).update(JSON.stringify(captureMediaManifest(resolve(root,name))));
 return hash.digest('hex');
}
