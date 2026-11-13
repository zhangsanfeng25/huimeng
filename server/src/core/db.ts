import Database from 'better-sqlite3';
import { dirname } from 'node:path';
import { mkdirSync } from 'node:fs';
import { config } from '../config.js';
import {assertNoInterruptedSwitch} from '../normalization/switch-state.js';

export const maintenanceProbe=assertNoInterruptedSwitch(config.databasePath,process.env.HUIMENG_MAINTENANCE_PROBE);
if(!maintenanceProbe)mkdirSync(dirname(config.databasePath), { recursive: true });
export const db = new Database(config.databasePath,maintenanceProbe?{readonly:true,fileMustExist:true}:undefined);
if(!maintenanceProbe)db.pragma('journal_mode = WAL');
db.pragma('foreign_keys = ON');
db.pragma('busy_timeout = 5000');

// A cutover requires a process restart. Empty/new databases still use legacy
// migrations; a normalized copy already includes its compatibility projections.
export const normalizedSchema = (db.pragma('table_info(orders)') as Array<{name:string}>).some(column=>column.name==='id')
  && !(db.pragma('table_info(orders)') as Array<{name:string}>).some(column=>column.name==='user_id');
export const readTables = Object.fromEntries(
  ['orders','order_items','after_sales','refunds','ratings','product_revisions','community_posts']
    .map(table=>[table,normalizedSchema?`compat_${table}`:table])
) as Record<string,string>;

export const now = () => new Date().toISOString();
export const tx = <T>(fn: () => T) => db.transaction(fn)();
