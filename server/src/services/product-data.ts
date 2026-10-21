import {z} from 'zod';
import {db} from '../core/db.js';
import {bookInput,bookColumns} from './book-details.js';
export const input=z.object({name:z.string().trim().min(1).max(100),summary:z.string().trim().min(1).max(5000),categoryId:z.string(),
  ageRange:z.string().trim().min(1).max(40),priceCents:z.number().int().min(0).max(100000000),stock:z.number().int().min(0).max(1000000),
  coverUrl:z.string().max(500).optional(),detailImages:z.array(z.string().max(500)).max(9).optional(),audioUrl:z.string().max(500).optional()}).extend(bookInput.shape);
export const details=(productId:string,merchantId:string)=>db.prepare(`SELECT p.id,p.category_id categoryId,p.status,p.stock_available stock,
  r.id revisionId,r.version,${bookColumns},r.name,r.summary,r.age_range ageRange,r.price_cents priceCents,r.cover_url coverUrl,
  r.detail_images_json detailImages,r.audio_url audioUrl,r.review_status reviewStatus,r.rejection_reason rejectionReason,c.name category
  FROM products p JOIN product_revisions r ON r.id=(SELECT id FROM product_revisions WHERE product_id=p.id ORDER BY version DESC LIMIT 1)
  JOIN categories c ON c.id=p.category_id WHERE p.id=? AND p.merchant_id=?`).get(productId,merchantId) as any;
export function images(value:string|null){try{const rows=JSON.parse(value||'[]');return Array.isArray(rows)?rows.filter(v=>typeof v==='string'):[];}catch{return [];}}
