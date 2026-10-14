import test,{after} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtempSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
const fixture=mkdtempSync(join(tmpdir(),'huimeng-sync-'));
process.env.DATABASE_PATH=join(fixture,'test.db');process.env.UPLOAD_DIR=join(fixture,'uploads');
const {db}=await import('../src/core/db.js');
await import('../scripts/migrate.js');await import('../scripts/seed.js');
const {executeOnce,canonicalHash}=await import('../src/sync/receipts.js');
const actor={id:'U10001',name:'test',roles:['USER'],activeRole:'USER'};
const envelope=(id:string,payload:Record<string,unknown>={recipient:'本地',phone:'13800138000',region:'测试区域',detail:'测试详细地址'})=>({protocolVersion:1 as const,clientId:'test-client',operationId:id,action:'address.save' as const,payload,payloadHash:canonicalHash(payload)});
after(()=>db.close());
test('same operation replays once and a different payload conflicts',()=>{
 const op=envelope('retry-1'),first=executeOnce(actor,op),second=executeOnce(actor,op);
 assert.equal(first.entityId,second.entityId);assert.equal(second.replayed,true);
 assert.equal((db.prepare('SELECT COUNT(*) n FROM addresses WHERE id=?').get(first.entityId) as {n:number}).n,1);
 assert.throws(()=>executeOnce(actor,envelope('retry-1',{recipient:'changed'})),/幂等|载荷/);
});
test('failed business mutation leaves no receipt',()=>{
 assert.throws(()=>executeOnce(actor,envelope('invalid',{recipient:''})),/信息|格式|地址/);
 assert.equal((db.prepare("SELECT COUNT(*) n FROM sync_receipts WHERE operation_id='invalid'").get() as {n:number}).n,0);
});
test('canonical hash sorts objects but preserves array order',()=>{
 assert.equal(canonicalHash({b:2,a:'绘梦'}),canonicalHash({a:'绘梦',b:2}));
 assert.notEqual(canonicalHash({a:[1,2]}),canonicalHash({a:[2,1]}));
});
test('invalid hash and unknown actions are rejected',()=>{
 assert.throws(()=>executeOnce(actor,{...envelope('bad-hash'),payloadHash:'bad'}),/校验/);
 assert.throws(()=>executeOnce(actor,{...envelope('bad-action'),action:'not.real' as never}),/操作/);
});
test('revoked merchant cannot replay a previously successful mutation',()=>{
 const merchant={id:'MUSER01',name:'merchant',roles:['USER','MERCHANT'],merchantId:'M20001'};
 const op={...envelope('merchant-create',{name:'离线商品',summary:'示例简介',categoryId:'C01',ageRange:'3–6岁',priceCents:1000,stock:5}),action:'product.save' as const};
 op.payload.categoryId=(db.prepare('SELECT id FROM categories LIMIT 1').get() as {id:string}).id;
 op.payloadHash=canonicalHash(op.payload);executeOnce(merchant,op);
 db.prepare("UPDATE merchants SET business_status='SUSPENDED' WHERE id='M20001'").run();
 assert.throws(()=>executeOnce(merchant,op),/权限|商家/);
});

test('prototype names and unsupported actions are rejected as bad operations',()=>{
 for(const action of ['constructor.save','__proto__.save','address.constructor'])assert.throws(()=>executeOnce(actor,{...envelope('invalid-'+action),action:action as never}),/不支持的同步操作/);
});
