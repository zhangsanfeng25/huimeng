import {createHash,randomUUID} from 'node:crypto';
import {existsSync,readFileSync,writeFileSync,renameSync,mkdirSync,unlinkSync} from 'node:fs';
import {join} from 'node:path';
import {db,now} from '../core/db.js';
import {config} from '../config.js';
import {SyncError,type Actor} from './types.js';
export function saveMedia(actor:Actor,operationId:string,purpose:string,hash:string,bytes:Buffer){
 if(!['COMMUNITY','PRODUCT'].includes(purpose)||purpose==='PRODUCT'&&!actor.roles.includes('MERCHANT'))throw new SyncError(403,'MEDIA_ROLE','当前账号无权上传此类图片');
 if(!operationId||operationId.length>100||!Buffer.isBuffer(bytes)||bytes.length<100||bytes.length>3*1024*1024||bytes[0]!==255||bytes[1]!==216||bytes.at(-2)!==255||bytes.at(-1)!==217)throw new SyncError(400,'INVALID_MEDIA','请选择小于 3 MB 的 JPEG 图片');
 if(hash!==createHash('sha256').update(bytes).digest('hex'))throw new SyncError(400,'HASH_MISMATCH','图片校验失败');
 let old=db.prepare('SELECT * FROM sync_media WHERE user_id=? AND operation_id=?').get(actor.id,operationId) as {sha256:string;url:string;purpose:string;state:string}|undefined;
 if(old&&(old.sha256!==hash||old.purpose!==purpose))throw new SyncError(409,'KEY_REUSE','同一图片操作不能使用不同内容或用途');
 const reused=!!old;
 if(!old){old={sha256:hash,url:`/public/community-${randomUUID()}.jpg`,purpose,state:'PREPARED'};db.prepare("INSERT INTO sync_media(user_id,operation_id,sha256,url,purpose,state,created_at) VALUES(?,?,?,?,?,'PREPARED',?)").run(actor.id,operationId,hash,old.url,purpose,now());}
 mkdirSync(config.uploadDir,{recursive:true});const target=join(config.uploadDir,old.url.slice(8)),temp=target+'.tmp';
 if(existsSync(target)){if(createHash('sha256').update(readFileSync(target)).digest('hex')!==hash)throw new SyncError(409,'MEDIA_CORRUPTED','已上传图片校验冲突，请处理');}
 else {if(existsSync(temp))unlinkSync(temp);writeFileSync(temp,bytes,{flag:'wx'});renameSync(temp,target);}
 db.transaction(()=>{db.prepare('INSERT OR IGNORE INTO community_uploads(path,user_id,created_at) VALUES(?,?,?)').run(old!.url,actor.id,now());db.prepare("UPDATE sync_media SET state='READY' WHERE user_id=? AND operation_id=?").run(actor.id,operationId);})();
 return {operationId,url:old.url,sha256:hash,replayed:reused};
}
