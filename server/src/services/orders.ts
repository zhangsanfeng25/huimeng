// Shared synchronous business rules used by HTTP routes and atomic offline sync.
import {createHash} from 'node:crypto';
import {z} from 'zod';
import {db,now} from '../core/db.js';
import {id,productSelect} from './shop-data.js';
import {config} from '../config.js';
import {closePendingCheckout} from '../core/order-lifecycle.js';
import {createOrderNotifications} from '../core/order-notifications.js';
import {audit} from '../core/http.js';
import type {Statement} from 'better-sqlite3';
import type {BusinessContext,BusinessResult} from './context.js';
export function checkoutCreate(ctx:BusinessContext):BusinessResult {
    const key = String(ctx.headers['idempotency-key'] || '');
    if (!key)
        return ctx.reply(400, { message: '缺少 Idempotency-Key' });
    const parsed = z.object({ addressId: z.string(), items: z.array(z.object({ productId: z.string(), quantity: z.number().int().min(1).max(99) })).min(1) }).safeParse(ctx.payload);
    if (!parsed.success)
        return ctx.reply(400, { message: '结算参数不正确' });
    const requestHash = createHash('sha256').update(JSON.stringify(parsed.data)).digest('hex');
    const existing = db.prepare('SELECT id,request_hash requestHash FROM checkout_groups WHERE user_id=? AND idempotency_key=?').get(ctx.actor.id, key) as any;
    if (existing) {
        if (existing.requestHash !== requestHash)
            return ctx.reply(409, { message: '同一幂等键不能用于不同订单' });
        return ctx.reply(200, { checkoutGroupId: existing.id, reused: true });
    }
    const address = db.prepare('SELECT * FROM addresses WHERE id=? AND user_id=?').get(parsed.data.addressId, ctx.actor.id) as any;
    if (!address)
        return ctx.reply(400, { message: '收货地址不存在' });
    const groupId = id('CG'), stamp = now(), expiresAt = new Date(Date.now() + config.orderTimeoutMinutes * 60000).toISOString();
    const result = db.transaction(() => {
        let productQuery: Statement, reserveStock: Statement, orderInsert: Statement, itemInsert: Statement, reserveMovement: Statement, createEvent: Statement;
        const byMerchant = new Map<string, Array<any>>();
        let grand = 0;
        for (const item of parsed.data.items) {
            const product = (productQuery ??= db.prepare(`${productSelect} WHERE p.id=? AND p.status='ONLINE'`)).get(item.productId) as any;
            if (!product)
                throw new Error('部分商品已下架');
            const changed = (reserveStock ??= db.prepare(`UPDATE products SET stock_available=stock_available-?,stock_reserved=stock_reserved+?,updated_at=? WHERE id=? AND stock_available>=?`)).run(item.quantity, item.quantity, stamp, item.productId, item.quantity);
            if (changed.changes !== 1)
                throw new Error(`${product.name} 库存不足`);
            const subtotal = product.priceCents * item.quantity;
            grand += subtotal;
            const group = byMerchant.get(product.merchantId) || [];
            group.push({ ...item, ...product, subtotal });
            byMerchant.set(product.merchantId, group);
        }
        db.prepare(`INSERT INTO checkout_groups(id,user_id,total_cents,status,version,idempotency_key,request_hash,created_at,expires_at) VALUES(?,?,?,'PENDING',1,?,?,?,?)`).run(groupId, ctx.actor.id, grand, key, requestHash, stamp, expiresAt);
        const orderIds: string[] = [];
        for (const [merchantId, items] of byMerchant) {
            const orderId = id('HM'), total = items.reduce((n, x) => n + x.subtotal, 0);
            orderIds.push(orderId);
            (orderInsert ??= db.prepare(`INSERT INTO orders(id,checkout_group_id,user_id,merchant_id,address_snapshot_json,total_cents,status,version,expires_at,created_at,updated_at) VALUES(?,?,?,?,?,?,'PENDING_PAYMENT',1,?,?,?)`)).run(orderId, groupId, ctx.actor.id, merchantId, JSON.stringify(address), total, expiresAt, stamp, stamp);
            for (const item of items) {
                (itemInsert ??= db.prepare(`INSERT INTO order_items(id,order_id,product_id,product_name,cover_url,unit_price_cents,quantity,subtotal_cents) VALUES(?,?,?,?,?,?,?,?)`)).run(id('OI'), orderId, item.productId, item.name, item.coverUrl, item.priceCents, item.quantity, item.subtotal);
                (reserveMovement ??= db.prepare(`INSERT INTO stock_movements(product_id,order_id,type,available_delta,reserved_delta,created_at) VALUES(?,?,'RESERVE',?,?,?)`)).run(item.productId, orderId, -item.quantity, item.quantity, stamp);
            }
            (createEvent ??= db.prepare(`INSERT INTO order_events(order_id,actor_id,action,from_status,to_status,created_at) VALUES(?,?,'CREATE',NULL,'PENDING_PAYMENT',?)`)).run(orderId, ctx.actor.id, stamp);
            createOrderNotifications(orderId, 'CREATED', stamp);
        }
        return { checkoutGroupId: groupId, orderIds, totalCents: grand, expiresAt };
    })();
    ctx.emit('orders', 'create');
    ctx.emit('products', 'stock');
    ctx.emit('notices', 'create');
    return ctx.reply(201, result);
}

