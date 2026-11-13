import { readFileSync, readdirSync } from 'node:fs';
import { basename, join } from 'node:path';
import { db, now } from '../src/core/db.js';
import {assertSupportedSchema} from '../src/normalization/schema-guard.js';

assertSupportedSchema(db,{allowLegacyUpgrade:true});

db.exec('CREATE TABLE IF NOT EXISTS schema_migrations(version TEXT PRIMARY KEY, applied_at TEXT NOT NULL)');
const directory = join(process.cwd(), 'migrations');
const files = readdirSync(directory).filter(name => name.endsWith('.sql')).sort();
const applied = db.prepare('SELECT 1 FROM schema_migrations WHERE version = ?');
const record = db.prepare('INSERT INTO schema_migrations(version, applied_at) VALUES(?, ?)');

for (const file of files) {
  const version = basename(file, '.sql');
  if (applied.get(version)) continue;
  db.transaction(() => {
    db.exec(readFileSync(join(directory, file), 'utf8'));
    record.run(version, now());
  })();
  console.log(`已应用迁移：${version}`);
}
console.log('数据库迁移完成');
