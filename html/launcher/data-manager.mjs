import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import { resolve, join, dirname, relative, isAbsolute, sep } from 'node:path';
import { existsSync, mkdirSync, readdirSync, readFileSync, writeFileSync, appendFileSync, statSync, lstatSync, cpSync, renameSync, rmSync } from 'node:fs';
import { createHash, randomUUID } from 'node:crypto';
import { createConnection } from 'node:net';

const project = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const server = join(project, 'server');
const require = createRequire(join(server, 'package.json'));
const Database = require('better-sqlite3');
require('dotenv').config({ path: join(server, '.env'), quiet: true });
const databasePath = resolve(server, process.env.DATABASE_PATH || 'data/huimeng.db');
const uploadsPath = resolve(server, process.env.UPLOAD_DIR || 'uploads');
const backupRoot = resolve(server, process.env.BACKUP_DIR || 'data/backups');
const historyPath = join(backupRoot, 'maintenance.jsonl');
const [action, ...args] = process.argv.slice(2);
const option = (key, fallback = '') => { const index = args.indexOf(key); return index >= 0 ? args[index + 1] : fallback; };
const digest = path => createHash('sha256').update(readFileSync(path)).digest('hex');
function removeTree(target, parent) {
  const path=resolve(target), root=resolve(parent);
  if(!path.startsWith(root.endsWith(sep)?root:root+sep)||path===root)throw new Error('拒绝清理越界目录');
  rmSync(path,{recursive:true,force:true});
}
const stamp = () => new Date().toISOString().replace(/[:.]/g, '-');
function history(operation, result, detail) {
  mkdirSync(backupRoot, { recursive: true });
  appendFileSync(historyPath, JSON.stringify({ id: randomUUID(), operator: '本地控制中心', module: '数据管理', action: operation, result, detail, createdAt: new Date().toISOString() }) + '\n');
}
function inspect(path) {
  const db = new Database(path, { readonly: true, fileMustExist: true });
  try {
    if (db.pragma('integrity_check', { simple: true }) !== 'ok') throw new Error('数据库完整性校验失败');
    const tables = new Set(db.prepare("SELECT name FROM sqlite_master WHERE type='table'").all().map(row => row.name));
    for (const name of ['users', 'user_roles', 'products', 'product_revisions', 'orders', 'audit_logs']) if (!tables.has(name)) throw new Error('备份不是绘梦业务数据库：缺少 ' + name);
  } finally { db.close(); }
}
function filesIn(root) {
  const files = [];
  if (!existsSync(root)) return files;
  if(lstatSync(root).isSymbolicLink()||!lstatSync(root).isDirectory())throw new Error('素材根目录必须是实际文件夹');
  function walk(dir) {
    for (const name of readdirSync(dir)) {
      const path = join(dir, name), stat = lstatSync(path);
      if (stat.isSymbolicLink()) throw new Error('素材目录不能包含符号链接');
      if (stat.isDirectory()) walk(path);
      else files.push({ path: relative(root, path).replaceAll('\\', '/'), size: stat.size, sha256: digest(path) });
    }
  }
  walk(root); return files;
}
async function backup(reason = '手动备份') {
  inspect(databasePath);
  mkdirSync(backupRoot, { recursive: true });
  const path = join(backupRoot, 'backup-' + stamp() + '-' + randomUUID().slice(0, 6));
  mkdirSync(path);
  const db = new Database(databasePath, { readonly: true, fileMustExist: true });
  try {
    await db.backup(join(path, 'database.sqlite'));
    if (existsSync(uploadsPath)) cpSync(uploadsPath, join(path, 'uploads'), { recursive: true, errorOnExist: true });
    else mkdirSync(join(path, 'uploads'));
    const manifest = { format: 'huimeng-backup-v1', createdAt: new Date().toISOString(), reason, database: { sha256: digest(join(path, 'database.sqlite')) }, uploads: filesIn(join(path, 'uploads')) };
    writeFileSync(join(path, 'manifest.json'), JSON.stringify(manifest, null, 2));
    history('备份', '成功', path);
    return { path, createdAt: manifest.createdAt, images: manifest.uploads.length };
  } catch (error) { removeTree(path, backupRoot); throw error; }
  finally { db.close(); }
}
function validateBackup(source) {
  const path = resolve(source), manifest = JSON.parse(readFileSync(join(path, 'manifest.json'), 'utf8'));
  if (manifest.format !== 'huimeng-backup-v1' || !Array.isArray(manifest.uploads)) throw new Error('请选择绘梦完整备份目录');
  if(lstatSync(path).isSymbolicLink()||lstatSync(join(path,'manifest.json')).isSymbolicLink())throw new Error('备份目录不能使用符号链接');
  const database = join(path, 'database.sqlite');
  if(lstatSync(database).isSymbolicLink())throw new Error('备份数据库不能使用符号链接');
  if (digest(database) !== manifest.database?.sha256) throw new Error('数据库 hash 校验失败，备份可能损坏');
  inspect(database);
  const root = join(path, 'uploads');
  for (const file of manifest.uploads) {
    if (typeof file.path !== 'string' || isAbsolute(file.path) || file.path.split(/[\\/]/).includes('..')) throw new Error('备份素材路径非法');
    const target = resolve(root, file.path);
    if (!target.startsWith(resolve(root) + '\\') && !target.startsWith(resolve(root) + '/')) throw new Error('备份素材越界');
    if (lstatSync(target).isSymbolicLink() || statSync(target).size !== file.size || digest(target) !== file.sha256) throw new Error('图片 hash 校验失败：' + file.path);
  }
  // Also reject unlisted extra content and symlinks before copying any files.
  if (filesIn(root).length !== manifest.uploads.length) throw new Error('备份图片列表校验失败');
  return path;
}
async function portOpen() {
  return new Promise(resolveResult => {
    const socket = createConnection({ host: '127.0.0.1', port: Number(process.env.PORT || 3000) });
    const finish = value => { socket.destroy(); resolveResult(value); };
    socket.setTimeout(1000); socket.once('connect', () => finish(true)); socket.once('error', () => finish(false)); socket.once('timeout', () => finish(false));
  });
}
async function restore(source) {
  if (!args.includes('--service-stopped') || await portOpen()) throw new Error('请先停止服务，再执行恢复（service must be stopped）');
  const path = validateBackup(source); // No current data is touched until validation succeeds.
  const safety = await backup('恢复前自动备份');
  const suffix = '.restore-' + randomUUID();
  const oldDatabase = databasePath + suffix, oldUploads = uploadsPath + suffix;
  const stagedDatabase = databasePath + suffix + '.new', stagedUploads = uploadsPath + suffix + '.new';
  const moved = { database: false, uploads: false, newDatabase: false, newUploads: false };
  try {
    cpSync(join(path, 'database.sqlite'), stagedDatabase);
    const staged = new Database(stagedDatabase); try { staged.prepare('DELETE FROM sessions').run(); } finally { staged.close(); }
    cpSync(join(path, 'uploads'), stagedUploads, { recursive: true });
    const db = new Database(databasePath); try { db.pragma('wal_checkpoint(TRUNCATE)'); db.exec('BEGIN EXCLUSIVE; COMMIT;'); } finally { db.close(); }
    renameSync(databasePath, oldDatabase); moved.database = true;
    if (existsSync(uploadsPath)) { renameSync(uploadsPath, oldUploads); moved.uploads = true; }
    for (const tail of ['-wal', '-shm']) rmSync(databasePath + tail, { force: true });
    renameSync(stagedDatabase, databasePath); moved.newDatabase = true;
    renameSync(stagedUploads, uploadsPath); moved.newUploads = true;
    inspect(databasePath);
    history('恢复', '成功', path + '；恢复前备份：' + safety.path);
  } catch (error) {
    if (moved.newDatabase) rmSync(databasePath, { force: true });
    if (moved.newUploads) removeTree(uploadsPath, dirname(uploadsPath));
    if (moved.database) renameSync(oldDatabase, databasePath);
    if (moved.uploads) renameSync(oldUploads, uploadsPath);
    throw new Error('恢复失败，当前数据已回滚：' + error.message);
  } finally {
    for (const temporary of [stagedDatabase, stagedUploads]) removeTree(temporary, dirname(temporary));
  }
  // Keep the safety snapshot as the recovery artifact; old moved files can now be removed.
  rmSync(oldDatabase, { force: true }); removeTree(oldUploads, dirname(uploadsPath));
  return { restored: true, safetyBackup: safety.path, message: '恢复成功，请重新打开服务，网页和手机需重新登录。' };
}
const exportTables = ['users','merchants','categories','products','product_revisions','orders','order_items','ratings','community_posts','comments','notifications','audit_logs','after_sales','after_sale_events','after_sale_items','refunds','conversations','chat_messages','conversation_reads','feedbacks','feedback_replies','checkout_groups','payments','order_address_snapshots','after_sale_requests','product_revision_images','product_revision_contents','community_post_images','after_sale_images','notification_read_receipts'];
function csv(value) {
  let text = value == null ? '' : String(value);
  if (/^[\s]*[=+@-]/.test(text)) text = "'" + text;
  return '"' + text.replaceAll('"', '""') + '"';
}
async function exportData(output) {
  if (!output) throw new Error('请选择导出目录');
  const path = join(resolve(output), 'huimeng-export-' + stamp()); mkdirSync(path, { recursive: true });
  const db = new Database(databasePath, { readonly: true, fileMustExist: true });
  try {
    const data = db.transaction(() => Object.fromEntries(exportTables.filter(table=>db.prepare("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?").get(table)).map(table => {
      const view='compat_'+table;
      const source=db.prepare("SELECT 1 FROM sqlite_master WHERE type='view' AND name=?").get(view)?view:table;
      const columns = db.prepare(`PRAGMA table_info(${source})`).all().map(row => row.name).filter(name => !/password|token|secret/i.test(name));
      const rows=db.prepare(`SELECT ${columns.map(name => '"' + name + '"').join(',')} FROM "${source}"`).all();
      if(table==='notifications'&&!columns.includes('is_read')){
        columns.push('is_read','read_at');
        const receipt=db.prepare('SELECT read_at FROM notification_read_receipts WHERE notification_id=? AND user_id=?');
        for(const row of rows){const read=row.user_id?receipt.get(row.id,row.user_id):undefined;row.is_read=read?1:0;row.read_at=read?.read_at??null;}
      }
      return [table, { columns, rows }];
    })))();
    for (const [table, { columns, rows }] of Object.entries(data)) writeFileSync(join(path, table + '.csv'), '\uFEFF' + [columns.map(csv).join(','), ...rows.map(row => columns.map(name => csv(row[name])).join(','))].join('\r\n'), 'utf8');
    writeFileSync(join(path, 'data.json'), JSON.stringify(Object.fromEntries(Object.entries(data).map(([table,value]) => [table,value.rows])), null, 2));
    history('导出', '成功', path); return { path, tables: Object.keys(data).length };
  } finally { db.close(); }
}
function records() {
  const keyword = option('--keyword').toLowerCase(), start = option('--start'), end = option('--end');
  if(start&&end&&start>end)throw new Error('开始日期不能晚于结束日期');
  const startTime=start?new Date(start+'T00:00:00').toISOString():'';
  const endDate=end?new Date(end+'T00:00:00'):null;if(endDate)endDate.setDate(endDate.getDate()+1);const endTime=endDate?endDate.toISOString():'';
  const db = new Database(databasePath, { readonly: true, fileMustExist: true });
  let result;
  try { result = db.prepare(`SELECT request_id id,operator_name operator,module,action,object_id detail,result,created_at createdAt FROM audit_logs
    WHERE (?='' OR instr(lower(request_id||' '||operator_name||' '||module||' '||action||' '||COALESCE(object_id,'')||' '||result),?)>0)
    AND (?='' OR julianday(created_at)>=julianday(?)) AND (?='' OR julianday(created_at)<julianday(?))
    ORDER BY created_at DESC LIMIT 1000`).all(keyword,keyword,startTime,startTime,endTime,endTime); }
  finally { db.close(); }
  if (existsSync(historyPath)) for (const line of readFileSync(historyPath, 'utf8').split('\n').filter(Boolean)) { try { result.push(JSON.parse(line)); } catch {} }

  const localDay = value => { const date=new Date(value); return `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}`; };

  result = result.filter(row => (!keyword || Object.values(row).join(' ').toLowerCase().includes(keyword)) && (!start || localDay(row.createdAt) >= start) && (!end || localDay(row.createdAt) <= end));
  return { records: result.sort((a,b) => b.createdAt.localeCompare(a.createdAt)).slice(0, 1000) };
}
try {
  let result;
  if (action === 'backup') result = await backup();
  else if (action === 'restore') result = await restore(option('--source'));
  else if (action === 'validate') { result = { path: validateBackup(option('--source')), valid: true }; }
  else if (action === 'export') result = await exportData(option('--output'));
  else if (action === 'records') result = records();
  else if (action === 'list') result = { backups: existsSync(backupRoot) ? readdirSync(backupRoot).filter(name => name.startsWith('backup-') && existsSync(join(backupRoot,name,'manifest.json'))).sort().reverse().map(name => ({ path: join(backupRoot,name), ...JSON.parse(readFileSync(join(backupRoot,name,'manifest.json'),'utf8')) })) : [] };
  else throw new Error('未知数据管理操作');
  process.stdout.write(JSON.stringify(result));
} catch (error) {
  try { history(action || '未知操作', '失败', error.message); } catch {}
  process.stderr.write(error.message); process.exitCode = 1;
}
