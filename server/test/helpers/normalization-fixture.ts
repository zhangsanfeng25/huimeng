import Database from 'better-sqlite3';
import {mkdtempSync,mkdirSync,readFileSync,readdirSync,rmSync,writeFileSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join,resolve,sep} from 'node:path';
import {fileURLToPath} from 'node:url';

export async function createNormalizationFixture(options:{through:'010'|'011';case:'standard'|'cross-shop-partial'|'legacy-notice'}){
 const directory=mkdtempSync(join(tmpdir(),'huimeng-normalization-'));
 const databasePath=join(directory,'fixture.db'),uploadDir=join(directory,'uploads');mkdirSync(uploadDir);
 const db=new Database(databasePath);db.pragma('foreign_keys=ON');
 try{
  const migrations=fileURLToPath(new URL('../../migrations/',import.meta.url));
  for(const file of readdirSync(migrations).filter(f=>f.endsWith('.sql')&&f.slice(0,3)<=options.through).sort()){
   db.exec(readFileSync(join(migrations,file),'utf8'));
   db.prepare('INSERT INTO schema_migrations(version,applied_at) VALUES(?,?)').run(file.slice(0,-4),'2026-10-24T00:00:00Z');
  }
  const stamp='2026-10-24T00:00:00Z',expires='2030-01-01T00:00:00Z';
  for(const id of ['U1','U2','MU1','MU2','A1']){
   db.prepare("INSERT INTO users(id,username,nickname,password_hash,created_at,updated_at) VALUES(?,?,?,'fixture-hash',?,?)").run(id,id,'测试'+id,stamp,stamp);
   db.prepare('INSERT INTO user_roles(user_id,role) VALUES(?,?)').run(id,id==='A1'?'ADMIN':id.startsWith('MU')?'MERCHANT':'USER');
  }
  db.prepare("INSERT INTO categories(id,name,parent_name,created_at,updated_at) VALUES('C1','测试分类','年龄分类',?,?)").run(stamp,stamp);
  for(let n=1;n<=2;n++){
   db.prepare("INSERT INTO merchants(id,owner_user_id,shop_name,approval_status,business_status,applied_at,updated_at) VALUES(?,?,?,'APPROVED','OPEN',?,?)").run('M'+n,'MU'+n,'测试店铺'+n,stamp,stamp);
   db.prepare("INSERT INTO products(id,merchant_id,category_id,status,stock_available,created_at,updated_at) VALUES(?,?,'C1','ONLINE',10,?,?)").run('P'+n,'M'+n,stamp,stamp);
   db.prepare("INSERT INTO product_revisions(id,product_id,version,name,summary,age_range,price_cents,detail_images_json,review_status,reviewed_by,created_at) VALUES(?,?,1,?,'测试简介','3–6岁',?,'[\"/public/a.png\",\"/public/a.png\",\"/public/b.png\"]','APPROVED','A1',?)").run('R'+n,'P'+n,'历史成交商品'+n,n===1?3000:4000,stamp);
   db.prepare('UPDATE products SET published_revision_id=? WHERE id=?').run('R'+n,'P'+n);
  }
  db.prepare("INSERT INTO checkout_groups(id,user_id,total_cents,status,idempotency_key,request_hash,created_at,expires_at) VALUES('G1','U1',10000,'PAID','fixture-checkout','fixture-request',?,?)").run(stamp,expires);
  for(let n=1;n<=2;n++){
   const amount=n===1?6000:4000;
   db.prepare("INSERT INTO orders(id,checkout_group_id,user_id,merchant_id,address_snapshot_json,total_cents,status,logistics_status,expires_at,paid_at,created_at,updated_at) VALUES(?,'G1','U1',?,?,?,'PAID','PREPARING',?,?,?,?)").run('O'+n,'M'+n,JSON.stringify({id:'ADDR1',user_id:'U1',recipient:'测试收货人',phone:'13800000000',region:'测试地区',detail:'成交时地址'}),amount,expires,stamp,stamp,stamp);
   db.prepare('INSERT INTO order_items(id,order_id,product_id,product_name,unit_price_cents,quantity,subtotal_cents) VALUES(?,?,?,?,?,?,?)').run('I'+n,'O'+n,'P'+n,'历史成交商品'+n,n===1?3000:4000,n===1?2:1,amount);
   db.prepare("INSERT INTO order_events(order_id,actor_id,action,to_status,created_at) VALUES(?,'U1','PAY','PREPARING',?)").run('O'+n,stamp);
  }
  db.prepare("INSERT INTO payments(id,checkout_group_id,amount_cents,method,idempotency_key,paid_at) VALUES('PAY1','G1',10000,'DEMO_BALANCE','fixture-pay',?)").run(stamp);
  db.prepare("INSERT INTO community_posts(id,author_user_id,author_role,title,content,images_json,created_at,updated_at) VALUES('POST1','U1','USER','测试帖子','测试正文','[\"/public/a.png\",\"/public/b.png\"]',?,?)").run(stamp,stamp);
  db.prepare("INSERT INTO notifications(id,user_id,audience,title,content,is_read,created_at) VALUES('N1','U1','指定账号','测试通知','测试通知正文',1,?)").run(stamp);
  if(options.case==='cross-shop-partial'){
   const count=options.through==='011'?2:1;
   for(let n=1;n<=count;n++){
    db.prepare("INSERT INTO after_sales(id,order_id,buyer_id,merchant_id,type,amount_cents,reason,status,client_id,created_at,updated_at) VALUES(?,'O1','U1','M1','REFUND_ONLY',1000,'测试补偿','COMPLETED',?,?,?)").run('AS'+n,'fixture-sale-'+n,stamp,stamp);
    if(options.through==='011')db.prepare("INSERT INTO after_sale_items(after_sale_id,order_item_id,quantity,amount_cents,restock_quantity) VALUES(?,'I1',1,1000,0)").run('AS'+n);
    db.prepare("INSERT INTO refunds(id,after_sale_id,order_id,amount_cents,method,status,created_at) VALUES(?,?,'O1',1000,'DEMO','COMPLETED',?)").run('RF'+n,'AS'+n,stamp);
   }
  }
  writeFileSync(join(uploadDir,'a.png'),Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl6sAAAAABJRU5ErkJggg==','base64'));
 }finally{db.close();}
 return {databasePath,uploadDir,async dispose(){
  const absolute=resolve(directory),parent=resolve(tmpdir());
  if(!absolute.startsWith(parent+sep)||!absolute.slice(parent.length+1).startsWith('huimeng-normalization-'))throw Error('拒绝清理非测试目录');
  rmSync(absolute,{recursive:true,force:true});
 }};
}
