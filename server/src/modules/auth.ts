import {businessRoute} from '../services/context.js';
import {profileAvatar,profileSave} from '../services/profile.js';
import { Router } from 'express';
import rateLimit from 'express-rate-limit';
import { z } from 'zod';
import { db, now } from '../core/db.js';
import { asyncRoute, audit, requireAuth } from '../core/http.js';
import { createToken, hashPassword, hashToken, verifyPassword } from '../core/security.js';
import { config } from '../config.js';
import { broadcast } from '../core/events.js';
import {defaultAvatars,leastUsedDefaultAvatar} from '../core/default-avatars.js';

export const authRouter=Router();
const loginLimit=rateLimit({windowMs:60_000,limit:8,standardHeaders:'draft-8',legacyHeaders:false,message:{message:'登录尝试过多，请稍后再试'}});
const loginSchema=z.object({username:z.string().min(1),password:z.string().min(8),role:z.enum(['USER','MERCHANT','ADMIN']).optional()});

authRouter.post('/login',loginLimit,asyncRoute(async(req,res)=>{
  const parsed=loginSchema.safeParse(req.body);
  if(!parsed.success) return res.status(400).json({message:'请输入正确的账号和密码'});
  const {username,password,role}=parsed.data;
  const user=db.prepare(`SELECT * FROM users WHERE username=? OR email=? OR phone=?`).get(username,username,username) as any;
  if(!user || user.status!=='ACTIVE' || !verifyPassword(password,user.password_hash)) return res.status(401).json({message:'账号或密码错误'});
  const shop=db.prepare("SELECT 1 FROM merchants WHERE owner_user_id=? AND approval_status='APPROVED' AND business_status='OPEN'").get(user.id);
  const roles=(db.prepare('SELECT role FROM user_roles WHERE user_id=?').all(user.id) as Array<{role:string}>).map(row=>row.role).filter(r=>r!=='MERCHANT'||Boolean(shop));
  const activeRole=role || (roles.includes('ADMIN')?'ADMIN':roles.includes('MERCHANT')?'MERCHANT':'USER');
  if(!roles.includes(activeRole)) return res.status(403).json({message:'此账号没有所选角色'});
  const token=createToken();
  const expiresAt=new Date(Date.now()+config.sessionTtlHours*3_600_000).toISOString();
  db.prepare('DELETE FROM sessions WHERE expires_at<=?').run(now());
  db.prepare('INSERT INTO sessions(token_hash,user_id,active_role,expires_at,created_at) VALUES(?,?,?,?,?)').run(hashToken(token),user.id,activeRole,expiresAt,now());
  audit(user.id,user.nickname,'账户','登录',user.username);
  return res.json({token,expiresAt,user:{id:user.id,username:user.username,name:user.nickname,avatarUrl:user.avatar_url||'',roles,activeRole,version:user.version}});
}));

authRouter.post('/register',loginLimit,asyncRoute(async(req,res)=>{
  const parsed=z.object({username:z.string().min(3).max(32),password:z.string().min(8).max(64),nickname:z.string().min(1).max(30),email:z.string().email().optional(),phone:z.string().min(6).max(20).optional()}).safeParse(req.body);
  if(!parsed.success) return res.status(400).json({message:'注册信息格式不正确'});
  const value=parsed.data,id=`U${Date.now().toString().slice(-10)}`,stamp=now();
  try{
    db.transaction(()=>{
      db.prepare(`INSERT INTO users(id,username,email,phone,nickname,password_hash,status,created_at,updated_at) VALUES(?,?,?,?,?,?,'ACTIVE',?,?)`).run(id,value.username,value.email||null,value.phone||null,value.nickname,hashPassword(value.password),stamp,stamp);
      db.prepare(`INSERT INTO user_roles(user_id,role) VALUES(?,'USER')`).run(id);
      db.prepare('UPDATE users SET avatar_url=? WHERE id=?').run(leastUsedDefaultAvatar(),id);
    })();
  }catch{return res.status(409).json({message:'账号、邮箱或手机号已被使用'});}
  broadcast('users','create');
  return res.status(201).json({id,message:'注册成功'});
}));

// Re-enable only after a verified, expiring recovery credential is implemented.
authRouter.post('/forgot-password',loginLimit,(_req,res)=>{
  res.status(501).json({message:'自助找回尚未接入身份验证，请联系管理员协助处理'});
});

authRouter.get('/me',requireAuth(),(req,res)=>{
  const profile=db.prepare('SELECT username,nickname,avatar_url,version FROM users WHERE id=?').get(req.actor!.id) as {username:string,nickname:string,avatar_url:string|null,version:number};
  res.json({user:{...req.actor,username:profile.username,name:profile.nickname,avatarUrl:profile.avatar_url||'',version:profile.version}});
});

authRouter.post('/switch-role',requireAuth(),(req,res)=>{
  const parsed=z.object({role:z.enum(['USER','MERCHANT'])}).safeParse(req.body);
  if(!parsed.success)return res.status(400).json({message:'请选择买家或商家身份'});
  if(!req.actor!.roles.includes(parsed.data.role))return res.status(403).json({message:'商家申请尚未通过或店铺已停用，请查看申请状态'});
  const token=req.headers.authorization?.replace(/^Bearer\s+/i,'')||'';
  db.prepare('UPDATE sessions SET active_role=? WHERE token_hash=?').run(parsed.data.role,hashToken(token));
  res.json({activeRole:parsed.data.role,roles:req.actor!.roles});
});
authRouter.get('/default-avatars',requireAuth(),(_req,res)=>res.json({data:defaultAvatars}));
authRouter.put('/me/avatar',requireAuth(),businessRoute(profileAvatar));
authRouter.put('/me',requireAuth(),businessRoute(profileSave));
authRouter.post('/logout',requireAuth(),(req,res)=>{
  const raw=req.headers.authorization?.replace(/^Bearer\s+/i,'')||'';
  db.prepare('DELETE FROM sessions WHERE token_hash=?').run(hashToken(raw));
  res.json({ok:true});
});
authRouter.post('/change-password',requireAuth(),asyncRoute(async(req,res)=>{
  const parsed=z.object({currentPassword:z.string(),newPassword:z.string().min(8).max(64)}).safeParse(req.body);
  if(!parsed.success)return res.status(400).json({message:'新密码需要 8 至 64 位'});
  const user=db.prepare('SELECT password_hash FROM users WHERE id=?').get(req.actor!.id) as any;
  if(!verifyPassword(parsed.data.currentPassword,user.password_hash))return res.status(400).json({message:'当前密码不正确'});
  db.transaction(()=>{
    db.prepare('UPDATE users SET password_hash=?,updated_at=? WHERE id=?').run(hashPassword(parsed.data.newPassword),now(),req.actor!.id);
    db.prepare('DELETE FROM sessions WHERE user_id=?').run(req.actor!.id);
  })();
  audit(req.actor!.id,req.actor!.name,'账户','修改密码',req.actor!.id);
  res.json({message:'密码修改成功，请重新登录'});
}));
