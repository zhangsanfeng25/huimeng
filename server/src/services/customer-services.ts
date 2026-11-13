import {readTables} from '../core/db.js';
import {z} from 'zod';
import {db,now,normalizedSchema} from '../core/db.js';
import {replaceOrderedValues} from '../normalization/relations.js';
import {audit} from '../core/http.js';
import {id} from './shop-data.js';
import type {BusinessContext} from './context.js';
import type {Actor} from '../sync/types.js';
import {SyncError} from '../sync/types.js';
import {broadcastPrivate} from '../core/events.js';
import {refundableItems,selectSaleItems,saleItems,refundedAmount} from './after-sale-items.js';

type Row=Record<string,any>;
export const afterSaleColumns=`COALESCE((SELECT status FROM after_sales WHERE order_id=o.id ORDER BY created_at DESC,rowid DESC LIMIT 1),'') after_sale_state,COALESCE((SELECT id FROM after_sales WHERE order_id=o.id ORDER BY created_at DESC,rowid DESC LIMIT 1),'') after_sale_id,COALESCE((SELECT SUM(amount_cents) FROM ${readTables.refunds} WHERE order_id=o.id),0) refunded_cents`;
/** Recorded actions only. Order ownership must be checked by the caller before exposing them. */
export function orderServiceEvents(orderId:string){
 return db.prepare(`SELECT e.id,e.action,e.note,e.created_at createdAt,a.type FROM after_sale_events e JOIN ${readTables.after_sales} a ON a.id=e.after_sale_id WHERE a.order_id=? ORDER BY e.created_at,e.id`).all(orderId);
}
const text=z.string().trim().min(1).max(2000);
const client=z.string().trim().min(1).max(100);
function fail(message:string,status=409):never{throw new SyncError(status,'SERVICE_RULE',message);}
function input<T>(schema:z.ZodType<T>,body:unknown):T{const parsed=schema.safeParse(body);if(!parsed.success)fail('请检查必填内容、长度及格式',400);return parsed.data;}
const staff=(a:Actor)=>a.roles.some(r=>['ADMIN','OPERATOR','SUPPORT'].includes(r));
const writer=(a:Actor)=>a.roles.some(r=>['ADMIN','OPERATOR'].includes(r));
function merchant(a:Actor){return a.activeRole==='MERCHANT'&&!!a.merchantId;}
function role(a:Actor){return merchant(a)?'MERCHANT':'USER';}
function get(table:string,key:string){const row=db.prepare(`SELECT * FROM ${readTables[table]||table} WHERE id=?`).get(key) as Row|undefined;if(!row)fail('记录不存在',404);return row;}
function canSee(a:Actor,kind:string,row:Row,admin=false){
 if(admin){if(!staff(a))fail('无权查看管理数据',403);return;}
 const allowed=kind==='feedback'?row.user_id===a.id:merchant(a)?row.merchant_id===a.merchantId:row.buyer_id===a.id;
 if(!allowed)fail('无权访问此记录',403);
}
function notify(userId:string,title:string,content:string,audience='指定账号'){db.prepare("INSERT INTO notifications(id,user_id,audience,title,content,status,created_at) VALUES(?,?,?,?,?,'SENT',?)").run(id('N'),userId,audience,title,content,now());}
function owner(merchantId:string){return (db.prepare('SELECT owner_user_id id FROM merchants WHERE id=?').get(merchantId) as Row).id as string;}
function saleEvent(a:Actor,sale:Row,action:string,note=''){
 db.prepare('INSERT INTO after_sale_events(after_sale_id,actor_id,action,note,created_at) VALUES(?,?,?,?,?)').run(sale.id,a.id,action,note,now());
 notify(sale.buyer_id,'售后进度更新',`售后单 ${sale.id}：${note||action}`,'用户售后消息');
 notify(owner(sale.merchant_id),'售后进度更新',`售后单 ${sale.id}：${note||action}`,'商家售后消息');
}
export function hasBlockingAfterSale(orderId:string){return !!db.prepare(`SELECT 1 FROM ${readTables.after_sales} WHERE order_id=? AND status NOT IN ('REJECTED','CANCELLED','COMPLETED')`).get(orderId)||!!db.prepare(`SELECT 1 FROM ${readTables.orders} WHERE id=? AND total_cents>0 AND total_cents<=(SELECT COALESCE(SUM(amount_cents),0) FROM ${readTables.refunds} WHERE order_id=?)`).get(orderId,orderId);}
export function orderAfterSale(a:Actor,orderId:string){
 const order=get('orders',orderId);canSee(a,'after-sales',{buyer_id:order.user_id,merchant_id:order.merchant_id});
 return {order:{id:order.id,status:order.status,total_cents:order.total_cents,refunded_cents:refundedAmount(orderId),merchant_id:order.merchant_id},items:refundableItems(orderId),sale:db.prepare(`SELECT * FROM ${readTables.after_sales} WHERE order_id=? ORDER BY created_at DESC,(SELECT source.rowid FROM after_sales source WHERE source.id=${readTables.after_sales}.id) DESC LIMIT 1`).get(orderId)||null};
}

