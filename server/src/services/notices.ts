// Shared synchronous business rules used by HTTP routes and atomic offline sync.
import {z} from 'zod';
import {db,now} from '../core/db.js';
import {accessibleNotice,readNotice,deleteNotice} from './notice-data.js';
import type {BusinessContext,BusinessResult} from './context.js';
export function noticeBulk(ctx:BusinessContext):BusinessResult {
    const action = String(ctx.params.action);
    if (action !== 'read' && action !== 'delete')
        return ctx.reply(404, { message: '操作不存在' });
    const parsed = z.object({ ids: z.array(z.string().min(1)).min(1).max(1000) }).safeParse(ctx.payload);
    if (!parsed.success)
        return ctx.reply(400, { message: '请选择 1 至 1000 条消息' });
    const ids = [...new Set(parsed.data.ids)];
    if (ids.some(id => !accessibleNotice(id, ctx.actor.id, ctx.actor.roles)))
        return ctx.reply(404, { message: '部分消息不存在或已删除，请刷新列表' });
    db.transaction(() => { for (const id of ids)
        (action === 'read' ? readNotice : deleteNotice).run(id, ctx.actor.id, now()); })();
    return ctx.reply(200, { ok: true, count: ids.length });
}

export function noticeRead(ctx:BusinessContext):BusinessResult {
    const id = String(ctx.params.id);
    if (!accessibleNotice(id, ctx.actor.id, ctx.actor.roles))
        return ctx.reply(404, { message: '消息不存在' });
    readNotice.run(id, ctx.actor.id, now());
    return ctx.reply(200, { ok: true });
}

export function noticeDelete(ctx:BusinessContext):BusinessResult {
    const id = String(ctx.params.id);
    if (!accessibleNotice(id, ctx.actor.id, ctx.actor.roles))
        return ctx.reply(404, { message: '消息不存在' });
    deleteNotice.run(id, ctx.actor.id, now());
    return ctx.reply(200, { ok: true });
}
