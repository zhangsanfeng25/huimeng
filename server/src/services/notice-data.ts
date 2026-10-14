import {db} from '../core/db.js';
import type {Statement} from 'better-sqlite3';
let noticeList:Statement;
export function visibleNotices(userId:string,roles:string[]){
  const buyer=roles.includes('USER')?1:0,merchant=roles.includes('MERCHANT')?1:0;
  return (noticeList??=db.prepare(`SELECT n.id,n.title,n.content,n.audience,n.created_at,
    CASE WHEN s.read_at IS NOT NULL OR (n.user_id=? AND n.is_read=1) THEN 1 ELSE 0 END is_read
    FROM notifications n LEFT JOIN notification_user_state s ON s.notification_id=n.id AND s.user_id=?
    WHERE n.status='SENT' AND s.deleted_at IS NULL AND (n.user_id=? OR (n.user_id IS NULL AND
      n.created_at >= (SELECT created_at FROM users WHERE id=?) AND
      (n.audience NOT IN ('全部商家','全部用户') OR (n.audience='全部商家' AND ?=1) OR (n.audience='全部用户' AND ?=1))))
    ORDER BY n.created_at DESC`)).all(userId,userId,userId,userId,merchant,buyer);
}
const noticeVisibility=db.prepare(`SELECT n.id FROM notifications n LEFT JOIN notification_user_state s ON s.notification_id=n.id AND s.user_id=?
  WHERE n.id=? AND n.status='SENT' AND s.deleted_at IS NULL AND (n.user_id=? OR (n.user_id IS NULL AND
  n.created_at >= (SELECT created_at FROM users WHERE id=?) AND
  (n.audience NOT IN ('全部商家','全部用户') OR (n.audience='全部商家' AND ?=1) OR (n.audience='全部用户' AND ?=1))))`);
export function accessibleNotice(id:string,userId:string,roles:string[]){
  return !!noticeVisibility.get(userId,id,userId,userId,roles.includes('MERCHANT')?1:0,roles.includes('USER')?1:0);
}
export const readNotice=db.prepare(`INSERT INTO notification_user_state(notification_id,user_id,read_at) VALUES(?,?,?)
  ON CONFLICT(notification_id,user_id) DO UPDATE SET read_at=COALESCE(notification_user_state.read_at,excluded.read_at)`);
export const deleteNotice=db.prepare(`INSERT INTO notification_user_state(notification_id,user_id,deleted_at) VALUES(?,?,?)
  ON CONFLICT(notification_id,user_id) DO UPDATE SET deleted_at=excluded.deleted_at`);