export function serviceList(a:Actor,kind:string,admin=false):Row[]{
 if(admin&&!staff(a))fail('无权查看管理数据',403);
 if(kind==='after-sales')return db.prepare(`SELECT s.*,m.shop_name shop,u.nickname buyer FROM ${readTables.after_sales} s JOIN merchants m ON m.id=s.merchant_id JOIN users u ON u.id=s.buyer_id ${admin?'':merchant(a)?'WHERE s.merchant_id=?':'WHERE s.buyer_id=?'} ORDER BY s.updated_at DESC LIMIT 200`).all(...(admin?[]:[merchant(a)?a.merchantId:a.id])) as Row[];
 if(kind==='feedback')return db.prepare(`SELECT f.*,u.nickname submitter FROM feedbacks f JOIN users u ON u.id=f.user_id ${admin?'':'WHERE f.user_id=?'} ORDER BY f.updated_at DESC LIMIT 200`).all(...(admin?[]:[a.id])) as Row[];
 if(kind==='conversations'){
  if(admin)fail('管理员不通过普通会话接口读取私人聊天',403);
  return db.prepare(`SELECT c.*,m.shop_name shop,u.nickname buyer,
   (SELECT content FROM chat_messages WHERE conversation_id=c.id ORDER BY id DESC LIMIT 1) last_message,
   (SELECT COUNT(*) FROM chat_messages x WHERE x.conversation_id=c.id AND x.sender_role<>? AND x.id>COALESCE((SELECT last_message_id FROM conversation_reads WHERE conversation_id=c.id AND reader_role=?),0)) unread
   FROM conversations c JOIN merchants m ON m.id=c.merchant_id JOIN users u ON u.id=c.buyer_id WHERE ${merchant(a)?'c.merchant_id':'c.buyer_id'}=? ORDER BY c.updated_at DESC LIMIT 200`).all(role(a),role(a),merchant(a)?a.merchantId:a.id) as Row[];
 }
 fail('未知服务类型',404);
}
export function serviceDetail(a:Actor,kind:string,key:string,admin=false,before=0):Row{
 const table=kind==='after-sales'?'after_sales':kind==='feedback'?'feedbacks':kind==='conversations'?'conversations':'';if(!table)fail('未知服务类型',404);
 const row=get(table,key);canSee(a,kind,row,admin);
 if(kind==='after-sales'){
  const order=get('orders',row.order_id);
  const canApplyAgain=['REJECTED','CANCELLED','COMPLETED'].includes(row.status)&&['PAID','SHIPPED','COMPLETED'].includes(order.status)&&!hasBlockingAfterSale(order.id)&&refundableItems(order.id).some(item=>item.refundable_cents>0&&item.returnable_quantity>0);
  return {...row,can_apply_again:canApplyAgain,images:JSON.parse(row.images_json),events:db.prepare('SELECT e.*,u.nickname actor FROM after_sale_events e JOIN users u ON u.id=e.actor_id WHERE after_sale_id=? ORDER BY e.id').all(key),items:saleItems(key),refund:db.prepare(`SELECT * FROM ${readTables.refunds} WHERE after_sale_id=?`).get(key)||null};
 }
 if(kind==='feedback')return {...row,replies:db.prepare('SELECT r.*,u.nickname sender FROM feedback_replies r JOIN users u ON u.id=r.sender_id WHERE feedback_id=? ORDER BY r.id').all(key)};
 if(admin)fail('无权通过管理接口读取私人聊天',403);
 const messages=db.prepare(`SELECT x.*,u.nickname sender,p.name product_name,oi.product_name order_product FROM chat_messages x JOIN users u ON u.id=x.sender_id LEFT JOIN ${readTables.product_revisions} p ON p.id=(SELECT published_revision_id FROM products WHERE id=x.product_id) LEFT JOIN ${readTables.order_items} oi ON oi.id=(SELECT id FROM ${readTables.order_items} WHERE order_id=x.order_id LIMIT 1) WHERE x.conversation_id=? AND (?=0 OR x.id<?) ORDER BY x.id DESC LIMIT 50`).all(key,before,before) as Row[];
 return {...row,shop:(db.prepare('SELECT shop_name name FROM merchants WHERE id=?').get(row.merchant_id) as Row).name,buyer:(db.prepare('SELECT nickname name FROM users WHERE id=?').get(row.buyer_id) as Row).name,messages:messages.reverse(),hasMore:messages.length===50,readerRole:role(a),peerRead:(db.prepare('SELECT last_message_id n FROM conversation_reads WHERE conversation_id=? AND reader_role<>?').get(key,role(a)) as Row)?.n||0};
}

