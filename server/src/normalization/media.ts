import {createHash} from 'node:crypto';
import {existsSync,lstatSync,readdirSync,readFileSync} from 'node:fs';
import {join,relative,resolve,sep} from 'node:path';
import type {MediaEntry} from './types.js';

export function captureMediaManifest(uploadDir:string):MediaEntry[]{
 const root=resolve(uploadDir),result:MediaEntry[]=[];
 if(!existsSync(root))return result;
 if(lstatSync(root).isSymbolicLink()||!lstatSync(root).isDirectory())throw Error('素材根目录不能是符号链接或文件');
 const visit=(directory:string)=>{
  for(const name of readdirSync(directory).sort()){
   const path=join(directory,name),stat=lstatSync(path);
   if(!resolve(path).startsWith(root+sep))throw Error('素材路径越界');
   if(stat.isSymbolicLink())throw Error('素材目录不能包含符号链接');
   if(stat.isDirectory())visit(path);
   else if(stat.isFile()){
    const bytes=readFileSync(path);
    if(bytes.length!==stat.size)throw Error('读取期间素材发生变化');
    result.push({path:relative(root,path).split(sep).join('/'),size:bytes.length,sha256:createHash('sha256').update(bytes).digest('hex')});
   }else throw Error('素材目录包含非普通文件');
  }
 };
 visit(root);return result.sort((a,b)=>a.path<b.path?-1:a.path>b.path?1:0);
}
