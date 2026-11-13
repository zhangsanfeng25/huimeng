import {db, readTables} from '../core/db.js';
import {productSelect} from './shop-data.js';

type Product = {id:string; name:string; priceCents:number; coverUrl:string|null; ageRange:string|null;
  summary:string|null; category:string; updatedAt:string};
export type RecommendedProduct = Pick<Product, 'id'|'name'|'priceCents'|'coverUrl'> & {reason:string};

let heatCache: {expires: number; values: Map<string, {posts:number; likes:number; comments:number}>} | undefined;

function heat() {
  if (heatCache && heatCache.expires > Date.now()) return heatCache.values;
  const cutoff = new Date(Date.now() - 30 * 86400_000).toISOString();
  const rows = db.prepare(`SELECT p.product_id id,COUNT(*) posts,
    SUM((SELECT COUNT(*) FROM post_likes WHERE post_id=p.id)) likes,
    SUM((SELECT COUNT(*) FROM comments WHERE post_id=p.id AND status='PUBLIC')) comments
    FROM ${readTables.community_posts} p
    WHERE p.status='PUBLIC' AND p.created_at>=? AND p.product_id IS NOT NULL GROUP BY p.product_id`)
    .all(cutoff) as Array<{id:string; posts:number; likes:number; comments:number}>;
  heatCache = {expires: Date.now() + 60_000, values:new Map(rows.map(row=>[row.id,row]))};
  return heatCache.values;
}

function ageRange(text: string) {
  const digit = (value:string) => '零一二三四五六七八九'.indexOf(value==='两'?'二':value);
  const normalized = text.normalize('NFKC').replace(/[零一二两三四五六七八九十]{1,3}(?=\s*(?:[-–—~～至到]|岁|个?月))/g, value => {
    if(!value.includes('十'))return String(digit(value));
    const [tens,ones] = value.split('十');
    return String((tens?digit(tens):1)*10+(ones?digit(ones):0));
  });
  const match = normalized.match(/\b(\d{1,2})\s*(?:(?:岁\s*)?[-–—~～至到]\s*(\d{1,2})\s*)?岁(\s*[+＋]|以上|以下)?/);
  if (!match) {
    const months=normalized.match(/\b(\d{1,2})\s*个?月/);
    return months ? [Number(months[1])/12,Number(months[1])/12] : undefined;
  }
  const age = Number(match[1]);
  if (match[3]?.includes('以下')) return [0, age];
  if (match[3]) return [age, Infinity];
  const end = Number(match[2] ?? age);
  return [Math.min(age,end), Math.max(age,end)];
}

const topics = ['动物','科普','情绪','习惯','睡前','冒险','友谊','自然','亲子','恐龙','想象','成长','勇气','数学','颜色'];

export function recommendationIntent(input: string, earlier: string[] = []) {
  let age:number[]|null|undefined, budget:number|null|undefined, wanted:string[]|undefined;
  for(const text of [input,...earlier]){
    if(age===undefined)age=/不限年龄|年龄不限/.test(text)?null:ageRange(text);
    if(budget===undefined){
      const match=text.normalize('NFKC').match(/(?:预算|不超过|低于|以内)[^\d]{0,4}(\d+(?:\.\d+)?)\s*元?|(?:(\d+(?:\.\d+)?)\s*元\s*(?:以内|以下))/);
      budget=/不限预算|预算不限/.test(text)?null:match?Math.round(Number(match[1]??match[2])*100):undefined;
    }
    if(wanted===undefined){
      const matches=topics.filter(topic=>text.includes(topic));
      if(/不限主题|主题不限/.test(text))wanted=[];
      else if(matches.length)wanted=matches;
    }
  }
  return {age:age??undefined,budget:budget??undefined,wanted:wanted??[],
    clear:age!==undefined||budget!==undefined||wanted!==undefined||/不限|直接推荐|都可以/.test(input)};
}