function saleAction(ctx:BusinessContext):Row{
 const {actor:a,payload:b,params}=ctx,key=params.id,action=String(b.action);
 if(action==='create'){
  if(merchant(a)||!a.roles.includes('USER'))fail('请以买家身份申请售后',403);
  const v=input(z.object({orderId:client,type:z.enum(['REFUND_ONLY','RETURN_REFUND']),reason:z.string().trim().min(1).max(200),description:z.string().trim().max(2000).default(''),images:z.array(z.string()).max(3).default([]),clientId:client,items:z.array(z.object({orderItemId:client,quantity:z.number().int().positive(),amountCents:z.number().int().positive()})).min(1).max(100).optional()}),b);
  const previous=db.prepare(`SELECT * FROM ${readTables.after_sales} WHERE buyer_id=? AND client_id=?`).get(a.id,v.clientId) as Row|undefined;
  if(previous){const oldItems=saleItems(previous.id);const changed=v.items&&(v.items.length!==oldItems.length||v.items.some(i=>!oldItems.some(o=>o.id===i.orderItemId&&o.quantity===i.quantity&&o.amount_cents===i.amountCents)));if(previous.order_id!==v.orderId||previous.type!==v.type||previous.reason!==v.reason||previous.description!==v.description||previous.images_json!==JSON.stringify(v.images)||changed)fail('同一请求标识不能用于不同申请');return {data:previous,reused:true};}
  const order=get('orders',v.orderId);if(order.user_id!==a.id)fail('无权为此订单申请售后',403);
  if(!['PAID','SHIPPED','COMPLETED'].includes(order.status))fail('仅已付款订单可申请售后');
  if(hasBlockingAfterSale(order.id))fail('此订单已有进行中或已全额退款的售后');
  if(v.type==='RETURN_REFUND'&&order.status==='PAID')fail('未发货订单请选择仅退款');
  const items=selectSaleItems(order,v.type,v.items),amount=items.reduce((sum,i)=>sum+i.amountCents,0);
  if(v.images.some(url=>!db.prepare('SELECT 1 FROM community_uploads WHERE path=? AND user_id=?').get(url,a.id)))fail('凭证必须使用本人上传的图片',400);
  const stamp=now(),sid=id('AS');
  if(normalizedSchema){
   db.prepare("INSERT INTO after_sales(id,order_id,type,amount_cents,reason,description,status,created_at,updated_at) VALUES(?,?,?,?,?,?,'MERCHANT_REVIEW',?,?)").run(sid,order.id,v.type,amount,v.reason,v.description,stamp,stamp);
   db.prepare('INSERT INTO after_sale_requests(buyer_id,client_id,after_sale_id) VALUES(?,?,?)').run(a.id,v.clientId,sid);
   replaceOrderedValues(db,'after_sale_images',sid,v.images);
  }else db.prepare("INSERT INTO after_sales(id,order_id,buyer_id,merchant_id,type,amount_cents,reason,description,images_json,status,client_id,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,'MERCHANT_REVIEW',?,?,?)").run(sid,order.id,a.id,order.merchant_id,v.type,amount,v.reason,v.description,JSON.stringify(v.images),v.clientId,stamp,stamp);
  const insert=db.prepare('INSERT INTO after_sale_items(after_sale_id,order_item_id,quantity,amount_cents,restock_quantity) VALUES(?,?,?,?,?)');
  for(const item of items)insert.run(sid,item.orderItemId,item.quantity,item.amountCents,item.restockQuantity);
  const row=get('after_sales',sid);saleEvent(a,row,'CREATE','买家提交售后');return {data:row};
 }
 const row=get('after_sales',key),isAdmin=writer(a);canSee(a,'after-sales',row,isAdmin);
 if(action==='refund'&&row.status==='COMPLETED'&&isAdmin)return {ok:true,reused:true};
 let next='',note=String(b.note||'').trim();
 if(action==='cancel'){
  if(merchant(a)||row.buyer_id!==a.id)fail('无权撤销',403);
  if(!['MERCHANT_REVIEW','ADMIN_REVIEW','WAITING_RETURN'].includes(row.status))fail('当前状态不能撤销');next='CANCELLED';note='买家撤销售后申请';
 }else if(action==='merchant-review'){
  if(!merchant(a)||row.merchant_id!==a.merchantId)fail('无权初审',403);
  if(row.status!=='MERCHANT_REVIEW')fail('状态已变化，请刷新');
  const v=input(z.object({approve:z.boolean(),note:z.string().trim().max(1000),returnAddress:z.string().trim().max(1000).default('')}),b);
  if(!v.approve&&!v.note)fail('不同意须填写原因',400);note=(v.approve?'商家同意：':'商家不同意：')+v.note;
  db.prepare('UPDATE after_sales SET merchant_note=?,return_address=? WHERE id=?').run(note,v.returnAddress,key);next='ADMIN_REVIEW';
 }else if(action==='approve'||action==='reject'){
  if(!isAdmin)fail('无权审核',403);if(row.status!=='ADMIN_REVIEW')fail('状态已变化，请刷新');if(!note||note.length>1000)fail('请填写 1 至 1000 字审核说明',400);
  if(action==='reject')next='REJECTED';else if(row.type==='RETURN_REFUND'){
   const address=String(b.returnAddress||row.return_address).trim();if(!address||address.length>1000)fail('审核退货须填写退货地址',400);db.prepare('UPDATE after_sales SET return_address=? WHERE id=?').run(address,key);next='WAITING_RETURN';
  }else next='REFUND_PENDING';
 }else if(action==='return'){
  if(merchant(a)||row.buyer_id!==a.id)fail('无权提交物流',403);if(row.status!=='WAITING_RETURN')fail('状态不允许提交退货');
  const v=input(z.object({carrier:z.string().trim().min(1).max(60),trackingNo:z.string().trim().min(1).max(100)}),b);db.prepare('UPDATE after_sales SET carrier=?,tracking_no=? WHERE id=?').run(v.carrier,v.trackingNo,key);next='RETURNING';note=v.carrier+' '+v.trackingNo;
 }else if(action==='receive'){
  if(!merchant(a)||row.merchant_id!==a.merchantId)fail('无权确认收货',403);if(row.status!=='RETURNING')fail('状态不允许确认收货');next='REFUND_PENDING';note='商家确认收到退货，等待平台退款';
 }else if(action==='refund'){
  if(!isAdmin)fail('无权退款',403);if(row.status!=='REFUND_PENDING')fail('状态不允许退款');
  const order=get('orders',row.order_id);const payment=db.prepare('SELECT method FROM payments WHERE checkout_group_id=?').get(order.checkout_group_id) as Row|undefined;
  if(payment&&!String(payment.method).startsWith('DEMO'))fail('真实支付不能使用演示退款');
  db.prepare(`INSERT INTO refunds(id,after_sale_id,${normalizedSchema?'':'order_id,'}amount_cents,method,status,created_at) VALUES(?,?,${normalizedSchema?'':'?,'}?,'DEMO','COMPLETED',?)`).run(id('RF'),key,...(normalizedSchema?[]:[row.order_id]),row.amount_cents,now());
  for(const item of saleItems(key)){
   if(!item.restock_quantity)continue;
   db.prepare('UPDATE products SET stock_available=stock_available+?,updated_at=? WHERE id=?').run(item.restock_quantity,now(),item.product_id);
   db.prepare("INSERT INTO stock_movements(product_id,order_id,type,available_delta,reserved_delta,created_at) VALUES(?,?,'REFUND_RETURN',?,0,?)").run(item.product_id,row.order_id,item.restock_quantity,now());
  }
  next='COMPLETED';note='演示退款完成，金额 '+(row.amount_cents/100).toFixed(2)+' 元';
 }else fail('不支持的售后操作',400);
 db.prepare('UPDATE after_sales SET status=?,updated_at=?,version=version+1 WHERE id=?').run(next,now(),key);
 saleEvent(a,row,action,note||next);if(isAdmin)audit(a.id,a.name,'售后',action,key,'成功',note);return {ok:true,data:get('after_sales',key)};
}

