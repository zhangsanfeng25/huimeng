import {readTables} from './db.js';
import { randomUUID } from 'node:crypto';
import { db,normalizedSchema } from './db.js';
import type { Statement } from 'better-sqlite3';

let orderQuery:Statement,noticeInsert:Statement;

type OrderNoticeEvent='CREATED'|'PAID'|'SHIPPED'|'COMPLETED'|'AUTO_COMPLETED';
const titles:Record<OrderNoticeEvent,[string,string]>={
  CREATED:['订单已提交','收到新订单'],
  PAID:['订单支付成功','新订单待发货'],
  SHIPPED:['商家已发货','订单已发货'],
  COMPLETED:['订单已完成','买家确认收货，订单已完成'],
  AUTO_COMPLETED:['订单已自动确认收货','系统已自动确认收货']
};
const descriptions:Record<OrderNoticeEvent,[string,string]>={
  CREATED:['请在订单有效期内完成支付。','买家已下单，等待支付。'],
  PAID:['付款成功，商家将为你准备绘本。','买家已完成支付，请准备发货。'],
  SHIPPED:['商家已发货，可在订单详情查看物流进度。','发货成功，可在订单详情查看物流进度。'],
  COMPLETED:['已确认收货，可前往订单评价绘本。','买家已确认收货，本次订单已完成。'],
  AUTO_COMPLETED:['发货已满30天，系统已自动确认收货，可前往订单评价绘本。','发货已满30天，系统已自动确认收货，本次订单已完成。']
};

/** Call inside the order transaction so state and inbox messages commit together. */
export function createOrderNotifications(orderId:string,event:OrderNoticeEvent,stamp:string):void{
  const order=(orderQuery??=db.prepare(`SELECT o.user_id buyer,o.total_cents total,m.owner_user_id merchant,m.shop_name shop,
    (SELECT product_name FROM ${readTables.order_items} WHERE order_id=o.id LIMIT 1) product
    FROM ${readTables.orders} o JOIN merchants m ON m.id=o.merchant_id WHERE o.id=?`)).get(orderId) as {buyer:string;merchant:string;shop:string;total:number;product:string}|undefined;
  if(!order)throw new Error('订单消息对应的订单不存在');
  const recipients=[order.buyer,order.merchant];
  const insert=noticeInsert??=db.prepare(`INSERT INTO notifications(id,user_id,audience,title,content,status,${normalizedSchema?'':'is_read,'}created_at) VALUES(?,?,?,?,?,'SENT',${normalizedSchema?'':'0,'}?)`);
  const summary=`订单 ${orderId} · ${order.shop} · ${order.product||'绘本订单'} · ¥${(order.total/100).toFixed(2)}`;
  for(let index=0;index<recipients.length;index++){
    if(index===1&&recipients[index]===recipients[0])continue;
    insert.run(`N${randomUUID()}`,recipients[index],index===0?'用户订单消息':'商家订单消息',titles[event][index],`${summary}\n${descriptions[event][index]}`,stamp);
  }
}
