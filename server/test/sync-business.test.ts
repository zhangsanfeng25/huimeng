import test,{after} from 'node:test';import assert from 'node:assert/strict';import {mkdtempSync} from 'node:fs';import {tmpdir} from 'node:os';import {join} from 'node:path';
const fixture=mkdtempSync(join(tmpdir(),'huimeng-sync-business-'));process.env.DATABASE_PATH=join(fixture,'db');process.env.UPLOAD_DIR=join(fixture,'uploads');
const {db}=await import('../src/core/db.js');await import('../scripts/migrate.js');await import('../scripts/seed.js');
const {executeOnce,canonicalHash}=await import('../src/sync/receipts.js');
const buyer={id:'U10001',name:'test',roles:['USER'],activeRole:'USER'};
after(()=>db.close());
function op(action:string,id:string,payload:Record<string,unknown>,entityId?:string,baseVersion?:number){return {protocolVersion:1 as const,clientId:'test',operationId:id,action:action as import('../src/sync/types.js').SyncAction,payload,payloadHash:canonicalHash(payload),entityId,baseVersion};}
test('community operations share validation and replay without duplicate comments',()=>{
 const post=executeOnce(buyer,op('post.create','p1',{title:'离线帖子',content:'离线写入的内容'}));assert.ok(post.entityId);
 const comment=op('comment.create','c1',{postId:post.entityId,content:'恢复网络后同步的评论'});
 executeOnce(buyer,comment);executeOnce(buyer,comment);
 assert.equal((db.prepare('SELECT COUNT(*) n FROM comments WHERE post_id=?').get(post.entityId) as {n:number}).n,1);
 executeOnce(buyer,op('post.like','l1',{postId:post.entityId,enabled:true}));
 executeOnce(buyer,op('post.like','l2',{postId:post.entityId,enabled:false}));
 assert.equal((db.prepare('SELECT COUNT(*) n FROM post_likes WHERE post_id=?').get(post.entityId) as {n:number}).n,0);
});
test('cart uses exact quantity and buyer cannot upload merchant goods',()=>{
 const value=op('cart.set','cart1',{productId:'P0002',quantity:4,selected:true});executeOnce(buyer,value);executeOnce(buyer,value);
 assert.equal((db.prepare("SELECT quantity FROM cart_items WHERE user_id=? AND product_id='P0002'").get(buyer.id) as {quantity:number}).quantity,4);
 assert.throws(()=>executeOnce(buyer,op('product.save','forbidden',{name:'book'})),/商家|权限/);
});
