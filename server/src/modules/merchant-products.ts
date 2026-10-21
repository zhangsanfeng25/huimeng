import {businessRoute} from '../services/context.js';
import {productSave} from '../services/products.js';
import express, { Router } from 'express';
import { randomUUID } from 'node:crypto';
import { writeFileSync } from 'node:fs';
import { join } from 'node:path';

import { db, now } from '../core/db.js';
import {requireAuth} from '../core/http.js';

import { config } from '../config.js';

export const merchantProductsRouter=Router();
import {details,images} from '../services/product-data.js';
import {readBookDetails} from '../services/book-details.js';
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
  res.json({data:{...data,...readBookDetails(data),detailImages:images(data.detailImages)}});
});
merchantProductsRouter.post('/merchant/products',requireAuth('MERCHANT'),businessRoute(productSave));
merchantProductsRouter.put('/merchant/products/:productId',requireAuth('MERCHANT'),businessRoute(productSave));
