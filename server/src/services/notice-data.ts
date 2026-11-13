import {db,normalizedSchema,readTables} from '../core/db.js';
import type {Statement} from 'better-sqlite3';
let noticeList:Statement;
const noticeRole=`CASE
  WHEN n.audience IN ('全部商家','商家订单消息','商家售后消息')
    OR (n.audience='订单消息' AND n.title IN ('收到新订单','新订单待发货','订单已发货','买家确认收货，订单已完成','系统已自动确认收货')) THEN 'MERCHANT'
  WHEN n.audience IN ('全部用户','用户订单消息','用户售后消息') OR n.audience='订单消息' THEN 'USER'
  WHEN n.audience='指定账号' AND n.title='售后进度更新' THEN COALESCE((
    SELECT CASE WHEN sale.buyer_id=n.user_id THEN 'USER' WHEN shop.owner_user_id=n.user_id THEN 'MERCHANT' END
    FROM ${readTables.after_sales} sale JOIN merchants shop ON shop.id=sale.merchant_id
    WHERE sale.id=substr(n.content,5,instr(n.content,'：')-5)
  ),'UNKNOWN')
  ELSE ''
END`;
const withinRole=(column:string)=>`(${column}='' OR (${column}='MERCHANT' AND ?=1) OR (${column}='USER' AND ?=1))`;

export function visibleNotices(userId:string,roles:string[]){
  const buyer=roles.includes('USER')?1:0,merchant=roles.includes('MERCHANT')?1:0;
  return (noticeList??=db.prepare(`WITH available AS (SELECT n.id,n.title,n.content,n.audience,n.created_at,${noticeRole} visibilityRole,
    CASE WHEN ${normalizedSchema?'EXISTS(SELECT 1 FROM notification_read_receipts r WHERE r.notification_id=n.id AND r.user_id=?)':'s.read_at IS NOT NULL OR (n.user_id=? AND n.is_read=1)'} THEN 1 ELSE 0 END is_read
    FROM notifications n LEFT JOIN notification_user_state s ON s.notification_id=n.id AND s.user_id=?
    WHERE n.status='SENT' AND s.deleted_at IS NULL AND (n.user_id=? OR (n.user_id IS NULL AND
      n.created_at >= (SELECT created_at FROM users WHERE id=?))))
    SELECT * FROM available WHERE ${withinRole('visibilityRole')} ORDER BY created_at DESC`)).all(userId,userId,userId,userId,merchant,buyer);
}
const noticeVisibility=db.prepare(`SELECT n.id FROM notifications n LEFT JOIN notification_user_state s ON s.notification_id=n.id AND s.user_id=?
  WHERE n.id=? AND n.status='SENT' AND s.deleted_at IS NULL AND (n.user_id=? OR (n.user_id IS NULL AND
  n.created_at >= (SELECT created_at FROM users WHERE id=?))) AND ${withinRole(`(${noticeRole})`)}`);
export function accessibleNotice(id:string,userId:string,roles:string[]){
  const merchant=roles.includes('MERCHANT')?1:0,buyer=roles.includes('USER')?1:0;
  return !!noticeVisibility.get(userId,id,userId,userId,merchant,buyer);
}
export const readNotice=db.prepare(normalizedSchema?`INSERT INTO notification_read_receipts(notification_id,user_id,read_at) VALUES(?,?,?) ON CONFLICT(notification_id,user_id) DO NOTHING`:`INSERT INTO notification_user_state(notification_id,user_id,read_at) VALUES(?,?,?)
  ON CONFLICT(notification_id,user_id) DO UPDATE SET read_at=COALESCE(notification_user_state.read_at,excluded.read_at)`);
export const deleteNotice=db.prepare(`INSERT INTO notification_user_state(notification_id,user_id,deleted_at) VALUES(?,?,?)
  ON CONFLICT(notification_id,user_id) DO UPDATE SET deleted_at=excluded.deleted_at`);
