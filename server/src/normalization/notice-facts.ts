import type {SchemaPhase,Sqlite} from './types.js';
export type NotificationReadFact={notification_id:string;user_id:string;read_at:string|null};
/** Personal state takes precedence; NULL records a known read with unknown time. */
export function notificationReadFacts(db:Sqlite,phase:SchemaPhase):NotificationReadFact[]{
 if(phase==='normalized')return db.prepare('SELECT notification_id,user_id,read_at FROM notification_read_receipts ORDER BY notification_id,user_id').all() as NotificationReadFact[];
 const facts=new Map<string,NotificationReadFact>();
 const add=(row:NotificationReadFact)=>facts.set(JSON.stringify([row.notification_id,row.user_id]),row);
 for(const row of db.prepare('SELECT id notification_id,user_id,read_at FROM notifications WHERE user_id IS NOT NULL AND is_read=1').all() as NotificationReadFact[])add(row);
 for(const row of db.prepare('SELECT notification_id,user_id,read_at FROM notification_reads').all() as NotificationReadFact[])add(row);
 for(const row of db.prepare('SELECT notification_id,user_id,read_at FROM notification_user_state WHERE read_at IS NOT NULL').all() as NotificationReadFact[])add(row);
 return [...facts.values()].sort((a,b)=>a.notification_id.localeCompare(b.notification_id)||a.user_id.localeCompare(b.user_id));
}
