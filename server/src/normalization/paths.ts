import {existsSync,lstatSync,realpathSync} from 'node:fs';
import {dirname,isAbsolute,relative,resolve,sep} from 'node:path';
export function assertNoSymbolicAncestors(path:string):void{
 let current=resolve(path);
 for(;;){if(existsSync(current)&&lstatSync(current).isSymbolicLink())throw Error('维护路径不能经过符号链接');const parent=dirname(current);if(parent===current)break;current=parent;}
}
/** Every existing component must be a real path under the explicitly chosen root. */
export function containedPath(root:string,path:string):string{
 const base=resolve(root),target=resolve(path),rel=relative(base,target);
 assertNoSymbolicAncestors(target);
 if(!rel||rel==='..'||rel.startsWith('..'+sep)||isAbsolute(rel))throw Error('维护路径相同或越界');
 if(!existsSync(base)||lstatSync(base).isSymbolicLink()||!lstatSync(base).isDirectory()||realpathSync(base).toLowerCase()!==base.toLowerCase())throw Error('维护根目录必须为实际目录');
 let current=base;
 for(const part of rel.split(sep)){current=resolve(current,part);if(existsSync(current)&&lstatSync(current).isSymbolicLink())throw Error('维护路径不能经过符号链接');}
 return target;
}
