import {createHash} from 'node:crypto';
import type {BusinessBaseline,SchemaPhase,Sqlite} from './types.js';
import {notificationReadFacts} from './notice-facts.js';

export const legacyResources=['addresses','after_sale_events','after_sale_items','after_sales','audit_logs','cart_items','categories','chat_messages','checkout_groups','comments','community_posts','community_uploads','conversation_reads','conversations','favorites','feedback_replies','feedbacks','merchants','notification_reads','notification_user_state','notifications','order_events','order_items','orders','payments','post_likes','product_revisions','products','ratings','refunds','sessions','settings','stock_movements','sync_entity_map','sync_media','sync_receipts','sync_snapshots','user_roles','users'] as const;
const projections:Record<string,string>={orders:'compat_orders',order_items:'compat_order_items',after_sales:'compat_after_sales',refunds:'compat_refunds',ratings:'compat_ratings',product_revisions:'compat_product_revisions',community_posts:'compat_community_posts'};
const jsonFields=new Set(['product_revisions.detail_images_json','product_revisions.contents_json','community_posts.images_json','after_sales.images_json','orders.address_snapshot_json']);
const hash=(v:string)=>createHash('sha256').update(v).digest('hex');
function canonical(value:unknown):string{
 if(Array.isArray(value))return '['+value.map(canonical).join(',')+']';
 if(value!==null&&typeof value==='object')return '{'+Object.entries(value).sort(([a],[b])=>a.localeCompare(b)).map(([k,v])=>JSON.stringify(k)+':'+canonical(v)).join(',')+'}';
 return JSON.stringify(value);
}
export function captureBusinessBaseline(db:Sqlite,phase:SchemaPhase):BusinessBaseline{
 const result:BusinessBaseline={modelVersion:2,resources:{},amounts:{},counters:{}};
 const exists=(name:string)=>!!db.prepare("SELECT 1 FROM sqlite_master WHERE name=? AND type IN ('table','view')").get(name);
 for(const resource of legacyResources){
  const source=phase==='normalized'?(projections[resource]||resource):resource;
  if(!exists(source)&&!(resource==='after_sale_items'&&phase==='legacy'))throw Error('Missing baseline relation: '+resource);
  const rows=resource==='after_sale_items'&&phase==='legacy'?legacySaleItemRows(db):db.prepare(`SELECT * FROM ${source}`).all() as Array<Record<string,unknown>>;
  const digests=rows.map(row=>{
   // Redundant read columns are compared as merged personal facts below.
   const normalized=Object.fromEntries(Object.entries(row).filter(([key])=>!(resource==='notifications'&&['is_read','read_at'].includes(key))&&!(resource==='notification_user_state'&&key==='read_at')).map(([key,value])=>[key,jsonFields.has(resource+'.'+key)&&typeof value==='string'?JSON.parse(value):value]));
   return hash(canonical(normalized));
  }).sort();
  result.resources[resource]={count:rows.length,rows:digests,digest:hash(digests.join('\n'))};
 }
 const readDigests=notificationReadFacts(db,phase).map(row=>hash(canonical(row))).sort();
 result.resources.notification_read_facts={count:readDigests.length,rows:readDigests,digest:hash(readDigests.join('\n'))};
 for(const [name,table,column] of [['orders','orders','total_cents'],['checkout','checkout_groups','total_cents'],['payments','payments','amount_cents'],['refunds','refunds','amount_cents']]){
  const source=phase==='normalized'?(projections[table]||table):table;
  result.amounts[name]=(db.prepare(`SELECT COALESCE(SUM(${column}),0) n FROM ${source}`).get() as {n:number}).n;
 }
 if(exists('sqlite_sequence'))for(const row of db.prepare('SELECT name,seq FROM sqlite_sequence ORDER BY name').all() as Array<{name:string;seq:number}>)result.counters[row.name]=row.seq;
 return result;
}
/** The exact historical full-order backfill defined by migration 011. */
export function legacySaleItemRows(db:Sqlite):Array<Record<string,unknown>>{
 if(db.prepare("SELECT 1 FROM sqlite_master WHERE type='table' AND name='after_sale_items'").get())return db.prepare('SELECT * FROM after_sale_items').all() as Array<Record<string,unknown>>;
 return db.prepare('SELECT a.id after_sale_id,i.id order_item_id,i.quantity,i.subtotal_cents amount_cents,i.quantity restock_quantity FROM after_sales a JOIN order_items i ON i.order_id=a.order_id WHERE i.subtotal_cents>0').all() as Array<Record<string,unknown>>;
}
