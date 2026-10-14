// Shared synchronous business rules used by HTTP routes and atomic offline sync.
import {randomUUID} from 'node:crypto';
import {z} from 'zod';
import {db,now} from '../core/db.js';
import {audit} from '../core/http.js';
import {merchantApplication} from './merchant-data.js';
import type {BusinessContext,BusinessResult} from './context.js';
export function merchantApply(ctx:BusinessContext):BusinessResult {
    const parsed = z.object({ shopName: z.string().trim().min(2).max(60), contactName: z.string().trim().min(2).max(30),
        contactPhone: z.string().trim().regex(/^\+?[0-9][0-9 -]{5,19}$/), businessAddress: z.string().trim().min(5).max(200),
        description: z.string().trim().min(10).max(1000), acceptedTerms: z.literal(true) }).safeParse(ctx.payload);
    if (!parsed.success)
        return ctx.reply(400, { message: '请完整填写店铺、联系人、有效联系电话、经营地址和至少 10 字的介绍，并确认资料真实' });
    const old = db.prepare('SELECT id,approval_status status FROM merchants WHERE owner_user_id=?').get(ctx.actor.id) as {
        id: string;
        status: string;
    } | undefined;
    if (old && old.status !== 'REJECTED')
        return ctx.reply(409, { message: old.status === 'PENDING' ? '申请正在审核，请勿重复提交' : '店铺已审核通过，请查看当前经营状态' });
    const v = parsed.data, stamp = now(), id = old?.id || `M${randomUUID().replaceAll('-', '').slice(0, 16).toUpperCase()}`;
    db.transaction(() => {
        if (old)
            db.prepare(`UPDATE merchants SET shop_name=?,description=?,contact_name=?,contact_phone=?,business_address=?,
      approval_status='PENDING',business_status='PENDING',rejection_reason=NULL,reviewed_at=NULL,reviewed_by=NULL,applied_at=?,updated_at=? WHERE id=?`)
                .run(v.shopName, v.description, v.contactName, v.contactPhone, v.businessAddress, stamp, stamp, id);
        else
            db.prepare(`INSERT INTO merchants(id,owner_user_id,shop_name,description,contact_name,contact_phone,business_address,applied_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?)`)
                .run(id, ctx.actor.id, v.shopName, v.description, v.contactName, v.contactPhone, v.businessAddress, stamp, stamp);
        audit(ctx.actor.id, ctx.actor.name, '商家申请', old ? '重新提交' : '提交申请', id);
    })();
    ctx.emit('merchants', 'apply');
    ctx.emit('users', 'update');
    return ctx.reply(201, { data: merchantApplication(ctx.actor.id) });
}
