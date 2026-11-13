import type {Sqlite,PreflightReport,SchemaPhase} from './types.js';

const addressKeys=new Set(['id','user_id','recipient','phone','region','detail','is_default','created_at','updated_at','version']);
const stateMap:Record<string,string>={WAITING_PAYMENT:'PENDING_PAYMENT',PREPARING:'PAID',READY_TO_SHIP:'PAID',SHIPPED:'SHIPPED',IN_TRANSIT:'SHIPPED',OUT_FOR_DELIVERY:'SHIPPED',COMPLETED:'COMPLETED',CANCELLED:'CANCELLED'};
export function inspectNormalization(db:Sqlite):PreflightReport{
 const tables=new Set((db.prepare("SELECT name FROM sqlite_master WHERE type='table'").all() as Array<{name:string}>).map(r=>r.name));
 const orderColumns=tables.has('orders')?db.prepare('PRAGMA table_info(orders)').all() as Array<{name:string}>:[];
 const phase:SchemaPhase=orderColumns.some(c=>c.name==='user_id')?(tables.has('product_revision_images')?'expanded':'legacy'):'normalized';
 const report:PreflightReport={phase,integrityOk:false,foreignKeysOk:false,issues:[],canProceed:false};
 const issue=(code:string,entity:string,count:number,blocking=true)=>{if(count)report.issues.push({code,entity,count,blocking});};
 try{
  report.integrityOk=db.pragma('integrity_check',{simple:true})==='ok';issue('INTEGRITY_ERROR','database',report.integrityOk?0:1);
  const foreign=db.pragma('foreign_key_check') as unknown[];report.foreignKeysOk=foreign.length===0;issue('FOREIGN_KEY_ERROR','database',foreign.length);
  const required=['users','user_roles','merchants','products','product_revisions','checkout_groups','orders','order_items','payments','after_sales','refunds','community_posts','notifications','conversation_reads','chat_messages','conversations'];
  const missing=required.filter(t=>!tables.has(t));for(const name of missing)issue('MISSING_TABLE',name,1);
  if(missing.length)return report;
  // Until the target projector is installed, fail closed rather than validate the wrong columns.
  const views=new Set((db.prepare("SELECT name FROM sqlite_master WHERE type='view'").all() as Array<{name:string}>).map(r=>r.name));
  if(phase==='normalized'&&!views.has('compat_orders')){issue('MISSING_COMPATIBILITY_VIEWS','database',1);return report;}
  const order=phase==='normalized'?'compat_orders':'orders',item=phase==='normalized'?'compat_order_items':'order_items',sale=phase==='normalized'?'compat_after_sales':'after_sales',refund=phase==='normalized'?'compat_refunds':'refunds';
  const count=(code:string,entity:string,sql:string)=>{const result=db.prepare(sql).get() as {n:number};issue(code,entity,result.n);};
  count('ORDER_BUYER_MISMATCH','orders',`SELECT COUNT(*) n FROM ${order} o JOIN checkout_groups g ON g.id=o.checkout_group_id WHERE o.user_id<>g.user_id`);
  count('ORDER_EXPIRY_MISMATCH','orders',`SELECT COUNT(*) n FROM ${order} o JOIN checkout_groups g ON g.id=o.checkout_group_id WHERE o.expires_at<>g.expires_at`);
  count('SALE_OWNER_MISMATCH','after_sales',`SELECT COUNT(*) n FROM ${sale} a JOIN ${order} o ON o.id=a.order_id WHERE a.merchant_id<>o.merchant_id OR a.buyer_id<>o.user_id`);
  count('PUBLISHED_REVISION_MISMATCH','products',"SELECT COUNT(*) n FROM products p LEFT JOIN product_revisions r ON r.id=p.published_revision_id WHERE (p.published_revision_id IS NOT NULL AND (r.id IS NULL OR r.product_id<>p.id)) OR (p.status='ONLINE' AND (r.id IS NULL OR r.review_status<>'APPROVED'))");
  count('ITEM_SUBTOTAL_MISMATCH','order_items',`SELECT COUNT(*) n FROM ${item} WHERE subtotal_cents<>unit_price_cents*quantity OR unit_price_cents<0 OR quantity<=0`);
  count('ORDER_TOTAL_MISMATCH','orders',`SELECT COUNT(*) n FROM ${order} o WHERE total_cents<>(SELECT COALESCE(SUM(subtotal_cents),0) FROM ${item} i WHERE i.order_id=o.id)`);
  count('CHECKOUT_TOTAL_MISMATCH','checkout_groups',`SELECT COUNT(*) n FROM checkout_groups g WHERE total_cents<>(SELECT COALESCE(SUM(total_cents),0) FROM ${order} o WHERE o.checkout_group_id=g.id)`);
  count('PAYMENT_AMOUNT_MISMATCH','payments','SELECT COUNT(*) n FROM payments p JOIN checkout_groups g ON g.id=p.checkout_group_id WHERE p.amount_cents<>g.total_cents');
  count('REFUND_OVER_TOTAL','refunds',`SELECT COUNT(*) n FROM ${order} o WHERE (SELECT COALESCE(SUM(amount_cents),0) FROM ${refund} r WHERE r.order_id=o.id)>o.total_cents`);
  count('REFUND_ASSOCIATION_MISMATCH','refunds',`SELECT COUNT(*) n FROM ${refund} r JOIN ${sale} a ON a.id=r.after_sale_id WHERE r.order_id<>a.order_id OR r.amount_cents<>a.amount_cents OR a.status<>'COMPLETED'`);
  count('NEGATIVE_STOCK','products','SELECT COUNT(*) n FROM products WHERE stock_available<0 OR stock_reserved<0');
  count('ACTIVE_SALE_DUPLICATE','after_sales',`SELECT COUNT(*) n FROM (SELECT order_id FROM ${sale} WHERE status NOT IN ('REJECTED','CANCELLED','COMPLETED') GROUP BY order_id HAVING COUNT(*)>1)`);
  count('READ_CURSOR_MISMATCH','conversation_reads','SELECT COUNT(*) n FROM conversation_reads r LEFT JOIN chat_messages m ON m.id=r.last_message_id WHERE r.last_message_id<>0 AND (m.id IS NULL OR m.conversation_id<>r.conversation_id)');
  if(tables.has('after_sale_items')){
   count('SALE_ITEM_ORDER_MISMATCH','after_sale_items',`SELECT COUNT(*) n FROM after_sale_items x JOIN ${sale} a ON a.id=x.after_sale_id JOIN ${item} i ON i.id=x.order_item_id WHERE a.order_id<>i.order_id`);
   count('SALE_TOTAL_MISMATCH','after_sales',`SELECT COUNT(*) n FROM ${sale} a WHERE a.amount_cents<>(SELECT COALESCE(SUM(amount_cents),0) FROM after_sale_items x WHERE x.after_sale_id=a.id)`);
  }else if(phase==='legacy'){
   count('SALE_TOTAL_MISMATCH','after_sales',`SELECT COUNT(*) n FROM after_sales a WHERE a.amount_cents<>(SELECT COALESCE(SUM(subtotal_cents),0) FROM order_items i WHERE i.order_id=a.order_id)`);
  }
  let invalidStatus=0;for(const row of db.prepare(`SELECT status,logistics_status FROM ${order}`).all() as Array<{status:string;logistics_status:string}>)if(!stateMap[row.logistics_status]||stateMap[row.logistics_status]!==row.status)invalidStatus++;
  issue('ORDER_STATUS_MISMATCH','orders',invalidStatus);
  if(phase!=='normalized'){
   for(const [table,column] of [['product_revisions','detail_images_json'],['product_revisions','contents_json'],['community_posts','images_json'],['after_sales','images_json']]){
    let bad=0;for(const row of db.prepare(`SELECT ${column} value FROM ${table}`).all() as Array<{value:string}>)try{const values=JSON.parse(row.value);if(!Array.isArray(values)||values.some(v=>typeof v!=='string'||!v.trim()))bad++;}catch{bad++;}
    issue('INVALID_JSON_ARRAY',table+'.'+column,bad);
   }
  }
  let invalidAddress=0,unknown=0;for(const row of db.prepare(`SELECT address_snapshot_json value FROM ${order}`).all() as Array<{value:string}>)try{const v=JSON.parse(row.value);if(!v||Array.isArray(v)||typeof v!=='object')invalidAddress++;else if(Object.keys(v).some(k=>!addressKeys.has(k)))unknown++;}catch{invalidAddress++;}
  issue('INVALID_ADDRESS_SNAPSHOT','orders',invalidAddress);issue('UNKNOWN_ADDRESS_KEYS','orders',unknown);
 }catch{issue('PREFLIGHT_READ_ERROR','database',1);}
 report.canProceed=report.integrityOk&&report.foreignKeysOk&&!report.issues.some(i=>i.blocking);return report;
}
