import express, { Router } from 'express';
import { randomUUID } from 'node:crypto';
import { writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { z } from 'zod';
import { db, now } from '../core/db.js';
import { requireAuth, audit } from '../core/http.js';
import { broadcast } from '../core/events.js';
import { config } from '../config.js';

export const merchantProductsRouter=Router();
const input=z.object({name:z.string().trim().min(1).max(100),summary:z.string().trim().min(1).max(5000),categoryId:z.string(),
  ageRange:z.string().trim().min(1).max(40),priceCents:z.number().int().min(0).max(100000000),stock:z.number().int().min(0).max(1000000),
  coverUrl:z.string().max(500).optional(),detailImages:z.array(z.string().max(500)).max(9).optional(),audioUrl:z.string().max(500).optional()});
const details=(productId:string,merchantId:string)=>db.prepare(`SELECT p.id,p.category_id categoryId,p.status,p.stock_available stock,
  r.id revisionId,r.version,r.name,r.summary,r.age_range ageRange,r.price_cents priceCents,r.cover_url coverUrl,
  r.detail_images_json detailImages,r.audio_url audioUrl,r.review_status reviewStatus,r.rejection_reason rejectionReason,c.name category
  FROM products p JOIN product_revisions r ON r.id=(SELECT id FROM product_revisions WHERE product_id=p.id ORDER BY version DESC LIMIT 1)
  JOIN categories c ON c.id=p.category_id WHERE p.id=? AND p.merchant_id=?`).get(productId,merchantId) as any;
function images(value:string|null){try{const rows=JSON.parse(value||'[]');return Array.isArray(rows)?rows.filter(v=>typeof v==='string'):[];}catch{return [];}}
merchantProductsRouter.post('/merchant/media/uploads',requireAuth('MERCHANT'),express.raw({type:'image/jpeg',limit:'3mb'}),(req,res)=>{
  const bytes=req.body as Buffer;
  if(!Buffer.isBuffer(bytes)||bytes.length<100||bytes.length>3*1024*1024||bytes[0]!==255||bytes[1]!==216||bytes.at(-2)!==255||bytes.at(-1)!==217)
    return res.status(400).json({message:'请选择小于 3 MB 的 JPEG 图片'});
  const path=`/public/community-${randomUUID()}.jpg`;writeFileSync(join(config.uploadDir,path.slice(8)),bytes,{flag:'wx'});
  db.prepare('INSERT INTO community_uploads(path,user_id,created_at) VALUES(?,?,?)').run(path,req.actor!.id,now());
  res.status(201).json({url:path});
});
merchantProductsRouter.get('/merchant/products/:productId',requireAuth('MERCHANT'),(req,res)=>{
  const data=details(String(req.params.productId),req.actor!.merchantId!);
  if(!data)return res.status(404).json({message:'商品不存在'});
  res.json({data:{...data,detailImages:images(data.detailImages)}});
});
merchantProductsRouter.post('/merchant/products',requireAuth('MERCHANT'),(req,res)=>save(req,res));
merchantProductsRouter.put('/merchant/products/:productId',requireAuth('MERCHANT'),(req,res)=>save(req,res,String(req.params.productId)));
function save(req:express.Request,res:express.Response,productId?:string){
  const parsed=input.safeParse(req.body);if(!parsed.success)return res.status(400).json({message:'请完整填写绘本名称、简介、适龄、有效价格库存及最多 9 张图片'});
  const old=productId?details(productId,req.actor!.merchantId!):null;
  if(productId&&!old)return res.status(404).json({message:'商品不存在'});
  if(old?.status==='PENDING_REVIEW')return res.status(409).json({message:'审核中的商品暂不能编辑'});
  const v=parsed.data;
  if(!db.prepare("SELECT 1 FROM categories WHERE id=? AND status='ENABLED'").get(v.categoryId))return res.status(400).json({message:'请选择有效商品分类'});
  const gallery=v.detailImages===undefined?images(old?.detailImages):[...new Set(v.detailImages)];
  const cover=v.coverUrl===undefined?(old?.coverUrl||gallery[0]||''):v.coverUrl;
  // All historical versions are scoped to this product, including administrator supplied media.
  const allowed=new Set<string>();
  if(productId)for(const revision of db.prepare('SELECT cover_url,detail_images_json FROM product_revisions WHERE product_id=?').all(productId) as any[]){if(revision.cover_url)allowed.add(revision.cover_url);images(revision.detail_images_json).forEach(v=>allowed.add(v));}
  if([cover,...gallery].filter(Boolean).some(path=>!allowed.has(path)&&!db.prepare('SELECT 1 FROM community_uploads WHERE path=? AND user_id=? AND post_id IS NULL').get(path,req.actor!.id)))
    return res.status(400).json({message:'图片必须由当前商家上传或属于当前商品'});
  const audio=v.audioUrl===undefined?(old?.audioUrl||null):v.audioUrl||null;
  const id=productId||`P${randomUUID().replaceAll('-','').slice(0,16)}`,revisionId=`PR${randomUUID().replaceAll('-','').slice(0,16)}`,stamp=now();
  db.transaction(()=>{
    if(!old)db.prepare(`INSERT INTO products(id,merchant_id,category_id,status,stock_available,stock_reserved,created_at,updated_at) VALUES(?,?,?,'DRAFT',?,0,?,?)`).run(id,req.actor!.merchantId,v.categoryId,v.stock,stamp,stamp);
    if(old?.status==='DRAFT')db.prepare(`UPDATE product_revisions SET name=?,summary=?,age_range=?,price_cents=?,cover_url=?,detail_images_json=?,audio_url=?,review_status='DRAFT',rejection_reason=NULL WHERE id=?`)
      .run(v.name,v.summary,v.ageRange,v.priceCents,cover||null,JSON.stringify(gallery),audio,old.revisionId);
    else db.prepare(`INSERT INTO product_revisions(id,product_id,version,name,summary,age_range,price_cents,cover_url,detail_images_json,audio_url,ar_enabled,review_status,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,0,'DRAFT',?)`)
      .run(revisionId,id,old?old.version+1:1,v.name,v.summary,v.ageRange,v.priceCents,cover||null,JSON.stringify(gallery),audio,stamp);
    db.prepare("UPDATE products SET category_id=?,stock_available=?,status='DRAFT',updated_at=? WHERE id=?").run(v.categoryId,v.stock,stamp,id);
    audit(req.actor!.id,req.actor!.name,'商品',old?'编辑图文':'新增图文',id);
  })();
  broadcast('products',old?'update':'create');return res.status(old?200:201).json({ok:true,productId:id,revisionId:old?.status==='DRAFT'?old.revisionId:revisionId});
}