export function checkoutPay(ctx:BusinessContext):BusinessResult {
    const key = String(ctx.headers['idempotency-key'] || '');
    if (!key)
        return ctx.reply(400, { message: '缺少 Idempotency-Key' });
    const method = z.enum(['DEMO_BALANCE', 'DEMO_ALIPAY', 'DEMO_WECHAT']).parse(ctx.payload?.method || 'DEMO_BALANCE');
    const groupId = String(ctx.params.groupId), stamp = now();
    const result = db.transaction(() => {
        let payEvent: Statement, deductReservedStock: Statement, payMovement: Statement;
        const group = db.prepare(`SELECT * FROM checkout_groups WHERE id=? AND user_id=?`).get(groupId, ctx.actor.id) as any;
        if (!group)
            throw new Error('结算单不存在');
        const paid = db.prepare('SELECT * FROM payments WHERE checkout_group_id=?').get(groupId) as any;
        if (paid)
            return { paymentId: paid.id, reused: true };
        if (group.status !== 'PENDING' || group.expires_at <= stamp)
            throw new Error('订单已过期或不能支付');
        const paymentId = id('PAY');
        db.prepare(`INSERT INTO payments(id,checkout_group_id,amount_cents,method,idempotency_key,paid_at) VALUES(?,?,?,?,?,?)`).run(paymentId, groupId, group.total_cents, method, key, stamp);
        db.prepare(`UPDATE checkout_groups SET status='PAID',version=version+1 WHERE id=?`).run(groupId);
        db.prepare(`UPDATE orders SET status='PAID',logistics_status='PREPARING',paid_at=?,updated_at=?,version=version+1 WHERE checkout_group_id=? AND status='PENDING_PAYMENT'`).run(stamp, stamp, groupId);
        for (const order of db.prepare(`SELECT id FROM orders WHERE checkout_group_id=?`).all(groupId) as any[]) {
            (payEvent ??= db.prepare(`INSERT INTO order_events(order_id,actor_id,action,from_status,to_status,created_at) VALUES(?,?,'PAY','WAITING_PAYMENT','PREPARING',?)`)).run(order.id, ctx.actor.id, stamp);
            createOrderNotifications(order.id, 'PAID', stamp);
        }
        for (const item of db.prepare(`SELECT oi.product_id,oi.quantity FROM order_items oi JOIN orders o ON o.id=oi.order_id WHERE o.checkout_group_id=?`).all(groupId) as any[]) {
            (deductReservedStock ??= db.prepare(`UPDATE products SET stock_reserved=stock_reserved-? WHERE id=? AND stock_reserved>=?`)).run(item.quantity, item.product_id, item.quantity);
            (payMovement ??= db.prepare(`INSERT INTO stock_movements(product_id,type,available_delta,reserved_delta,created_at) VALUES(?,'PAY',0,?,?)`)).run(item.product_id, -item.quantity, stamp);
        }
        db.prepare(`DELETE FROM cart_items WHERE user_id=? AND product_id IN
      (SELECT oi.product_id FROM order_items oi JOIN orders o ON o.id=oi.order_id WHERE o.checkout_group_id=?)`).run(ctx.actor.id, groupId);
        return { paymentId, paidAt: stamp };
    })();
    ctx.emit('orders', 'pay');
    if (!result.reused)
        ctx.emit('notices', 'create');
    return ctx.reply(200, result);
}

