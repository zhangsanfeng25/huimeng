// Shared synchronous business rules used by HTTP routes and atomic offline sync.
import {z} from 'zod';
import {db,now} from '../core/db.js';
import {id} from './shop-data.js';
import type {BusinessContext,BusinessResult} from './context.js';
export function favoriteEnable(ctx:BusinessContext):BusinessResult {
    db.prepare('INSERT OR IGNORE INTO favorites(user_id,product_id,created_at) VALUES(?,?,?)').run(ctx.actor.id, String(ctx.params.productId), now());
    return ctx.reply(201, { ok: true });
}

export function favoriteDisable(ctx:BusinessContext):BusinessResult {
    db.prepare('DELETE FROM favorites WHERE user_id=? AND product_id=?').run(ctx.actor.id, String(ctx.params.productId));
    return ctx.reply(200, { ok: true });
}

export function cartSet(ctx:BusinessContext):BusinessResult {
    const parsed = z.object({ quantity: z.number().int().min(1).max(99), selected: z.boolean().optional() }).safeParse(ctx.payload);
    if (!parsed.success)
        return ctx.reply(400, { message: '购买数量须为 1 至 99 的整数' });
    const product = db.prepare('SELECT status,stock_available stock FROM products WHERE id=?').get(String(ctx.params.productId)) as {
        status: string;
        stock: number;
    } | undefined;
    if (parsed.data.selected === false) {
        const existing = db.prepare('SELECT quantity FROM cart_items WHERE user_id=? AND product_id=?').get(ctx.actor.id, String(ctx.params.productId)) as {
            quantity: number;
        } | undefined;
        if (existing && existing.quantity === parsed.data.quantity) {
            db.prepare('UPDATE cart_items SET selected=0,updated_at=? WHERE user_id=? AND product_id=?').run(now(), ctx.actor.id, String(ctx.params.productId));
            return ctx.reply(200, { ok: true });
        }
    }
    if (!product || product.status !== 'ONLINE' || parsed.data.quantity > product.stock)
        return ctx.reply(409, { message: '商品已下架或购买数量超过当前库存' });
    const quantity = parsed.data.quantity, selected = parsed.data.selected === false ? 0 : 1;
    db.prepare(`INSERT INTO cart_items(user_id,product_id,quantity,selected,updated_at) VALUES(?,?,?,?,?) ON CONFLICT(user_id,product_id) DO UPDATE SET quantity=excluded.quantity,selected=excluded.selected,updated_at=excluded.updated_at`).run(ctx.actor.id, String(ctx.params.productId), quantity, selected, now());
    return ctx.reply(200, { ok: true });
}

export function cartRemove(ctx:BusinessContext):BusinessResult {
    db.prepare('DELETE FROM cart_items WHERE user_id=? AND product_id=?').run(ctx.actor.id, String(ctx.params.productId));
    return ctx.reply(200, { ok: true });
}

export function addressCreate(ctx:BusinessContext):BusinessResult {
    const value = z.object({ recipient: z.string().min(1), phone: z.string().min(6), region: z.string().min(2), detail: z.string().min(2), isDefault: z.boolean().optional() }).parse(ctx.payload), addressId = id('ADDR'), stamp = now();
    db.transaction(() => { if (value.isDefault)
        db.prepare('UPDATE addresses SET is_default=0 WHERE user_id=?').run(ctx.actor.id); db.prepare(`INSERT INTO addresses(id,user_id,recipient,phone,region,detail,is_default,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?)`).run(addressId, ctx.actor.id, value.recipient, value.phone, value.region, value.detail, value.isDefault ? 1 : 0, stamp, stamp); })();
    return ctx.reply(201, { id: addressId });
}

export function addressUpdate(ctx:BusinessContext):BusinessResult {
    const value = z.object({ recipient: z.string().trim().min(1).max(50), phone: z.string().trim().min(6).max(30), region: z.string().trim().min(2).max(100), detail: z.string().trim().min(2).max(300), isDefault: z.boolean().optional() }).parse(ctx.payload);
    const addressId = String(ctx.params.id), userId = ctx.actor.id;
    if (!db.prepare('SELECT 1 FROM addresses WHERE id=? AND user_id=?').get(addressId, userId))
        return ctx.reply(404, { message: '收货地址不存在' });
    db.transaction(() => {
        if (value.isDefault)
            db.prepare('UPDATE addresses SET is_default=0 WHERE user_id=?').run(userId);
        db.prepare('UPDATE addresses SET recipient=?,phone=?,region=?,detail=?,is_default=?,updated_at=? WHERE id=? AND user_id=?').run(value.recipient, value.phone, value.region, value.detail, value.isDefault ? 1 : 0, now(), addressId, userId);
        if (!db.prepare('SELECT 1 FROM addresses WHERE user_id=? AND is_default=1').get(userId))
            db.prepare('UPDATE addresses SET is_default=1 WHERE id=?').run(addressId);
    })();
    return ctx.reply(200, { ok: true });
}

export function addressDelete(ctx:BusinessContext):BusinessResult {
    const changed = db.transaction(() => {
        const removed = db.prepare('DELETE FROM addresses WHERE id=? AND user_id=?').run(String(ctx.params.id), ctx.actor.id);
        if (removed.changes && !db.prepare('SELECT 1 FROM addresses WHERE user_id=? AND is_default=1').get(ctx.actor.id))
            db.prepare('UPDATE addresses SET is_default=1 WHERE id=(SELECT id FROM addresses WHERE user_id=? ORDER BY created_at DESC LIMIT 1)').run(ctx.actor.id);
        return removed.changes;
    })();
    if (!changed)
        return ctx.reply(404, { message: '收货地址不存在' });
    return ctx.reply(200, { ok: true });
}
