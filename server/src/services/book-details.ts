import {existsSync,readFileSync} from 'node:fs';
import {z} from 'zod';
import {db,now} from '../core/db.js';

const date=z.string().refine(value=>value===''||(/^\d{4}-\d{2}-\d{2}$/.test(value)&&Number.isFinite(Date.parse(value))&&new Date(value).toISOString().slice(0,10)===value),'请填写有效的年月日');
export const bookInput=z.object({
 authorName:z.string().trim().max(100).optional(),illustratorName:z.string().trim().max(100).optional(),
 publisher:z.string().trim().max(160).optional(),publicationDate:date.optional(),
 authorBio:z.string().trim().max(3000).optional(),illustratorBio:z.string().trim().max(3000).optional(),
 tableOfContents:z.array(z.string().trim().min(1).max(180)).max(80).optional(),
 editorialReview:z.string().trim().max(1500).optional(),metadataSource:z.enum(['','fictional_demo','manual']).optional()
});
export const bookFields={authorName:'author_name',illustratorName:'illustrator_name',publisher:'publisher',publicationDate:'publication_date',authorBio:'author_bio',illustratorBio:'illustrator_bio',tableOfContents:'contents_json',editorialReview:'editorial_review',metadataSource:'metadata_source'} as const;
export const bookColumns=Object.entries(bookFields).map(([key,column])=>`r.${column} ${key}`).join(',');
export function readBookDetails(value:any):Record<string,any>{
 const result:Record<string,any>={};
 for(const [key,column] of Object.entries(bookFields))result[key]=value?.[key]??value?.[column]??(key==='tableOfContents'?[]:'');
 if(typeof result.tableOfContents==='string')try{result.tableOfContents=JSON.parse(result.tableOfContents);}catch{result.tableOfContents=[];}
 if(!Array.isArray(result.tableOfContents))result.tableOfContents=[];
 return result;
}
export function saveBookDetails(revisionId:string,value:Record<string,unknown>){
 const keys=Object.keys(bookFields).filter(key=>value[key]!==undefined) as Array<keyof typeof bookFields>;
 if(!keys.length)return;
 db.prepare(`UPDATE product_revisions SET ${keys.map(key=>`${bookFields[key]}=?`).join(',')} WHERE id=?`)
  .run(...keys.map(key=>key==='tableOfContents'?JSON.stringify(value[key]):value[key]),revisionId);
}
export function fictionalBooks():any[]{
 // The app build emits dist/services; seed's rootDir build emits dist/src/services.
 const paths=['../../assets/fictional-books.json','../../../assets/fictional-books.json'].map(path=>new URL(path,import.meta.url));
 const asset=paths.find(path=>existsSync(path));
 if(!asset)throw new Error('Missing server/assets/fictional-books.json');
 return JSON.parse(readFileSync(asset,'utf8'));
}
/** Only enrich the untouched demo editions. This does not reseed accounts or transactions. */
export function fillFictionalBookDetails(bumpVersion=true):Array<{id:string;revisionId:string;name:string}>{
 return db.transaction(()=>{
  const changed:Array<{id:string;revisionId:string;name:string}>=[];const stamp=now();
  for(const book of fictionalBooks()){
   const row=db.prepare('SELECT r.*,p.published_revision_id FROM products p JOIN product_revisions r ON r.id=p.published_revision_id WHERE p.id=?').get(book.id) as any;
   if(!row||row.name!==book.name||row.metadata_source||Object.entries(bookFields).some(([key,column])=>key!=='tableOfContents'&&row[column])||row.contents_json!=='[]')continue;
   const parsed=bookInput.parse(book);saveBookDetails(row.id,parsed);
   if(row.summary===`${book.name}，适合亲子共读的温暖绘本。`)db.prepare('UPDATE product_revisions SET summary=? WHERE id=?').run(book.summary,row.id);
   if(bumpVersion)db.prepare('UPDATE products SET version=version+1,updated_at=? WHERE id=?').run(stamp,book.id);
   changed.push({id:book.id,revisionId:row.id,name:book.name});
  }
  return changed;
 })();
}
