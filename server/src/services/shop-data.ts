import {readTables} from '../core/db.js';
import {bookColumns} from './book-details.js';
export const id=(prefix:string)=>`${prefix}${Date.now().toString(36).toUpperCase()}${Math.random().toString(36).slice(2,7).toUpperCase()}`;
export const productSelect=`SELECT p.id,p.merchant_id merchantId,m.shop_name shop,p.category_id categoryId,c.name category,
 ${bookColumns},r.name,r.summary,r.age_range ageRange,r.price_cents priceCents,r.cover_url coverUrl,r.detail_images_json detailImages,
 r.audio_url audioUrl,r.ar_enabled arEnabled,r.ar_resource_url arResourceUrl,p.stock_available stock,p.status
 FROM products p JOIN ${readTables.product_revisions} r ON r.id=p.published_revision_id
 JOIN merchants m ON m.id=p.merchant_id JOIN categories c ON c.id=p.category_id`;
