import Database from 'better-sqlite3';
import { dirname } from 'node:path';
import { mkdirSync } from 'node:fs';
import { config } from '../config.js';

mkdirSync(dirname(config.databasePath), { recursive: true });
export const db = new Database(config.databasePath);
db.pragma('journal_mode = WAL');
db.pragma('foreign_keys = ON');
db.pragma('busy_timeout = 5000');

export const now = () => new Date().toISOString();
export const tx = <T>(fn: () => T) => db.transaction(fn)();
