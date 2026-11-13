import {createCipheriv, createDecipheriv, randomBytes} from 'node:crypto';
import {db} from '../core/db.js';
import {sameAiEndpoint} from './ai-endpoint.js';

type Settings = {endpoint: string; model: string; enabled: boolean};
type ConfigRow = Omit<Settings, 'enabled'> & {enabled: number; encrypted_key: string};

function ensureTables() {
  db.exec(`
    CREATE TABLE IF NOT EXISTS ai_config (
      id INTEGER PRIMARY KEY CHECK(id=1), endpoint TEXT NOT NULL, model TEXT NOT NULL,
      enabled INTEGER NOT NULL CHECK(enabled IN (0,1)), encrypted_key TEXT NOT NULL
    );
    CREATE TABLE IF NOT EXISTS ai_usage (
      role TEXT PRIMARY KEY, succeeded INTEGER NOT NULL, failed INTEGER NOT NULL, duration_ms INTEGER NOT NULL
    );
  `);
}

function readConfig() {
  ensureTables();
  return db.prepare('SELECT endpoint,model,enabled,encrypted_key FROM ai_config WHERE id=1').get() as ConfigRow | undefined;
}

function masterKey() {
  const key = Buffer.from(process.env.HUIMENG_AI_MASTER_KEY ?? '', 'base64');
  if (key.length !== 32) throw new Error('请在服务端设置 32 字节的 AI 加密主密钥');
  return key;
}

function encrypt(value: string) {
  const iv = randomBytes(12);
  const cipher = createCipheriv('aes-256-gcm', masterKey(), iv);
  const encrypted = Buffer.concat([cipher.update(value, 'utf8'), cipher.final()]);
  return [iv, cipher.getAuthTag(), encrypted].map(part => part.toString('base64')).join('.');
}

export function readAiCredentials() {
  const row = readConfig();
  if (!row?.encrypted_key) return {endpoint: row?.endpoint ?? '', model: row?.model ?? '', apiKey: ''};
  const [iv, tag, encrypted] = row.encrypted_key.split('.').map(part => Buffer.from(part, 'base64'));
  const decipher = createDecipheriv('aes-256-gcm', masterKey(), iv);
  decipher.setAuthTag(tag);
  const apiKey = Buffer.concat([decipher.update(encrypted), decipher.final()]).toString('utf8');
  return {endpoint: row.endpoint, model: row.model, apiKey};
}

export function publicAiConfig() {
  const row = readConfig();
  return {endpoint: row?.endpoint ?? '', model: row?.model ?? '', enabled: row?.enabled === 1, keyConfigured: Boolean(row?.encrypted_key)};
}

export function saveAiConfig(settings: Settings, key: string) {
  const previous = readConfig();
  const encrypted = key ? encrypt(key) : previous && sameAiEndpoint(previous.endpoint, settings.endpoint) ? previous.encrypted_key : '';
  db.prepare(`INSERT INTO ai_config(id,endpoint,model,enabled,encrypted_key) VALUES(1,?,?,?,?)
    ON CONFLICT(id) DO UPDATE SET endpoint=excluded.endpoint,model=excluded.model,enabled=excluded.enabled,encrypted_key=excluded.encrypted_key`)
    .run(settings.endpoint, settings.model, settings.enabled ? 1 : 0, encrypted);
  return publicAiConfig();
}

export function aiUsage() {
  ensureTables();
  return db.prepare('SELECT role,succeeded,failed,duration_ms durationMs FROM ai_usage ORDER BY role').all();
}

export function recordAiCall(role: string, success: boolean, durationMs: number) {
  ensureTables();
  db.prepare(`INSERT INTO ai_usage(role,succeeded,failed,duration_ms) VALUES(?,?,?,?)
    ON CONFLICT(role) DO UPDATE SET succeeded=succeeded+excluded.succeeded,failed=failed+excluded.failed,duration_ms=duration_ms+excluded.duration_ms`)
    .run(role, success ? 1 : 0, success ? 0 : 1, durationMs);
}
