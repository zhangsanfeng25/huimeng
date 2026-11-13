import {readTables} from '../core/db.js';
// Shared synchronous business rules used by HTTP routes and atomic offline sync.
import {z} from 'zod';
import {db,now,normalizedSchema} from '../core/db.js';
import {replaceOrderedValues} from '../normalization/relations.js';
import {makeId} from './community-data.js';
import type {BusinessContext,BusinessResult} from './context.js';
export function likeEnable(ctx:BusinessContext):BusinessResult {
    const id = String(ctx.params.id);
    if (!db.prepare(`SELECT 1 FROM ${readTables.community_posts} WHERE id=? AND status='PUBLIC'`).get(id))
        return ctx.reply(404, { message: '帖子不存在' });
    db.prepare('INSERT OR IGNORE INTO post_likes(post_id,user_id,created_at) VALUES(?,?,?)').run(id, ctx.actor.id, now());
    const count = (db.prepare('SELECT COUNT(*) count FROM post_likes WHERE post_id=?').get(id) as {
        count: number;
    }).count;
    ctx.emit('community', 'like');
    return ctx.reply(200, { liked: true, likeCount: count });
}

export function likeDisable(ctx:BusinessContext):BusinessResult {
    const id = String(ctx.params.id);
    db.prepare('DELETE FROM post_likes WHERE post_id=? AND user_id=?').run(id, ctx.actor.id);
    const count = (db.prepare('SELECT COUNT(*) count FROM post_likes WHERE post_id=?').get(id) as {
        count: number;
    }).count;
    ctx.emit('community', 'like');
    return ctx.reply(200, { liked: false, likeCount: count });
}

export function postCreate(ctx:BusinessContext):BusinessResult {
    const value = z.object({ title: z.string().min(1).max(80), content: z.string().min(1).max(2000), images: z.array(z.string().regex(/^\/public\/community-[a-f0-9-]+\.jpg$/)).max(9).optional(), productId: z.string().optional() }).parse(ctx.payload), postId = makeId('T'), stamp = now(), role = ctx.actor.roles.includes('MERCHANT') ? 'MERCHANT' : 'USER';
    const images = value.images || [];
    try {
        db.transaction(() => {
            for (const path of images) {
                const upload = db.prepare('SELECT 1 FROM community_uploads WHERE path=? AND user_id=? AND post_id IS NULL').get(path, ctx.actor.id);
                if (!upload)
                    throw new Error('INVALID_UPLOAD');
            }
            db.prepare(`INSERT INTO community_posts(id,author_user_id,author_role,title,content,cover_url,${normalizedSchema?'':'images_json,'}product_id,status,report_count,created_at,updated_at) VALUES(?,?,?,?,?,?,${normalizedSchema?'':'?,'}?, 'PUBLIC',0,?,?)`).run(postId, ctx.actor.id, role, value.title, value.content, images[0] || null, ...(normalizedSchema?[]:[JSON.stringify(images)]), value.productId || null, stamp, stamp);
            if(normalizedSchema)replaceOrderedValues(db,'community_post_images',postId,images);
            for (const path of images)
                db.prepare('UPDATE community_uploads SET post_id=? WHERE path=?').run(postId, path);
        })();
    }
    catch (error) {
        if ((error as Error).message === 'INVALID_UPLOAD')
            return ctx.reply(400, { message: '图片上传已失效，请重新选择' });
        throw error;
    }
    ctx.emit('community', 'create');
    return ctx.reply(201, { id: postId });
}

export function commentCreate(ctx:BusinessContext):BusinessResult {
    const value = z.object({ content: z.string().min(1).max(500) }).parse(ctx.payload), commentId = makeId('CM');
    const post = db.prepare(`SELECT id FROM ${readTables.community_posts} WHERE id=? AND status='PUBLIC'`).get(String(ctx.params.id));
    if (!post)
        return ctx.reply(404, { message: '帖子不存在' });
    db.prepare(`INSERT INTO comments(id,post_id,user_id,content,status,created_at) VALUES(?,?,?,?,'PUBLIC',?)`).run(commentId, String(ctx.params.id), ctx.actor.id, value.content, now());
    ctx.emit('community', 'comment');
    return ctx.reply(201, { id: commentId });
}

export function ratingCreate(ctx:BusinessContext):BusinessResult {
    const value = z.object({ orderItemId: z.string(), stars: z.number().int().min(1).max(5), content: z.string().min(1).max(1000) }).parse(ctx.payload);
    const item = db.prepare(`SELECT oi.product_id,o.user_id,o.status FROM ${readTables.order_items} oi JOIN ${readTables.orders} o ON o.id=oi.order_id WHERE oi.id=?`).get(value.orderItemId) as any;
    if (!item || item.user_id !== ctx.actor.id || item.status !== 'COMPLETED')
        return ctx.reply(403, { message: '只能评价本人已完成订单' });
    const ratingId = makeId('R');
    try {
        db.prepare(`INSERT INTO ratings(id,order_item_id,${normalizedSchema?'':'user_id,product_id,'}stars,content,status,created_at) VALUES(?,?,${normalizedSchema?'':'?,?,'}?,?,'PUBLIC',?)`).run(ratingId, value.orderItemId, ...(normalizedSchema?[]:[ctx.actor.id,item.product_id]), value.stars, value.content, now());
    }
    catch {
        return ctx.reply(409, { message: '该商品已评价' });
    }
    ctx.emit('ratings', 'create');
    return ctx.reply(201, { id: ratingId });
}
