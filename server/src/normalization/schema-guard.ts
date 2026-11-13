import type {Sqlite} from './types.js';
import {legacyResources} from './baseline.js';
/** Run before importing routers: several prepare statements during module load. */
export function assertSupportedSchema(db:Sqlite,options:{allowLegacyUpgrade?:boolean}={}):'empty'|'legacy'|'normalized'{
 const tables=new Set((db.prepare("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'").all() as Array<{name:string}>).map(row=>row.name));
 if(!tables.size){if(options.allowLegacyUpgrade)return 'empty';throw Error('数据库尚未初始化，请先通过控制中心完成初始化维护');}
 const reject=():never=>{throw Error('数据库结构未完成或与当前代码不兼容，请停止服务并进入数据库维护；不会自动执行规范化切换');};
 if(!tables.has('orders'))return reject();
 const columns=(table:string)=>new Set((db.prepare(`PRAGMA table_info(${table})`).all() as Array<{name:string}>).map(row=>row.name));
 const normalized=!columns('orders').has('user_id');
 for(const table of legacyResources)if(!tables.has(table)&&!(table==='after_sale_items'&&options.allowLegacyUpgrade&&!normalized))return reject();
 if(!normalized){if(tables.has('product_revision_images')||tables.has('order_address_snapshots')||tables.has('notification_read_receipts'))return reject();return 'legacy';}
 for(const table of ['order_address_snapshots','after_sale_requests','product_revision_images','product_revision_contents','community_post_images','after_sale_images','notification_read_receipts'])if(!tables.has(table))return reject();
 for(const [table,removed] of Object.entries({orders:['status','address_snapshot_json','expires_at'],order_items:['subtotal_cents'],after_sales:['buyer_id','merchant_id','client_id','images_json'],refunds:['order_id'],ratings:['user_id','product_id'],product_revisions:['detail_images_json','contents_json'],community_posts:['images_json'],notifications:['is_read','read_at'],notification_user_state:['read_at']}))if(removed.some(c=>columns(table).has(c)))return reject();
 try{
  for(const table of ['orders','order_items','after_sales','refunds','ratings','product_revisions','community_posts'])db.prepare(`SELECT * FROM compat_${table} LIMIT 0`).all();
  if(db.pragma('integrity_check',{simple:true})!=='ok'||(db.pragma('foreign_key_check') as unknown[]).length)return reject();
 }catch{return reject();}
 return 'normalized';
}