function chatAction(ctx:BusinessContext):Row{
 const {actor:a,payload:b,params}=ctx;
 if(b.action==='create'){
  let buyerId=a.id,merchantId=String(b.merchantId||'');
  if(merchant(a)){
   const order=get('orders',String(b.orderId||''));if(order.merchant_id!==a.merchantId)fail('无权联系此订单买家',403);buyerId=order.user_id;merchantId=a.merchantId!;
  }else if(!a.roles.includes('USER'))fail('请使用买家或商家身份',403);
  const shop=get('merchants',merchantId);if(shop.owner_user_id===buyerId)fail('不能与自己的店铺建立会话',400);
  if(!merchant(a)&&!db.prepare("SELECT 1 FROM merchants WHERE id=? AND approval_status='APPROVED' AND business_status='OPEN'").get(merchantId))fail('店铺当前不可联系');
  const existing=db.prepare('SELECT * FROM conversations WHERE buyer_id=? AND merchant_id=?').get(buyerId,merchantId);if(existing)return {data:existing};
  const cid=id('CV');db.prepare('INSERT INTO conversations(id,buyer_id,merchant_id,created_at,updated_at) VALUES(?,?,?,?,?)').run(cid,buyerId,merchantId,now(),now());return {data:get('conversations',cid)};
 }
 const row=get('conversations',params.id);canSee(a,'conversations',row);
 if(b.action==='read'){
  const limit=Number.isSafeInteger(b.lastMessageId)?Math.max(0,b.lastMessageId):Number.MAX_SAFE_INTEGER;
  const last=(db.prepare('SELECT COALESCE(MAX(id),0) n FROM chat_messages WHERE conversation_id=? AND id<=?').get(row.id,limit) as Row).n;
  db.prepare('INSERT INTO conversation_reads(conversation_id,reader_role,last_message_id) VALUES(?,?,?) ON CONFLICT(conversation_id,reader_role) DO UPDATE SET last_message_id=MAX(last_message_id,excluded.last_message_id)').run(row.id,role(a),last);return {ok:true};
 }
 if(b.action!=='send')fail('未知聊天操作',400);
 const v=input(z.object({content:text,clientId:client,productId:z.string().optional(),orderId:z.string().optional()}),b);
 if(v.productId&&!db.prepare('SELECT 1 FROM products WHERE id=? AND merchant_id=? AND published_revision_id IS NOT NULL').get(v.productId,row.merchant_id))fail('商品不属于此店铺',400);
 if(v.orderId&&!db.prepare(`SELECT 1 FROM ${readTables.orders} WHERE id=? AND user_id=? AND merchant_id=?`).get(v.orderId,row.buyer_id,row.merchant_id))fail('订单不属于此会话',403);
 const old=db.prepare('SELECT * FROM chat_messages WHERE sender_id=? AND sender_role=? AND client_id=?').get(a.id,role(a),v.clientId) as Row|undefined;
 if(old){if(old.conversation_id!==row.id||old.content!==v.content||(old.product_id||'')!==(v.productId||'')||(old.order_id||'')!==(v.orderId||''))fail('同一消息标识不能用于不同内容');return {ok:true,reused:true};}
 db.prepare('INSERT INTO chat_messages(conversation_id,sender_id,sender_role,content,product_id,order_id,client_id,created_at) VALUES(?,?,?,?,?,?,?,?)').run(row.id,a.id,role(a),v.content,v.productId||null,v.orderId||null,v.clientId,now());
 db.prepare('UPDATE conversations SET updated_at=? WHERE id=?').run(now(),row.id);return {ok:true};
}
function feedbackAction(ctx:BusinessContext):Row{
 const {actor:a,payload:b,params}=ctx;
 if(b.action==='create'){
  const v=input(z.object({title:z.string().trim().min(1).max(100),type:z.enum(['功能问题','订单问题','使用建议','其他']),content:text,clientId:client}),b);
  const previous=db.prepare('SELECT * FROM feedbacks WHERE user_id=? AND client_id=?').get(a.id,v.clientId) as Row|undefined;
  if(previous){if(previous.title!==v.title||previous.content!==v.content||previous.type!==v.type)fail('请求标识已用于其他反馈');return {data:previous,reused:true};}
  const fid=id('FB');db.prepare("INSERT INTO feedbacks(id,user_id,submitter_role,title,type,content,status,client_id,created_at,updated_at) VALUES(?,?,?,?,?,?,'OPEN',?,?,?)").run(fid,a.id,role(a),v.title,v.type,v.content,v.clientId,now(),now());return {data:get('feedbacks',fid)};
 }
 const row=get('feedbacks',params.id),isAdmin=writer(a);canSee(a,'feedback',row,isAdmin);
 if(b.action==='reply'){
  const v=input(z.object({content:text,clientId:client}),b);
  const old=db.prepare('SELECT * FROM feedback_replies WHERE sender_id=? AND client_id=?').get(a.id,v.clientId) as Row|undefined;
  if(old){if(old.feedback_id!==row.id||old.content!==v.content)fail('回复标识已用于其他内容');return {ok:true,reused:true};}
  db.prepare('INSERT INTO feedback_replies(feedback_id,sender_id,sender_role,content,client_id,created_at) VALUES(?,?,?,?,?,?)').run(row.id,a.id,isAdmin?'ADMIN':role(a),v.content,v.clientId,now());
  db.prepare("UPDATE feedbacks SET status='PROCESSING',updated_at=? WHERE id=?").run(now(),row.id);if(isAdmin)notify(row.user_id,'反馈收到回复',`反馈 ${row.title} 收到管理员回复，请进入意见反馈查看。`);
 }else{
  if(!isAdmin)fail('无权处理反馈',403);if(!['process','resolve'].includes(b.action))fail('未知反馈操作',400);
  db.prepare('UPDATE feedbacks SET status=?,updated_at=? WHERE id=?').run(b.action==='resolve'?'RESOLVED':'PROCESSING',now(),row.id);
 }
 if(isAdmin)audit(a.id,a.name,'反馈',b.action,row.id);return {ok:true};
}
export function serviceAction(ctx:BusinessContext){
 const kind=ctx.params.kind;
 const result=db.transaction(()=>kind==='after-sales'?saleAction(ctx):kind==='conversations'?chatAction(ctx):kind==='feedback'?feedbackAction(ctx):fail('未知服务',404))();
 if(kind==='conversations'){
  const conversation=result.data||get('conversations',ctx.params.id);broadcastPrivate([conversation.buyer_id,owner(conversation.merchant_id)],kind);
 }else ctx.emit(kind,'update');
 if(kind==='after-sales'){ctx.emit('orders','update');ctx.emit('products','stock');}if(kind!=='conversations')ctx.emit('notices','update');
 return ctx.reply(ctx.payload.action==='create'?201:200,result);
}