export function orderConfirm(ctx:BusinessContext):BusinessResult {
    const stamp = now(), orderId = String(ctx.params.orderId);
    const completed = db.transaction(() => {
        const changed = db.prepare(`UPDATE orders SET status='COMPLETED',logistics_status='COMPLETED',completed_at=?,updated_at=?,version=version+1 WHERE id=? AND user_id=? AND status='SHIPPED' AND logistics_status='OUT_FOR_DELIVERY'`).run(stamp, stamp, orderId, ctx.actor.id);
        if (changed.changes !== 1)
            return false;
        db.prepare(`INSERT INTO order_events(order_id,actor_id,action,from_status,to_status,created_at) VALUES(?,?, 'CONFIRM','OUT_FOR_DELIVERY','COMPLETED',?)`).run(orderId, ctx.actor.id, stamp);
        createOrderNotifications(orderId, 'COMPLETED', stamp);
        return true;
    })();
    if (!completed)
        return ctx.reply(409, { message: '订单尚未进入派送阶段' });
    ctx.emit('orders', 'complete');
    ctx.emit('notices', 'create');
    return ctx.reply(200, { ok: true });
}

export function orderCancel(ctx:BusinessContext):BusinessResult {
    const order = db.prepare('SELECT checkout_group_id groupId,status FROM orders WHERE id=? AND user_id=?').get(String(ctx.params.orderId), ctx.actor.id) as {
        groupId: string;
        status: string;
    } | undefined;
    if (!order)
        return ctx.reply(404, { message: '订单不存在' });
    if (order.status === 'CANCELLED')
        return ctx.reply(200, { ok: true, reused: true });
    if (order.status !== 'PENDING_PAYMENT')
        return ctx.reply(409, { message: '仅待付款订单可以取消，已付款订单请申请售后' });
    if (!closePendingCheckout(order.groupId, ctx.actor.id, '买家取消待付款结算单'))
        return ctx.reply(409, { message: '订单状态已变化，请刷新' });
    ctx.emit('orders', 'cancel');
    ctx.emit('products', 'stock');
    return ctx.reply(200, { ok: true });
}

export function orderShip(ctx:BusinessContext):BusinessResult {
    const stamp = now(), orderId = String(ctx.params.orderId), trackingNo = `DEMO${Date.now()}`;
    const shipped = db.transaction(() => {
        const changed = db.prepare(`UPDATE orders SET status='SHIPPED',logistics_status='SHIPPED',shipped_at=?,updated_at=?,version=version+1 WHERE id=? AND merchant_id=? AND status='PAID'`).run(stamp, stamp, orderId, ctx.actor.merchantId);
        if (changed.changes !== 1)
            return false;
        db.prepare(`INSERT INTO order_events(order_id,actor_id,action,from_status,to_status,note,created_at) VALUES(?,?,'SHIP','READY_TO_SHIP','SHIPPED',?,?)`).run(orderId, ctx.actor.id, trackingNo, stamp);
        createOrderNotifications(orderId, 'SHIPPED', stamp);
        return true;
    })();
    if (!shipped)
        return ctx.reply(409, { message: '订单不存在或状态不允许发货' });
    audit(ctx.actor.id, ctx.actor.name, '订单', '模拟发货', orderId);
    ctx.emit('orders', 'ship');
    ctx.emit('notices', 'create');
    return ctx.reply(200, { ok: true, trackingNo });
}

export function orderAdvance(ctx:BusinessContext):BusinessResult {
    const orderId = String(ctx.params.orderId), stamp = now(), order = db.prepare(`SELECT status,logistics_status logisticsStatus FROM orders WHERE id=? AND merchant_id=?`).get(orderId, ctx.actor.merchantId) as any;
    if (!order)
        return ctx.reply(404, { message: '订单不存在' });
    const next: Record<string, string> = { PREPARING: 'READY_TO_SHIP', READY_TO_SHIP: 'SHIPPED', SHIPPED: 'IN_TRANSIT', IN_TRANSIT: 'OUT_FOR_DELIVERY' }, to = next[order.logisticsStatus];
    if (!to)
        return ctx.reply(409, { message: '当前物流状态无需继续更新' });
    const status = to === 'SHIPPED' ? 'SHIPPED' : order.status, tracking = to === 'SHIPPED' ? `DEMO${Date.now()}` : null;
    db.transaction(() => { db.prepare(`UPDATE orders SET status=?,logistics_status=?,shipped_at=CASE WHEN ?='SHIPPED' THEN ? ELSE shipped_at END,updated_at=?,version=version+1 WHERE id=?`).run(status, to, to, stamp, stamp, orderId); db.prepare(`INSERT INTO order_events(order_id,actor_id,action,from_status,to_status,note,created_at) VALUES(?,?,'ADVANCE',?,?,?,?)`).run(orderId, ctx.actor.id, order.logisticsStatus, to, tracking, stamp); if (to === 'SHIPPED')
        createOrderNotifications(orderId, 'SHIPPED', stamp); })();
    ctx.emit('orders', 'advance');
    if (to === 'SHIPPED')
        ctx.emit('notices', 'create');
    return ctx.reply(200, { ok: true, status, toStatus: to, trackingNo: tracking });
}
