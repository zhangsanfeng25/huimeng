// Shared synchronous business rules used by HTTP routes and atomic offline sync.
import {z} from 'zod';
import {db,now} from '../core/db.js';
import {hashPassword,verifyPassword} from '../core/security.js';
import {audit} from '../core/http.js';
import {isDefaultAvatar} from '../core/default-avatars.js';
import type {BusinessContext,BusinessResult} from './context.js';
export function profileAvatar(ctx:BusinessContext):BusinessResult {
    const parsed = z.object({ avatarUrl: z.string().max(200) }).safeParse(ctx.payload);
    if (!parsed.success)
        return ctx.reply(400, { message: '请选择默认头像或上传裁剪后的头像' });
    if (!isDefaultAvatar(parsed.data.avatarUrl)) {
        if (!/^\/public\/community-[a-f0-9-]+\.jpg$/.test(parsed.data.avatarUrl))
            return ctx.reply(400, { message: '请选择默认头像或上传裁剪后的头像' });
        const owned = db.prepare('SELECT 1 FROM community_uploads WHERE path=? AND user_id=?').get(parsed.data.avatarUrl, ctx.actor.id);
        if (!owned)
            return ctx.reply(403, { message: '只能使用本人上传的图片' });
    }
    db.prepare('UPDATE users SET avatar_url=?,updated_at=? WHERE id=?').run(parsed.data.avatarUrl, now(), ctx.actor.id);
    ctx.emit('users', 'update');
    ctx.emit('merchants', 'update');
    ctx.emit('community', 'update');
    return ctx.reply(200, { avatarUrl: parsed.data.avatarUrl });
}

export function profileSave(ctx:BusinessContext):BusinessResult {
    const parsed = z.object({ username: z.string().trim().min(3).max(32).regex(/^[a-zA-Z0-9_]+$/), nickname: z.string().trim().min(1).max(30), currentPassword: z.string().min(1).max(64).optional(), newPassword: z.string().min(8).max(64).optional() })
        .refine(value => (value.currentPassword === undefined) === (value.newPassword === undefined)).safeParse(ctx.payload);
    if (!parsed.success)
        return ctx.reply(400, { message: '用户名需为 3 至 32 位字母、数字或下划线，昵称需为 1 至 30 字' });
    const { username, nickname, currentPassword, newPassword } = parsed.data;
    if (newPassword) {
        const user = db.prepare('SELECT password_hash FROM users WHERE id=?').get(ctx.actor.id) as {
            password_hash: string;
        };
        if (!verifyPassword(currentPassword!, user.password_hash))
            return ctx.reply(400, { message: '当前密码不正确，账户信息未更改' });
    }
    const conflict = db.prepare('SELECT id FROM users WHERE username=? COLLATE NOCASE AND id<>?').get(username, ctx.actor.id);
    if (conflict)
        return ctx.reply(409, { message: '用户名已被使用' });
    const passwordHash = newPassword ? hashPassword(newPassword) : undefined;
    try {
        db.transaction(() => {
            db.prepare('UPDATE users SET username=?,nickname=?,updated_at=? WHERE id=?').run(username, nickname, now(), ctx.actor.id);
            if (passwordHash) {
                db.prepare('UPDATE users SET password_hash=? WHERE id=?').run(passwordHash, ctx.actor.id);
                db.prepare('DELETE FROM sessions WHERE user_id=?').run(ctx.actor.id);
            }
            audit(ctx.actor.id, nickname, '账户', passwordHash ? '编辑信息及修改密码' : '编辑信息', ctx.actor.id);
        })();
    }
    catch (error: any) {
        if (error?.code === 'SQLITE_CONSTRAINT_UNIQUE')
            return ctx.reply(409, { message: '用户名已被使用' });
        throw error;
    }
    ctx.emit('users', 'update');
    ctx.emit('merchants', 'update');
    ctx.emit('community', 'update');
    return ctx.reply(200, { user: { id: ctx.actor.id, username, name: nickname }, passwordChanged: Boolean(passwordHash) });
}
