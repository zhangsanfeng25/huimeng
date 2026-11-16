import {readFileSync} from 'node:fs';
import {db, normalizedSchema, now, readTables, tx} from '../src/core/db.js';
import {saveBookDetails} from '../src/services/book-details.js';

const titles=JSON.parse(readFileSync(new URL('../assets/category-books.json',import.meta.url),'utf8')) as Record<string,[string,string][]>;
const categories=db.prepare("SELECT id,name FROM categories WHERE status='ENABLED' ORDER BY id").all() as {id:string;name:string}[];
const count=db.prepare(`SELECT COUNT(*) AS total FROM products p JOIN ${readTables.product_revisions} r ON r.id=p.published_revision_id
  WHERE p.category_id=? AND p.status='ONLINE' AND p.stock_available>0 AND TRIM(r.summary)<>'' AND TRIM(r.author_name)<>''`);
const product=db.prepare(`INSERT INTO products
  (id,merchant_id,category_id,status,stock_available,created_at,updated_at) VALUES(?,?,?,'ONLINE',24,?,?)`);
const revision=db.prepare(`INSERT INTO product_revisions
  (id,product_id,version,name,summary,age_range,price_cents,review_status,submitted_at,reviewed_at,created_at)
  VALUES(?,?,1,?,?,?,?,'APPROVED',?,?,?)`);
const publish=db.prepare('UPDATE products SET published_revision_id=? WHERE id=?');
const knownProduct=db.prepare('SELECT 1 FROM products WHERE id=?');
const merchant=db.prepare("SELECT id FROM merchants WHERE id IN ('M20001','M20002') AND approval_status='APPROVED' AND business_status='OPEN' ORDER BY id").all() as {id:string}[];
const stamp=now();
let added=0;
tx(()=>{
  for(const {id,name} of categories){
    const needed=Math.max(0,3-(count.get(id) as {total:number}).total);
    if(!needed)continue;
    const books=titles[name];
    if(!books||!merchant.length)throw Error(`缺少分类书目或可用示例店铺：${name}`);
    for(const [index,[title,summary]] of books.entries()){
      const productId=`CATEGORY-BOOK-${id}-${index+1}`;
      if(knownProduct.get(productId))continue;
      const revisionId=productId+'-R1';
      const age=name==='0–3岁认知'?'0–3岁':name==='9岁以上阅读'?'9–12岁':index===0?'3–6岁':'6–9岁';
      product.run(productId,merchant[index%merchant.length].id,id,stamp,stamp);
      revision.run(revisionId,productId,title,summary,age,2980+index*500,stamp,stamp,stamp);
      publish.run(revisionId,productId);
      saveBookDetails(revisionId,{
        authorName:['林栀','许小禾','周一帆'][index],
        illustratorName:['苏叶','周米','江小白'][index],
        publisher:'绘梦原创绘本',
        tableOfContents:[`开篇｜${title}`,`共读｜认识${name}`,'读后｜说一说你的发现'],
        editorialReview:`适合${age}共读。读完后，可以结合「${title}」的情节，请孩子说出自己最想重看的一页。`,
        metadataSource:'fictional_demo',
      });
      added++;
      if((count.get(id) as {total:number}).total>=3)break;
    }
    if((count.get(id) as {total:number}).total<3)throw Error(`分类未达到三本：${name}`);
  }
});
console.log(JSON.stringify({added,categories:categories.length,schema:normalizedSchema?'normalized':'legacy'}));
