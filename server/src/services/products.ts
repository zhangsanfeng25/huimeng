import {randomUUID} from 'node:crypto';
import {db,now} from '../core/db.js';
import {audit} from '../core/http.js';
import {input,details,images} from './product-data.js';
import {readBookDetails,saveBookDetails} from './book-details.js';
import type {BusinessContext,BusinessResult} from './context.js';
export function productSave(ctx:BusinessContext):BusinessResult {const productId=ctx.params.productId;
const parsed=input.safeParse(ctx.payload);if(!parsed.success)return ctx.reply(400,{message:'请完整填写绘本名称、简介、适龄、有效价格库存及最多 9 张图片'});
  const old=productId?details(productId,ctx.actor.merchantId!):null;
  if(productId&&!old)return ctx.reply(404,{message:'商品不存在'});
  if(old?.status==='PENDING_REVIEW')return ctx.reply(409,{message:'审核中的商品暂不能编辑'});
  const v=parsed.data;
  if(!db.prepare("SELECT 1 FROM categories WHERE id=? AND status='ENABLED'").get(v.categoryId))return ctx.reply(400,{message:'请选择有效商品分类'});
  const gallery=v.detailImages===undefined?images(old?.detailImages):[...new Set(v.detailImages)];
  const cover=v.coverUrl===undefined?(old?.coverUrl||gallery[0]||''):v.coverUrl;
  // All historical versions are scoped to this product, including administrator supplied media.
  const allowed=new Set<string>();
  if(productId)for(const revision of db.prepare('SELECT cover_url,detail_images_json FROM product_revisions WHERE product_id=?').all(productId) as any[]){if(revision.cover_url)allowed.add(revision.cover_url);images(revision.detail_images_json).forEach(v=>allowed.add(v));}
  if([cover,...gallery].filter(Boolean).some(path=>!allowed.has(path)&&!db.prepare('SELECT 1 FROM community_uploads WHERE path=? AND user_id=? AND post_id IS NULL').get(path,ctx.actor.id)))
    return ctx.reply(400,{message:'图片必须由当前商家上传或属于当前商品'});
  const audio=v.audioUrl===undefined?(old?.audioUrl||null):v.audioUrl||null;
  const id=productId||`P${randomUUID().replaceAll('-','').slice(0,16)}`,revisionId=`PR${randomUUID().replaceAll('-','').slice(0,16)}`,stamp=now();
  db.transaction(()=>{
    if(!old)db.prepare(`INSERT INTO products(id,merchant_id,category_id,status,stock_available,stock_reserved,created_at,updated_at) VALUES(?,?,?,'DRAFT',?,0,?,?)`).run(id,ctx.actor.merchantId,v.categoryId,v.stock,stamp,stamp);
    if(old?.status==='DRAFT')db.prepare(`UPDATE product_revisions SET name=?,summary=?,age_range=?,price_cents=?,cover_url=?,detail_images_json=?,audio_url=?,review_status='DRAFT',rejection_reason=NULL WHERE id=?`)
      .run(v.name,v.summary,v.ageRange,v.priceCents,cover||null,JSON.stringify(gallery),audio,old.revisionId);
    else db.prepare(`INSERT INTO product_revisions(id,product_id,version,name,summary,age_range,price_cents,cover_url,detail_images_json,audio_url,ar_enabled,review_status,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,0,'DRAFT',?)`)
      .run(revisionId,id,old?old.version+1:1,v.name,v.summary,v.ageRange,v.priceCents,cover||null,JSON.stringify(gallery),audio,stamp);
    saveBookDetails(old?.status==='DRAFT'?old.revisionId:revisionId,{...readBookDetails(old),...v});
    db.prepare("UPDATE products SET category_id=?,stock_available=?,status='DRAFT',updated_at=? WHERE id=?").run(v.categoryId,v.stock,stamp,id);
    audit(ctx.actor.id,ctx.actor.name,'商品',old?'编辑图文':'新增图文',id);
  })();
  ctx.emit('products',old?'update':'create');return ctx.reply(old?200:201,{ok:true,productId:id,revisionId:old?.status==='DRAFT'?old.revisionId:revisionId});

}
