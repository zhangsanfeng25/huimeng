import { db, now } from './db.js';

/** A single checkout payment can span several merchants: cancel the group atomically. */
export function closePendingCheckout(groupId: string, actorId: string | null, note: string): boolean {
  return db.transaction(() => {
    const stamp = now();
    const changed = db.prepare("UPDATE checkout_groups SET status='CLOSED',version=version+1 WHERE id=? AND status='PENDING'").run(groupId);
    if (!changed.changes) return false;
    const orders = db.prepare("SELECT id FROM orders WHERE checkout_group_id=? AND status='PENDING_PAYMENT'").all(groupId) as Array<{id:string}>;
    for (const order of orders) {
      const items = db.prepare('SELECT product_id,quantity FROM order_items WHERE order_id=?').all(order.id) as Array<{product_id:string;quantity:number}>;
      for (const item of items) {
        // Historical demo orders have no reservation ledger; do not manufacture stock.
        const reserved=(db.prepare('SELECT COALESCE(SUM(reserved_delta),0) quantity FROM stock_movements WHERE order_id=? AND product_id=?').get(order.id,item.product_id) as {quantity:number}).quantity;
        const quantity=Math.min(item.quantity,Math.max(0,reserved));
        if(quantity){
          const released=db.prepare('UPDATE products SET stock_available=stock_available+?,stock_reserved=stock_reserved-?,updated_at=? WHERE id=? AND stock_reserved>=?').run(quantity,quantity,stamp,item.product_id,quantity);
          if(!released.changes)throw new Error('库存预留记录不一致，请联系管理员');
          db.prepare("INSERT INTO stock_movements(product_id,order_id,type,available_delta,reserved_delta,created_at) VALUES(?,?,'RELEASE',?,?,?)").run(item.product_id,order.id,quantity,-quantity,stamp);
        }
      }
      db.prepare("UPDATE orders SET status='CANCELLED',logistics_status='CANCELLED',cancelled_at=?,updated_at=?,version=version+1 WHERE id=?").run(stamp,stamp,order.id);
      db.prepare("INSERT INTO order_events(order_id,actor_id,action,from_status,to_status,note,created_at) VALUES(?,?,'CANCEL','PENDING_PAYMENT','CANCELLED',?,?)").run(order.id,actorId||'SYSTEM',note,stamp);
    }
    return true;
  })();
}

export function expirePendingCheckouts(): void {
  const groups = db.prepare("SELECT id FROM checkout_groups WHERE status='PENDING' AND expires_at<=?").all(now()) as Array<{id:string}>;
  for (const group of groups) closePendingCheckout(group.id,null,'付款超时，订单自动关闭');
}
