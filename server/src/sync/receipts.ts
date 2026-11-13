import {createHash} from 'node:crypto';
import {db,now} from '../core/db.js';
import {broadcast} from '../core/events.js';
import {applyOperation,authorizeOperation} from './operations.js';
import {SyncError,type Actor,type SyncEnvelope,type SyncResult} from './types.js';
export function canonical(value:unknown):string{
 if(value===null||typeof value==='boolean'||typeof value==='string')return JSON.stringify(value);
 if(typeof value==='number'&&Number.isFinite(value))return JSON.stringify(value);
 if(Array.isArray(value))return '['+value.map(canonical).join(',')+']';
 if(typeof value==='object'&&value){const row=value as Record<string,unknown>;return '{'+Object.keys(row).sort().map(key=>JSON.stringify(key)+':'+canonical(row[key])).join(',')+'}';}
 throw new SyncError(400,'INVALID_JSON','同步载荷格式错误');
}
export function canonicalHash(value:unknown){return createHash('sha256').update(canonical(value)).digest('hex');}
export function executeOnce(actor:Actor,op:SyncEnvelope):SyncResult{
 const user=db.prepare("SELECT 1 FROM users WHERE id=? AND status='ACTIVE'").get(actor.id);if(!user)throw new SyncError(401,'AUTH_REQUIRED','请重新验证网络账号');
 const shop=db.prepare("SELECT id FROM merchants WHERE owner_user_id=? AND approval_status='APPROVED' AND business_status='OPEN'").get(actor.id) as {id:string}|undefined;
 const allowed=(db.prepare('SELECT role FROM user_roles WHERE user_id=?').all(actor.id) as Array<{role:string}>).map(row=>row.role);
 const roles=(actor.activeRole?[actor.activeRole]:actor.roles).filter(role=>allowed.includes(role)&&(role!=='MERCHANT'||!!shop));
 if(!roles.length)throw new SyncError(403,'ROLE_REQUIRED','当前身份权限已变化，请重新登录');
 actor={...actor,roles,merchantId:roles.includes('MERCHANT')?shop?.id:undefined};
 authorizeOperation(actor,op);
 if(op.payloadHash!==canonicalHash(op.payload))throw new SyncError(400,'HASH_MISMATCH','同步载荷校验失败');
 const hash=canonicalHash({action:op.action,entityId:op.entityId||null,baseVersion:op.baseVersion??null,payload:op.payload});
 const output=db.transaction(()=>{
  const previous=db.prepare('SELECT payload_hash,result_json FROM sync_receipts WHERE user_id=? AND operation_id=?').get(actor.id,op.operationId) as {payload_hash:string;result_json:string}|undefined;
  if(previous){if(previous.payload_hash!==hash)throw new SyncError(409,'KEY_REUSE','同一幂等键不能使用不同载荷');return {result:{...JSON.parse(previous.result_json),replayed:true} as SyncResult,events:[]};}
  const output=applyOperation(actor,op);
  db.prepare('INSERT INTO sync_receipts(user_id,operation_id,client_id,payload_hash,result_json,created_at) VALUES(?,?,?,?,?,?)').run(actor.id,op.operationId,op.clientId,hash,JSON.stringify(output.result),now());return output;
 })();
 for(const event of output.events)broadcast(event.resource,event.action);return output.result;
}
