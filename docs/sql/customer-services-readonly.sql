-- 只读查询：在 SQLite 工具中运行。建议打开控制中心生成的备份副本。
-- 会话与消息正文
SELECT c.id AS conversation_id, u.nickname AS buyer, m.shop_name AS store,
       msg.id AS message_id, sender.nickname AS sender, msg.sender_role,
       msg.content, msg.product_id, msg.order_id, msg.created_at
FROM conversations c
JOIN users u ON u.id=c.buyer_id
JOIN merchants m ON m.id=c.merchant_id
JOIN chat_messages msg ON msg.conversation_id=c.id
JOIN users sender ON sender.id=msg.sender_id
ORDER BY msg.id DESC LIMIT 100;

-- 替换为需要查询的订单编号
SELECT a.*, e.actor_id, e.action, e.note, e.created_at AS event_time
FROM after_sales a LEFT JOIN after_sale_events e ON e.after_sale_id=a.id
WHERE a.order_id='替换订单编号' ORDER BY e.id;

SELECT id,after_sale_id,order_id,amount_cents,method,status,created_at
FROM refunds ORDER BY created_at DESC LIMIT 100;

SELECT f.id,f.title,f.type,f.content,f.status,u.nickname AS submitter,
       f.submitter_role,f.created_at,r.sender_role,r.content AS reply,r.created_at AS reply_time
FROM feedbacks f JOIN users u ON u.id=f.user_id
LEFT JOIN feedback_replies r ON r.feedback_id=f.id
ORDER BY f.created_at DESC,r.id LIMIT 200;
