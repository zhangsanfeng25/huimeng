import { db, now, tx } from '../src/core/db.js';
import { hashPassword } from '../src/core/security.js';

const timestamp = now();
const userInsert = db.prepare(`INSERT OR IGNORE INTO users
  (id,username,email,phone,nickname,password_hash,status,created_at,updated_at)
  VALUES (@id,@username,@email,@phone,@nickname,@password_hash,'ACTIVE',@created_at,@updated_at)`);
const existingUser = db.prepare('SELECT 1 FROM users WHERE id = ?');
const roleInsert = db.prepare('INSERT OR IGNORE INTO user_roles(user_id,role) VALUES(?,?)');
const merchantInsert = db.prepare(`INSERT OR IGNORE INTO merchants
  (id,owner_user_id,shop_name,description,approval_status,business_status,applied_at,updated_at)
  VALUES (@id,@owner,@shop,@description,'APPROVED','OPEN',@applied,@updated)`);
const categoryInsert = db.prepare(`INSERT OR IGNORE INTO categories
  (id,name,parent_name,sort_order,status,created_at,updated_at) VALUES(?,?,?,?, 'ENABLED',?,?)`);

tx(() => {
  const accounts = [
    { id:'A0001', username:'admin', email:'admin@huimeng.local', phone:null, nickname:'平台管理员', roles:['ADMIN'], password:'huimeng123' },
    { id:'MUSER01', username:'merchant_a', email:'merchant-a@demo.cn', phone:'13800001001', nickname:'童书优选', roles:['USER','MERCHANT'], password:'demo12345' },
    { id:'MUSER02', username:'merchant_b', email:'merchant-b@demo.cn', phone:'13800001002', nickname:'乐读童书', roles:['USER','MERCHANT'], password:'demo12345' },
    { id:'U10001', username:'xingxing', email:'xingxing@demo.cn', phone:'13800002001', nickname:'星星妈妈', roles:['USER'], password:'demo12345' },
    { id:'U10002', username:'xiaoxiong', email:'bear@demo.cn', phone:'13800002002', nickname:'小熊爸爸', roles:['USER'], password:'demo12345' },
    { id:'U10003', username:'yunduo', email:'cloud@demo.cn', phone:'13800002003', nickname:'云朵', roles:['USER'], password:'demo12345' }
  ];
  for (const account of accounts) {
    // Password hashing is expensive; demo accounts already stored on disk do
    // not need to be hashed again at every service startup.
    if (!existingUser.get(account.id)) {
      userInsert.run({ ...account, password_hash:hashPassword(account.password), created_at:timestamp, updated_at:timestamp });
    }
    for (const role of account.roles) roleInsert.run(account.id, role);
  }

  merchantInsert.run({ id:'M20001', owner:'MUSER01', shop:'童书优选', description:'精选优质儿童绘本', applied:timestamp, updated:timestamp });
  merchantInsert.run({ id:'M20002', owner:'MUSER02', shop:'乐读童书', description:'陪伴孩子快乐阅读', applied:timestamp, updated:timestamp });
  const categories = [
    ['C01','3–6岁启蒙','适龄阅读',10], ['C02','6–9岁成长','适龄阅读',20],
    ['C03','科普百科','主题分类',30], ['C04','经典绘本','主题分类',40]
  ];
  for (const row of categories) categoryInsert.run(...row,timestamp,timestamp);

  const names = ['晚安，小熊','森林里的邮递员','星空百科','我会交朋友','神奇的海洋世界','月亮，晚安','勇敢的小火车','云朵面包','昆虫观察日记','恐龙世界','四季的秘密','第一次上学','小狐狸的礼物','颜色去哪儿了','爱笑的鲸鱼','会飞的房子','爷爷的花园','情绪小怪兽','地球的一天','睡前故事集'];
  const productInsert = db.prepare(`INSERT OR IGNORE INTO products
    (id,merchant_id,category_id,published_revision_id,status,stock_available,stock_reserved,created_at,updated_at)
    VALUES (?,?,?,?, 'ONLINE',?,0,?,?)`);
  const revisionInsert = db.prepare(`INSERT OR IGNORE INTO product_revisions
    (id,product_id,version,name,summary,age_range,price_cents,cover_url,detail_images_json,audio_url,ar_enabled,review_status,submitted_at,reviewed_at,reviewed_by,created_at)
    VALUES (?,?,1,?,?,?,?,?,'[]',NULL,0,'APPROVED',?,?,?,?)`);
  names.forEach((name,index) => {
    const id=`P${String(index+1).padStart(4,'0')}`;
    const revision=`PR${String(index+1).padStart(4,'0')}`;
    const merchant=index%2===0?'M20001':'M20002';
    const category=`C0${index%4+1}`;
    const price=2980+(index%7)*300;
    productInsert.run(id,merchant,category,revision,30+index*3,timestamp,timestamp);
    revisionInsert.run(revision,id,name,`${name}，适合亲子共读的温暖绘本。`,index%2===0?'3–6岁':'6–9岁',price,`/public/books/${id.toLowerCase()}.png`,timestamp,timestamp,'A0001',timestamp);
  });

  const addressInsert=db.prepare(`INSERT OR IGNORE INTO addresses(id,user_id,recipient,phone,region,detail,is_default,created_at,updated_at) VALUES(?,?,?,?,?,?,1,?,?)`);
  addressInsert.run('ADDR001','U10001','林小雨','13800002001','辽宁省大连市','高新区星海路 18 号',timestamp,timestamp);
  addressInsert.run('ADDR002','U10002','周明','13800002002','辽宁省大连市','甘井子区春田路 6 号',timestamp,timestamp);

  const checkoutInsert=db.prepare(`INSERT OR IGNORE INTO checkout_groups(id,user_id,total_cents,status,version,idempotency_key,request_hash,created_at,expires_at) VALUES(?,?,?,?,1,?,?,?,?)`);
  const orderInsert=db.prepare(`INSERT OR IGNORE INTO orders(id,checkout_group_id,user_id,merchant_id,address_snapshot_json,total_cents,status,logistics_status,version,expires_at,paid_at,shipped_at,completed_at,cancelled_at,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,1,?,?,?,?,?,?,?)`);
  const itemInsert=db.prepare(`INSERT OR IGNORE INTO order_items(id,order_id,product_id,product_name,cover_url,unit_price_cents,quantity,subtotal_cents) VALUES(?,?,?,?,?,?,?,?)`);
  const orderSeeds=[
    ['DEMO_CG_01','DEMO_ORDER_PENDING','U10001','M20001','P0001','晚安，小熊',2980,'PENDING_PAYMENT'],
    ['DEMO_CG_02','DEMO_ORDER_PAID','U10002','M20002','P0002','森林里的邮递员',3280,'PAID'],
    ['DEMO_CG_03','DEMO_ORDER_SHIPPED','U10001','M20001','P0003','星空百科',3580,'SHIPPED'],
    ['DEMO_CG_04','DEMO_ORDER_COMPLETED','U10002','M20002','P0004','我会交朋友',3880,'COMPLETED'],
    ['DEMO_CG_05','DEMO_ORDER_CANCELLED','U10001','M20001','P0005','神奇的海洋世界',4180,'CANCELLED']
  ];
  for(const [groupId,orderId,userId,merchantId,productId,productName,amount,status] of orderSeeds){
    const paid=['PAID','SHIPPED','COMPLETED'].includes(String(status))?timestamp:null,shipped=['SHIPPED','COMPLETED'].includes(String(status))?timestamp:null,completed=status==='COMPLETED'?timestamp:null,cancelled=status==='CANCELLED'?timestamp:null;
    const logistics={PENDING_PAYMENT:'WAITING_PAYMENT',PAID:'PREPARING',SHIPPED:'OUT_FOR_DELIVERY',COMPLETED:'COMPLETED',CANCELLED:'CANCELLED'}[String(status)]||'WAITING_PAYMENT';
    checkoutInsert.run(groupId,userId,Number(amount),status==='PENDING_PAYMENT'?'PENDING':status==='CANCELLED'?'CLOSED':'PAID',`seed-${groupId}`,'seed',timestamp,new Date(Date.now()+86_400_000).toISOString());
    orderInsert.run(orderId,groupId,userId,merchantId,JSON.stringify({recipient:'演示收货人',phone:'13800000000',region:'辽宁省大连市',detail:'演示地址'}),Number(amount),status,logistics,new Date(Date.now()+86_400_000).toISOString(),paid,shipped,completed,cancelled,timestamp,timestamp);
    itemInsert.run(`DEMO_ITEM_${orderId}`,orderId,productId,productName,`/public/books/${String(productId).toLowerCase()}.png`,Number(amount),1,Number(amount));
  }
  // Keep the existing demo pending order payable after replacing the project.
  const demoExpiry=new Date(Date.now()+30*86_400_000).toISOString();
  db.prepare("UPDATE checkout_groups SET expires_at=? WHERE id='DEMO_CG_01' AND status='PENDING'").run(demoExpiry);
  db.prepare("UPDATE orders SET expires_at=? WHERE id='DEMO_ORDER_PENDING' AND status='PENDING_PAYMENT'").run(demoExpiry);

  const postInsert=db.prepare(`INSERT OR IGNORE INTO community_posts(id,author_user_id,author_role,title,content,cover_url,images_json,product_id,status,report_count,created_at,updated_at) VALUES(?,?,?,?,?,NULL,'[]',?,'PUBLIC',0,?,?)`);
  const postTitles=['今晚的亲子共读时光','如何选择第一本科普绘本','周末书单分享','孩子第一次独立读完一本书','睡前十分钟阅读记录','海洋主题绘本推荐','和小熊一起认识情绪','雨天里的家庭阅读角'];
  postTitles.forEach((title,index)=>postInsert.run(`TDEMO${String(index+1).padStart(2,'0')}`,index%2===0?'U10001':'MUSER01',index%2===0?'USER':'MERCHANT',title,`${title}，记录一段温暖、真实的阅读体验。`,`P${String(index%8+1).padStart(4,'0')}`,timestamp,timestamp));

  const noticeInsert=db.prepare(`INSERT OR IGNORE INTO notifications(id,user_id,audience,title,content,status,is_read,created_at) VALUES(?,NULL,?,?,?,'SENT',0,?)`);
  noticeInsert.run('NDEMO01','全部用户','亲子阅读周活动','连续七天记录阅读，可解锁活动纪念装饰。',timestamp);
  noticeInsert.run('NDEMO02','全部商家','商品图片规范更新','请上传清晰、无外框的绘本封面图片。',timestamp);
  noticeInsert.run('NDEMO03','全部用户','本地演示系统说明','支付和物流均为模拟操作，不产生真实费用。',timestamp);

  db.prepare(`INSERT OR IGNORE INTO ratings(id,order_item_id,user_id,product_id,stars,content,status,created_at) VALUES('RDEMO01','DEMO_ITEM_DEMO_ORDER_COMPLETED','U10002','P0004',5,'插图很温暖，孩子很喜欢。','PUBLIC',?)`).run(timestamp);

  const settingInsert=db.prepare(`INSERT OR IGNORE INTO settings(key,value,updated_at) VALUES(?,?,?)`);
  for(const [key,value] of Object.entries({theme:'light',accent:'violet',wallpaper:'none',siteName:'绘梦儿童管理网站'})) settingInsert.run(key,value,timestamp);
});

console.log('演示数据初始化完成：1 个管理员、2 个商家、3 个用户、20 本绘本');
