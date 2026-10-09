import { Router } from 'express';
import { randomUUID } from 'node:crypto';
import { z } from 'zod';
import { db, now } from '../core/db.js';
import { audit, requireAuth } from '../core/http.js';
import { broadcast, subscribe } from '../core/events.js';

export const merchantApplicationRouter=Router();
export function merchantApplication(ownerId:string){
  return db.prepare(`SELECT id,shop_name shopName,description,contact_name contactName,contact_phone contactPhone,
    business_address businessAddress,approval_status status,business_status businessStatus,
    COALESCE(rejection_reason,'') rejectionReason,applied_at appliedAt,reviewed_at reviewedAt,updated_at updatedAt
    FROM merchants WHERE owner_user_id=?`).get(ownerId)||null;
}
merchantApplicationRouter.get('/me/events',requireAuth(),(_req,res)=>{
  res.set({'Content-Type':'text/event-stream','Cache-Control':'no-cache','Connection':'keep-alive'});
  res.flushHeaders();res.write('event: ready\ndata: {}\n\n');subscribe(res);
});
merchantApplicationRouter.get('/me/merchant-application',requireAuth(),(req,res)=>res.json({data:merchantApplication(req.actor!.id)}));
merchantApplicationRouter.post('/me/merchant-application',requireAuth('USER'),(req,res)=>{
  const parsed=z.object({shopName:z.string().trim().min(2).max(60),contactName:z.string().trim().min(2).max(30),
    contactPhone:z.string().trim().regex(/^\+?[0-9][0-9 -]{5,19}$/),businessAddress:z.string().trim().min(5).max(200),
    description:z.string().trim().min(10).max(1000),acceptedTerms:z.literal(true)}).safeParse(req.body);
  if(!parsed.success)return res.status(400).json({message:'请完整填写店铺、联系人、有效联系电话、经营地址和至少 10 字的介绍，并确认资料真实'});
  const old=db.prepare('SELECT id,approval_status status FROM merchants WHERE owner_user_id=?').get(req.actor!.id) as {id:string;status:string}|undefined;
  if(old&&old.status!=='REJECTED')return res.status(409).json({message:old.status==='PENDING'?'申请正在审核，请勿重复提交':'店铺已审核通过，请查看当前经营状态'});
  const v=parsed.data,stamp=now(),id=old?.id||`M${randomUUID().replaceAll('-','').slice(0,16).toUpperCase()}`;
  db.transaction(()=>{
    if(old)db.prepare(`UPDATE merchants SET shop_name=?,description=?,contact_name=?,contact_phone=?,business_address=?,
      approval_status='PENDING',business_status='PENDING',rejection_reason=NULL,reviewed_at=NULL,reviewed_by=NULL,applied_at=?,updated_at=? WHERE id=?`)
      .run(v.shopName,v.description,v.contactName,v.contactPhone,v.businessAddress,stamp,stamp,id);
    else db.prepare(`INSERT INTO merchants(id,owner_user_id,shop_name,description,contact_name,contact_phone,business_address,applied_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?)`)
      .run(id,req.actor!.id,v.shopName,v.description,v.contactName,v.contactPhone,v.businessAddress,stamp,stamp);
    audit(req.actor!.id,req.actor!.name,'商家申请',old?'重新提交':'提交申请',id);
  })();
  broadcast('merchants','apply');broadcast('users','update');res.status(201).json({data:merchantApplication(req.actor!.id)});
});
