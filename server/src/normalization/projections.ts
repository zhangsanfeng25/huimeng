import type {SchemaPhase,Sqlite} from './types.js';
export const addressFields=[['id','source_address_id'],['user_id','source_user_id'],['recipient','recipient'],['phone','phone'],['region','region'],['detail','detail'],['is_default','source_is_default'],['created_at','source_created_at'],['updated_at','source_updated_at'],['version','source_version']] as const;
export const businessStatusSql=(alias:string)=>`CASE ${alias}.logistics_status WHEN 'WAITING_PAYMENT' THEN 'PENDING_PAYMENT' WHEN 'PREPARING' THEN 'PAID' WHEN 'READY_TO_SHIP' THEN 'PAID' WHEN 'SHIPPED' THEN 'SHIPPED' WHEN 'IN_TRANSIT' THEN 'SHIPPED' WHEN 'OUT_FOR_DELIVERY' THEN 'SHIPPED' WHEN 'COMPLETED' THEN 'COMPLETED' WHEN 'CANCELLED' THEN 'CANCELLED' END`;
export function installCompatibilityViews(db:Sqlite,phase:SchemaPhase):void{
 const definitions:Record<string,string>=phase==='normalized'?{
  orders:`SELECT o.*,g.user_id,g.expires_at,${businessStatusSql('o')} status,
   (SELECT json_group_object(key,value) FROM (${addressFields.map(([key,col],i)=>`SELECT '${key}' key,a.${col} value WHERE (a.field_mask & ${2**i})<>0`).join(' UNION ALL ')})) address_snapshot_json
   FROM orders o JOIN checkout_groups g ON g.id=o.checkout_group_id LEFT JOIN order_address_snapshots a ON a.order_id=o.id`,
  order_items:'SELECT i.*,i.unit_price_cents*i.quantity subtotal_cents FROM order_items i',
  after_sales:`SELECT s.*,o.merchant_id,g.user_id buyer_id,q.client_id,
   (SELECT json_group_array(url) FROM (SELECT url FROM after_sale_images x WHERE x.after_sale_id=s.id ORDER BY position)) images_json
   FROM after_sales s JOIN orders o ON o.id=s.order_id JOIN checkout_groups g ON g.id=o.checkout_group_id LEFT JOIN after_sale_requests q ON q.after_sale_id=s.id`,
  refunds:'SELECT r.*,a.order_id FROM refunds r JOIN after_sales a ON a.id=r.after_sale_id',
  ratings:'SELECT r.*,i.product_id,g.user_id FROM ratings r JOIN order_items i ON i.id=r.order_item_id JOIN orders o ON o.id=i.order_id JOIN checkout_groups g ON g.id=o.checkout_group_id'
 }:Object.fromEntries(['orders','order_items','after_sales','refunds','ratings'].map(t=>[t,`SELECT * FROM ${t}`]));
 if(db.prepare("SELECT 1 FROM sqlite_master WHERE type='table' AND name='product_revisions'").get())definitions.product_revisions=phase==='normalized'?`SELECT r.*,
  (SELECT json_group_array(url) FROM (SELECT url FROM product_revision_images x WHERE x.revision_id=r.id ORDER BY position)) detail_images_json,
  (SELECT json_group_array(title) FROM (SELECT title FROM product_revision_contents x WHERE x.revision_id=r.id ORDER BY position)) contents_json
  FROM product_revisions r`:'SELECT * FROM product_revisions';
 if(db.prepare("SELECT 1 FROM sqlite_master WHERE type='table' AND name='community_posts'").get())definitions.community_posts=phase==='normalized'?`SELECT p.*,
  (SELECT json_group_array(url) FROM (SELECT url FROM community_post_images x WHERE x.post_id=p.id ORDER BY position)) images_json
  FROM community_posts p`:'SELECT * FROM community_posts';
 db.transaction(()=>{for(const [name,select] of Object.entries(definitions)){db.exec(`DROP VIEW IF EXISTS compat_${name}`);db.exec(`CREATE VIEW compat_${name} AS ${select}`);}})();
}