export function recommendProducts({age,budget,wanted}: ReturnType<typeof recommendationIntent>): RecommendedProduct[] {
  const recent = heat();
  const products = db.prepare(`${productSelect.replace('SELECT p.id,','SELECT p.id,p.updated_at updatedAt,')}
    WHERE p.status='ONLINE' AND p.stock_available>0 AND m.approval_status='APPROVED' AND m.business_status='OPEN'`).all() as Product[];
  return products.filter(item=>{
    if (budget !== undefined && item.priceCents > budget) return false;
    if (!age) return true;
    const range = ageRange(item.ageRange ?? '');
    return Boolean(range && range[0] <= age[1] && age[0] <= range[1]);
  }).map(item=>{
    const counts = recent.get(item.id);
    const matches = wanted.filter(topic=>[item.name,item.category,item.summary].join(' ').includes(topic));
    const reasons = [
      age && item.ageRange ? `标注适读年龄 ${item.ageRange}` : '',
      matches.length ? `内容涉及${matches.join('、')}` : '',
      counts ? `近 30 天有 ${counts.posts} 篇关联帖子、${counts.likes} 次点赞和 ${counts.comments} 条公开评论` : '',
    ].filter(Boolean);
    return {item,matches:matches.length,score:counts ? counts.posts + counts.likes + counts.comments * 2 : 0,
      reason:reasons.join('，') || (item.summary || item.category || '当前在售且有库存').slice(0, 95)};
  }).filter(result=>!wanted.length||result.matches>0)
    .sort((a,b)=>b.score-a.score||b.matches-a.matches||b.item.updatedAt.localeCompare(a.item.updatedAt)||a.item.id.localeCompare(b.item.id))
    .slice(0,3).map(({item:{id,name,priceCents,coverUrl},reason})=>({id,name,priceCents,coverUrl,reason}));
}

function orderWindow() {
  const today = new Date();
  today.setUTCHours(0,0,0,0);
  const day = 86400_000;
  return {
    days:Array.from({length:30},(_,index)=>new Date(today.getTime()-(29-index)*day).toISOString().slice(0,10)),
    start:new Date(today.getTime()-29*day).toISOString(),
    end:new Date(today.getTime()+day).toISOString(),
  };
}

export function aiDailyOrders() {
  const {days,start,end}=orderWindow();
  const rows=db.prepare(`SELECT substr(created_at,1,10) date,COUNT(*) orders FROM ${readTables.orders}
    WHERE created_at>=? AND created_at<? GROUP BY 1`).all(start,end) as Array<{date:string;orders:number}>;
  const totals=new Map(rows.map(row=>[row.date,row.orders]));
  return days.map(date=>({date,orders:totals.get(date)??0}));
}

export function aiShopProducts(merchantId:string,message:string) {
  return db.prepare(`SELECT p.id,r.name,r.age_range ageRange,r.price_cents priceCents,
      substr(r.summary,1,300) summary
    FROM products p JOIN ${readTables.product_revisions} r ON r.id=(
      SELECT id FROM ${readTables.product_revisions} WHERE product_id=p.id ORDER BY version DESC LIMIT 1)
    WHERE p.merchant_id=?
    ORDER BY (instr(?,p.id)>0 OR (r.name<>'' AND instr(?,r.name)>0)) DESC,p.updated_at DESC,p.id LIMIT 6`)
    .all(merchantId,message,message) as Array<{id:string;name:string;ageRange:string;priceCents:number;summary:string|null}>;
}

export function aiCommunityTopics() {
  const cutoff=new Date(Date.now()-30*86400_000).toISOString();
  return db.prepare(`WITH terms(topic) AS (VALUES ${topics.map(()=>'(?)').join(',')}),
    publicPosts AS (
      SELECT id,title,content FROM ${readTables.community_posts} WHERE status='PUBLIC'
    ), recentContent(body,kind) AS (
      SELECT title||' '||content,'post' FROM ${readTables.community_posts} WHERE status='PUBLIC' AND created_at>=?
      UNION ALL
      SELECT c.content,'comment' FROM comments c JOIN publicPosts p ON p.id=c.post_id WHERE c.status='PUBLIC' AND c.created_at>=?
    )
    SELECT topic,SUM(kind='post') posts,SUM(kind='comment') comments
    FROM terms JOIN recentContent ON instr(body,topic)>0
    GROUP BY topic ORDER BY posts+comments DESC,topic LIMIT 8`)
    .all(...topics,cutoff,cutoff) as Array<{topic:string;posts:number;comments:number}>;
}

export function aiMetrics(merchantId?: string) {
  const {start,end}=orderWindow();
  const orders = merchantId
    ? db.prepare(`SELECT COUNT(*) total FROM ${readTables.orders} WHERE merchant_id=? AND created_at>=? AND created_at<?`).get(merchantId,start,end)
    : db.prepare(`SELECT COUNT(*) total FROM ${readTables.orders} WHERE created_at>=? AND created_at<?`).get(start,end);
  const inventory = merchantId
    ? db.prepare("SELECT COUNT(*) online,SUM(stock_available=0) outOfStock FROM products WHERE status='ONLINE' AND merchant_id=?").get(merchantId)
    : db.prepare("SELECT COUNT(*) online,SUM(stock_available=0) outOfStock FROM products WHERE status='ONLINE'").get();
  return {...orders as {total:number}, ...inventory as {online:number; outOfStock:number|null}};
}
