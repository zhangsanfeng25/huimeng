import {mkdirSync,writeFileSync} from 'node:fs';
import {resolve,join} from 'node:path';
import {createHash} from 'node:crypto';
import {db} from '../src/core/db.js';
import {fictionalBooks,fillFictionalBookDetails,bookFields} from '../src/services/book-details.js';

const directory=resolve(process.cwd(),'../output/book-details-20261017');
const books=fictionalBooks();
const plan=books.map(book=>({id:book.id,name:book.name,summaryCharacters:book.summary.length,chapters:book.tableOfContents.length}));
if(!process.argv.includes('--apply')){console.log(JSON.stringify({mode:'dry-run',books:plan},null,2));db.close();}
else{
 mkdirSync(directory,{recursive:true});
 const stamp=new Date().toISOString().replace(/[:.]/g,'-');
 const backup=join(directory,`before-book-details-${stamp}.db`);
 function protectedDigest(){
  const tables=db.prepare("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name<>'schema_migrations' ORDER BY name").all() as {name:string}[];
  const values:Record<string,string>={};
  for(const {name} of tables){
   const ignored=name==='product_revisions'?new Set(['summary',...Object.values(bookFields)]):name==='products'?new Set(['version','updated_at']):new Set<string>();
   const rows=(db.prepare(`SELECT * FROM "${name.replaceAll('"','""')}"`).all() as Record<string,unknown>[]).map(row=>JSON.stringify(Object.fromEntries(Object.keys(row).filter(key=>!ignored.has(key)).sort().map(key=>[key,row[key]])))).sort();
   values[name]=createHash('sha256').update(JSON.stringify(rows)).digest('hex');
  }return values;
 }
 try{
  await db.backup(backup);
  const before=protectedDigest();
  await import('./migrate.js');
  const changed=db.transaction(()=>{const rows=fillFictionalBookDetails();if(JSON.stringify(protectedDigest())!==JSON.stringify(before))throw Error('受保护的业务数据发生变化，已回滚资料填充');return rows;})();
  const check=db.pragma('quick_check');
  const result={backup,changed,protectedDataUnchanged:true,quickCheck:check,books:plan};
  writeFileSync(join(directory,`fill-result-${stamp}.json`),JSON.stringify(result,null,2));console.log(JSON.stringify(result,null,2));
 }finally{db.close();}
}
