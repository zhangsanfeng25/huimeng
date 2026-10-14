import {db,now} from '../core/db.js';
import {z} from 'zod';
import {addressCreate,addressUpdate,addressDelete,favoriteEnable,favoriteDisable,cartSet,cartRemove} from '../services/buyer.js';
import {checkoutCreate,checkoutPay,orderCancel,orderConfirm,orderShip,orderAdvance} from '../services/orders.js';
import {postCreate,commentCreate,likeEnable,likeDisable,ratingCreate} from '../services/community.js';
import {noticeRead,noticeDelete} from '../services/notices.js';
import {profileSave,profileAvatar} from '../services/profile.js';
import {merchantApply} from '../services/merchant.js';
import {productSave} from '../services/products.js';
import {productSubmit,productOffline} from '../services/product-status.js';
import {runBusiness,type BusinessHandler} from '../services/context.js';
import {expirePendingCheckouts} from '../core/order-lifecycle.js';
import {SyncError,type Actor,type SyncEnvelope,type SyncResult} from './types.js';

const supportedActions=new Set(['address.save','address.delete','favorite.set','cart.set','cart.remove','profile.save','profile.avatar','post.create','comment.create','post.like','notice.read','notice.delete','merchant.apply','merchant.profile','product.save','product.submit','product.offline','checkout.create','checkout.pay','order.cancel','order.confirm','order.ship','order.advance','rating.create']);
export function resourceForAction(action:string){if(!supportedActions.has(action))return undefined;return ({address:'addresses',favorite:'favorites',cart:'cart',profile:'profile',post:'posts',comment:'comments',notice:'notifications',merchant:'merchantApplication',product:'merchantProducts',checkout:'checkouts',order:'buyerOrders',rating:'ownRatings'} as Record<string,string>)[action.split('.')[0]];}
export function remoteId(actor:Actor,resource:string,id:unknown):string{
 const key=String(id||'');const row=db.prepare('SELECT remote_id FROM sync_entity_map WHERE user_id=? AND resource=? AND local_id=?').get(actor.id,resource,key) as {remote_id:string}|undefined;
 return row?.remote_id||key;
}
function version(actor:Actor,op:SyncEnvelope,id:string){
 const scope=op.action.split('.')[0];const lookup:Record<string,{table:string;key:string;owner:string}>={address:{table:'addresses',key:id,owner:'user_id'},profile:{table:'users',key:actor.id,owner:'id'},product:{table:'products',key:id,owner:'merchant_id'},merchant:{table:'merchants',key:actor.merchantId||'',owner:'owner_user_id'},checkout:{table:'checkout_groups',key:id,owner:'user_id'},order:{table:'orders',key:id,owner:op.action==='order.ship'||op.action==='order.advance'?'merchant_id':'user_id'}};
 const item=lookup[scope];if(!item||op.baseVersion===undefined)return;
 const owner=item.owner==='merchant_id'?actor.merchantId||'':actor.id;
 const row=db.prepare(`SELECT version FROM ${item.table} WHERE id=? AND ${item.owner}=?`).get(item.key,owner) as {version:number}|undefined;
 if(row&&row.version!==op.baseVersion)throw new SyncError(409,'VERSION_CONFLICT','内容已在另一端修改，请处理冲突',{version:row.version});
 if(!row&&op.baseVersion>0)throw new SyncError(409,'ENTITY_DELETED','内容不存在或已删除，请处理冲突');
}
export function applyOperation(actor:Actor,op:SyncEnvelope):{result:SyncResult;events:Array<{resource:string;action:string}>}{
 const resource=resourceForAction(op.action);if(!resource)throw new SyncError(400,'UNKNOWN_ACTION','不支持的同步操作');
 const merchant=op.action.startsWith('product.')||op.action==='merchant.profile'||op.action==='order.ship'||op.action==='order.advance';
 if(merchant&&(!actor.roles.includes('MERCHANT')||!actor.merchantId))throw new SyncError(403,'ROLE_REQUIRED','当前账号没有有效商家权限');
 if(!merchant&&!op.action.startsWith('post.')&&!op.action.startsWith('comment.')&&!op.action.startsWith('profile.')&&!op.action.startsWith('notice.')&&!actor.roles.includes('USER'))throw new SyncError(403,'ROLE_REQUIRED','当前账号没有买家权限');
 const p={...op.payload};const id=remoteId(actor,resource,op.entityId||p.id||'');
 for(const [key,type] of [['productId','merchantProducts'],['postId','posts'],['addressId','addresses'],['groupId','checkouts'],['orderId','buyerOrders']] as const)if(p[key])p[key]=remoteId(actor,type,p[key]);
 if(Array.isArray(p.items))p.items=p.items.map((item:any)=>({...item,productId:remoteId(actor,'merchantProducts',item.productId)}));
 version(actor,op,id);
 const params:Record<string,string>={id,productId:String(p.productId||id),orderId:String(p.orderId||id),groupId:String(p.groupId||id)};
 let handler:BusinessHandler|undefined;
 switch(op.action){
  case 'address.save':{const exists=db.prepare('SELECT 1 FROM addresses WHERE id=? AND user_id=?').get(id,actor.id);handler=exists?addressUpdate:addressCreate;break;}
  case 'address.delete':handler=addressDelete;break;
  case 'favorite.set':handler=p.enabled===false?favoriteDisable:favoriteEnable;break;
  case 'cart.set':handler=cartSet;break;case 'cart.remove':handler=cartRemove;break;
  case 'profile.save':if(p.currentPassword||p.newPassword)throw new SyncError(400,'ONLINE_ONLY','密码不能进入同步队列');handler=profileSave;break;
  case 'profile.avatar':handler=profileAvatar;break;
  case 'post.create':handler=postCreate;break;
  case 'comment.create':handler=commentCreate;params.id=String(p.postId);break;
  case 'post.like':handler=p.enabled===false?likeDisable:likeEnable;params.id=String(p.postId);break;
  case 'notice.read':handler=noticeRead;break;case 'notice.delete':handler=noticeDelete;break;
  case 'merchant.apply':handler=merchantApply;break;
  case 'merchant.profile':{
   const input=z.object({shopName:z.string().trim().min(2).max(60),description:z.string().trim().min(10).max(1000)}).safeParse(p);if(!input.success)throw new SyncError(400,'INVALID_SHOP','请填写有效的店铺名称与介绍');
   db.prepare('UPDATE merchants SET shop_name=?,description=?,updated_at=? WHERE id=? AND owner_user_id=?').run(input.data.shopName,input.data.description,now(),actor.merchantId,actor.id);
   const data=db.prepare('SELECT * FROM merchants WHERE id=?').get(actor.merchantId) as {version:number};return {result:{operationId:op.operationId,entityId:actor.merchantId,version:data.version,data,replayed:false},events:[{resource:'merchants',action:'update'}]};
  }
  case 'product.save':handler=productSave;if(!db.prepare('SELECT 1 FROM products WHERE id=? AND merchant_id=?').get(id,actor.merchantId))delete params.productId;else params.productId=id;break;
  case 'product.submit':handler=productSubmit;params.productId=id;break;case 'product.offline':handler=productOffline;params.productId=id;break;
  case 'checkout.create':{
   expirePendingCheckouts();const parsed=z.object({expectedTotalCents:z.number().int().nonnegative(),items:z.array(z.object({productId:z.string(),quantity:z.number().int().min(1).max(99),expectedPriceCents:z.number().int().nonnegative()})).min(1)}).safeParse(p);
   if(!parsed.success)throw new SyncError(400,'INVALID_CHECKOUT','缺少订单价格快照');let total=0;
   for(const item of parsed.data.items){const product=db.prepare("SELECT r.price_cents price FROM products p JOIN product_revisions r ON r.id=p.published_revision_id WHERE p.id=? AND p.status='ONLINE'").get(item.productId) as {price:number}|undefined;if(!product||product.price!==item.expectedPriceCents)throw new SyncError(409,'PRICE_CHANGED','商品已变更，请核对价格后重新确认');total+=product.price*item.quantity;}
   if(total!==parsed.data.expectedTotalCents)throw new SyncError(409,'PRICE_CHANGED','订单金额已变化，请重新确认');handler=checkoutCreate;break;
  }
  case 'checkout.pay':{expirePendingCheckouts();const group=db.prepare('SELECT total_cents FROM checkout_groups WHERE id=? AND user_id=?').get(params.groupId,actor.id) as {total_cents:number}|undefined;if(!group||group.total_cents!==p.expectedTotalCents)throw new SyncError(409,'PRICE_CHANGED','付款金额无法确认，请查看订单');handler=checkoutPay;break;}
  case 'order.cancel':handler=orderCancel;break;case 'order.confirm':handler=orderConfirm;break;case 'order.ship':handler=orderShip;break;case 'order.advance':handler=orderAdvance;break;
  case 'rating.create':handler=ratingCreate;break;
 }
 if(!handler)throw new SyncError(400,'UNKNOWN_ACTION','不支持的同步操作');
 let output:ReturnType<typeof runBusiness>;try{output=runBusiness(handler,actor,p,params,{'idempotency-key':op.operationId});}catch(error){if(error instanceof z.ZodError)throw new SyncError(400,'INVALID_INPUT','同步信息格式不正确');throw error;}
 if(output.status>=400)throw new SyncError(output.status,output.status===409?'BUSINESS_CONFLICT':'BUSINESS_REJECTED',output.data.message||'操作未完成');
 const entityId=String(output.data.productId||output.data.checkoutGroupId||output.data.id||output.data.data?.id||output.data.user?.id||id||actor.id);
 if(op.entityId&&entityId)db.prepare('INSERT INTO sync_entity_map(user_id,resource,local_id,remote_id) VALUES(?,?,?,?) ON CONFLICT(user_id,resource,local_id) DO UPDATE SET remote_id=excluded.remote_id').run(actor.id,resource,op.entityId,entityId);
 const lookup=resource==='addresses'?'addresses':resource==='merchantProducts'?'products':resource==='profile'?'users':resource==='checkouts'?'checkout_groups':resource==='buyerOrders'?'orders':resource==='merchantApplication'?'merchants':null;
 const row=lookup?db.prepare(`SELECT version FROM ${lookup} WHERE id=?`).get(resource==='profile'?actor.id:entityId) as {version:number}|undefined:undefined;
 return {result:{operationId:op.operationId,entityId,version:row?.version||0,data:output.data,replayed:false},events:output.events};
}
