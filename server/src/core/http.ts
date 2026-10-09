import type { NextFunction, Request, Response } from 'express';
import { randomBytes } from 'node:crypto';
import { db, now } from './db.js';
import { hashToken } from './security.js';
import { config } from '../config.js';

export function requestId() { return `req_${randomBytes(6).toString('hex')}`; }
export function asyncRoute(handler: (req: Request, res: Response, next: NextFunction) => Promise<unknown>) {
  return (req: Request, res: Response, next: NextFunction) => Promise.resolve(handler(req,res,next)).catch(next);
}
export function requireAuth(...roles: string[]) {
  return (req: Request, res: Response, next: NextFunction) => {
    const raw=req.headers.authorization?.replace(/^Bearer\s+/i,'') || String(req.query.token || '');
    if(!raw) return res.status(401).json({message:'请先登录'});
    const session=db.prepare(`SELECT s.user_id,s.active_role,s.expires_at,u.nickname,u.status
      FROM sessions s JOIN users u ON u.id=s.user_id
      WHERE s.token_hash=? AND s.expires_at>?`).get(hashToken(raw),now()) as any;
    if(!session || session.status!=='ACTIVE') return res.status(401).json({message:'登录已失效，请重新登录'});
    // Extend an actively used session before it expires; idle sessions still expire.
    if(Date.parse(session.expires_at)-Date.now()<30*60_000){
      db.prepare('UPDATE sessions SET expires_at=? WHERE token_hash=?').run(new Date(Date.now()+config.sessionTtlHours*3_600_000).toISOString(),hashToken(raw));
    }
    const roleRows=db.prepare('SELECT role FROM user_roles WHERE user_id=?').all(session.user_id) as Array<{role:string}>;
    const merchant=db.prepare('SELECT id,approval_status,business_status FROM merchants WHERE owner_user_id=?').get(session.user_id) as {id:string;approval_status:string;business_status:string}|undefined;
    const actorRoles=roleRows.map(row=>row.role).filter(role=>role!=='MERCHANT'||(merchant?.approval_status==='APPROVED'&&merchant.business_status==='OPEN'));
    if(roles.length && !roles.some(role=>actorRoles.includes(role))) return res.status(403).json({message:'当前角色无权执行此操作'});
    req.actor={id:session.user_id,name:session.nickname,roles:actorRoles,merchantId:actorRoles.includes('MERCHANT')?merchant?.id:undefined,activeRole:actorRoles.includes(session.active_role)?session.active_role:(actorRoles.includes('USER')?'USER':actorRoles[0])};
    next();
  };
}
export function audit(operatorId:string,operatorName:string,module:string,action:string,objectId:string,result='成功',summary='') {
  db.prepare(`INSERT INTO audit_logs(request_id,operator_id,operator_name,module,action,object_id,result,summary,created_at)
    VALUES(?,?,?,?,?,?,?,?,?)`).run(requestId(),operatorId,operatorName,module,action,objectId,result,summary,now());
}
