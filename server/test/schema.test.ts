import test, { after } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, rmSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';

const fixture = mkdtempSync(join(tmpdir(),'huimeng-schema-'));
const previousPath = process.env.DATABASE_PATH;
process.env.DATABASE_PATH = join(fixture,'schema.db');
const { db } = await import('../src/core/db.js');
after(() => {
  db.close();
  if(previousPath === undefined) delete process.env.DATABASE_PATH;
  else process.env.DATABASE_PATH = previousPath;
  rmSync(fixture,{recursive:true,force:true});
});
await import('../scripts/migrate.js');
await import('../scripts/seed.js');

test('核心业务表已经完成迁移',()=>{
  const names=(db.prepare(`SELECT name FROM sqlite_master WHERE type='table'`).all() as Array<{name:string}>).map(x=>x.name);
  for(const expected of ['users','merchants','products','product_revisions','orders','order_items','ratings','community_posts','notifications','audit_logs']) assert.ok(names.includes(expected),`缺少表 ${expected}`);
});

test('演示数据包含两个商家与二十本绘本',()=>{
  assert.ok(Number((db.prepare('SELECT COUNT(*) count FROM merchants').get() as any).count)>=2);
  assert.ok(Number((db.prepare('SELECT COUNT(*) count FROM products').get() as any).count)>=20);
});
