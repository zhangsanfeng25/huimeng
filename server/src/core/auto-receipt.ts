import {db,normalizedSchema,now,readTables} from './db.js';
import {hasBlockingAfterSale} from '../services/customer-services.js';
import {createOrderNotifications} from './order-notifications.js';
import {broadcast} from './events.js';

/** Shipment + 30 days; an active after-sale blocks completion until it resolves. */
export function confirmOverdueOrders(stamp=now()):number{
 const cutoff=new Date(Date.parse(stamp)-30*86400000).toISOString();
 const count=db.transaction(()=>{
  const orders=db.prepare(`SELECT id FROM ${readTables.orders} WHERE status='SHIPPED' AND logistics_status='OUT_FOR_DELIVERY' AND julianday(shipped_at)<=julianday(?)`).all(cutoff) as {id:string}[];
  let completed=0;
  for(const {id} of orders){
   if(hasBlockingAfterSale(id))continue;
   const changed=db.prepare(`UPDATE orders SET ${normalizedSchema?'':"status='COMPLETED',"}logistics_status='COMPLETED',completed_at=?,updated_at=?,version=version+1 WHERE id=? AND logistics_status='OUT_FOR_DELIVERY'`).run(stamp,stamp,id);
   if(!changed.changes)continue;
   db.prepare("INSERT INTO order_events(order_id,actor_id,action,from_status,to_status,note,created_at) VALUES(?,'SYSTEM','AUTO_CONFIRM','OUT_FOR_DELIVERY','COMPLETED','发货已满30天，系统自动确认收货',?)").run(id,stamp);
   createOrderNotifications(id,'AUTO_COMPLETED',stamp);completed++;
  }
  return completed;
 })();
 if(count){broadcast('orders','complete');broadcast('notices','create');}
 return count;
}
