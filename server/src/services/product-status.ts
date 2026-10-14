// Shared synchronous business rules used by HTTP routes and atomic offline sync.
import {db,now} from '../core/db.js';
import type {BusinessContext,BusinessResult} from './context.js';
export function productOffline(ctx:BusinessContext):BusinessResult {
    const changed = db.prepare(`UPDATE products SET status='OFFLINE',updated_at=? WHERE id=? AND merchant_id=? AND status='ONLINE'`).run(now(), String(ctx.params.productId), ctx.actor.merchantId);
    if (changed.changes !== 1)
        return ctx.reply(409, { message: '仅已上架商品可以下架' });
    ctx.emit('products', 'offline');
    return ctx.reply(200, { ok: true });
}

export function productSubmit(ctx:BusinessContext):BusinessResult {
    const productId = String(ctx.params.productId), stamp = now();
    const product = db.prepare(`SELECT id FROM products WHERE id=? AND merchant_id=?`).get(productId, ctx.actor.merchantId);
    if (!product)
        return ctx.reply(404, { message: '商品不存在' });
    const revision = db.prepare(`SELECT id FROM product_revisions WHERE product_id=? ORDER BY version DESC LIMIT 1`).get(productId) as any;
    db.transaction(() => { db.prepare(`UPDATE product_revisions SET review_status='PENDING',submitted_at=?,rejection_reason=NULL WHERE id=?`).run(stamp, revision.id); db.prepare(`UPDATE products SET status='PENDING_REVIEW',updated_at=? WHERE id=?`).run(stamp, productId); })();
    ctx.emit('reviews', 'submit');
    return ctx.reply(200, { ok: true, revisionId: revision.id });
}
