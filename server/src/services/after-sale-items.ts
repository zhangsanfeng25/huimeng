import {db} from '../core/db.js';
import {SyncError} from '../sync/types.js';
type Row=Record<string,any>;
export type SaleItem={orderItemId:string;quantity:number;amountCents:number};
const reject=(message:string):never=>{throw new SyncError(409,'SERVICE_RULE',message);};
export function refundableItems(orderId:string):Row[]{
 return db.prepare(`SELECT i.*,
  MAX(0,i.subtotal_cents-COALESCE((SELECT SUM(x.amount_cents) FROM after_sale_items x JOIN after_sales a ON a.id=x.after_sale_id WHERE x.order_item_id=i.id AND a.status NOT IN ('REJECTED','CANCELLED')),0)) refundable_cents,
  MAX(0,i.quantity-COALESCE((SELECT SUM(x.restock_quantity) FROM after_sale_items x JOIN after_sales a ON a.id=x.after_sale_id WHERE x.order_item_id=i.id AND a.status NOT IN ('REJECTED','CANCELLED')),0)) returnable_quantity
  FROM order_items i WHERE i.order_id=? ORDER BY i.id`).all(orderId) as Row[];
}
export function selectSaleItems(order:Row,type:string,input?:SaleItem[]):Array<SaleItem&{restockQuantity:number}>{
 const available=refundableItems(order.id);
 const selected=input??available.filter(i=>i.refundable_cents>0&&i.returnable_quantity>0).map(i=>({orderItemId:i.id,quantity:i.returnable_quantity,amountCents:i.refundable_cents}));
 if(!selected.length)reject('订单已退款或无剩余可退额度');
 const seen=new Set<string>();
 return selected.map(item=>{
  if(seen.has(item.orderItemId))reject('申请商品不能重复');seen.add(item.orderItemId);
  const row=available.find(i=>i.id===item.orderItemId);if(!row)reject('商品不属于此订单');
  if(item.quantity>row!.returnable_quantity)reject('申请数量超过剩余可售后数量');
  if(item.amountCents>Math.min(row!.refundable_cents,row!.unit_price_cents*item.quantity))reject('退款金额超过所选商品可退额度');
  // An unshipped line fully refunded across several compensation cases cancels
  // every remaining unit, including units covered by those earlier cases.
  const restockQuantity=type==='RETURN_REFUND'?item.quantity:order.status!=='PAID'?0:
   item.amountCents===row!.refundable_cents?row!.returnable_quantity:
   item.amountCents===row!.unit_price_cents*item.quantity?item.quantity:0;
  return {...item,restockQuantity};
 });
}
export function saleItems(id:string):Row[]{
 return db.prepare(`SELECT i.id,i.product_id,i.product_name,i.cover_url,i.unit_price_cents,x.quantity,x.amount_cents,x.restock_quantity
 FROM after_sale_items x JOIN order_items i ON i.id=x.order_item_id WHERE x.after_sale_id=? ORDER BY i.id`).all(id) as Row[];
}
export function refundedAmount(orderId:string):number{return (db.prepare('SELECT COALESCE(SUM(amount_cents),0) n FROM refunds WHERE order_id=?').get(orderId) as {n:number}).n;}
export function withFulfillment(items:unknown[]):Row[]{
 return (items as Row[]).map(item=>{
  const cancelled=(db.prepare("SELECT COALESCE(SUM(x.restock_quantity),0) n FROM after_sale_items x JOIN after_sales a ON a.id=x.after_sale_id WHERE x.order_item_id=? AND a.type='REFUND_ONLY' AND a.status='COMPLETED'").get(item.id) as {n:number}).n;
  return {...item,cancelledQuantity:cancelled,fulfillableQuantity:Math.max(0,item.quantity-cancelled)};
 });
}
