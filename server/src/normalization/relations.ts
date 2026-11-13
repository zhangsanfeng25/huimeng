import type {OrderedRelation,Sqlite} from './types.js';
const definitions={
 product_revision_images:{parent:'product_revisions',json:'detail_images_json',owner:'revision_id',value:'url',limit:9},
 product_revision_contents:{parent:'product_revisions',json:'contents_json',owner:'revision_id',value:'title',limit:80},
 community_post_images:{parent:'community_posts',json:'images_json',owner:'post_id',value:'url',limit:9},
 after_sale_images:{parent:'after_sales',json:'images_json',owner:'after_sale_id',value:'url',limit:9}
} as const;
function config(relation:OrderedRelation){if(!Object.hasOwn(definitions,relation))throw Error('未知有序关系');return definitions[relation];}
function hasJson(db:Sqlite,parent:string,column:string){return (db.prepare(`PRAGMA table_info(${parent})`).all() as Array<{name:string}>).some(c=>c.name===column);}
export function readOrderedValues(db:Sqlite,relation:OrderedRelation,parentId:string):string[]{
 const d=config(relation);
 if(hasJson(db,d.parent,d.json)){
  const row=db.prepare(`SELECT ${d.json} value FROM ${d.parent} WHERE id=?`).get(parentId) as {value:string}|undefined;
  if(!row)throw Error('父记录不存在');const values=JSON.parse(row.value);
  if(!Array.isArray(values)||values.some(v=>typeof v!=='string'))throw Error('有序内容损坏');return values;
 }
 return (db.prepare(`SELECT ${d.value} value FROM ${relation} WHERE ${d.owner}=? ORDER BY position`).all(parentId) as Array<{value:string}>).map(r=>r.value);
}
export function replaceOrderedValues(db:Sqlite,relation:OrderedRelation,parentId:string,values:string[]):void{
 const d=config(relation);
 if(!Array.isArray(values)||values.length>d.limit||values.some(v=>typeof v!=='string'||!v.trim()))throw Error('有序内容不符合约束');
 db.transaction(()=>{
  if(!db.prepare(`SELECT 1 FROM ${d.parent} WHERE id=?`).get(parentId))throw Error('父记录不存在');
  if(db.prepare("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?").get(relation)){
   db.prepare(`DELETE FROM ${relation} WHERE ${d.owner}=?`).run(parentId);
   const insert=db.prepare(`INSERT INTO ${relation}(${d.owner},position,${d.value}) VALUES(?,?,?)`);
   values.forEach((v,i)=>insert.run(parentId,i,v));
  }
  if(hasJson(db,d.parent,d.json))db.prepare(`UPDATE ${d.parent} SET ${d.json}=? WHERE id=?`).run(JSON.stringify(values),parentId);
 })();
}
