CREATE TABLE after_sales (
 id TEXT PRIMARY KEY, order_id TEXT NOT NULL REFERENCES orders(id), buyer_id TEXT NOT NULL REFERENCES users(id), merchant_id TEXT NOT NULL REFERENCES merchants(id),
 type TEXT NOT NULL CHECK(type IN ('REFUND_ONLY','RETURN_REFUND')), amount_cents INTEGER NOT NULL CHECK(amount_cents>=0),
 reason TEXT NOT NULL, description TEXT NOT NULL DEFAULT '', images_json TEXT NOT NULL DEFAULT '[]',
 status TEXT NOT NULL CHECK(status IN ('MERCHANT_REVIEW','ADMIN_REVIEW','WAITING_RETURN','RETURNING','REFUND_PENDING','COMPLETED','REJECTED','CANCELLED')),
 merchant_note TEXT NOT NULL DEFAULT '', return_address TEXT NOT NULL DEFAULT '', carrier TEXT NOT NULL DEFAULT '', tracking_no TEXT NOT NULL DEFAULT '',
 client_id TEXT NOT NULL, version INTEGER NOT NULL DEFAULT 1, created_at TEXT NOT NULL, updated_at TEXT NOT NULL, UNIQUE(buyer_id,client_id)
);
CREATE UNIQUE INDEX one_active_after_sale ON after_sales(order_id) WHERE status NOT IN ('REJECTED','CANCELLED');
CREATE INDEX after_sales_buyer ON after_sales(buyer_id,created_at);
CREATE INDEX after_sales_merchant ON after_sales(merchant_id,status,created_at);
CREATE TABLE after_sale_events(id INTEGER PRIMARY KEY AUTOINCREMENT,after_sale_id TEXT NOT NULL REFERENCES after_sales(id),actor_id TEXT NOT NULL REFERENCES users(id),action TEXT NOT NULL,note TEXT NOT NULL DEFAULT '',created_at TEXT NOT NULL);
CREATE TABLE refunds(id TEXT PRIMARY KEY,after_sale_id TEXT NOT NULL UNIQUE REFERENCES after_sales(id),order_id TEXT NOT NULL UNIQUE REFERENCES orders(id),amount_cents INTEGER NOT NULL CHECK(amount_cents>=0),method TEXT NOT NULL CHECK(method='DEMO'),status TEXT NOT NULL CHECK(status='COMPLETED'),created_at TEXT NOT NULL);
CREATE TABLE conversations(id TEXT PRIMARY KEY,buyer_id TEXT NOT NULL REFERENCES users(id),merchant_id TEXT NOT NULL REFERENCES merchants(id),created_at TEXT NOT NULL,updated_at TEXT NOT NULL,UNIQUE(buyer_id,merchant_id));
CREATE INDEX conversations_merchant ON conversations(merchant_id,updated_at);
CREATE TABLE chat_messages(id INTEGER PRIMARY KEY AUTOINCREMENT,conversation_id TEXT NOT NULL REFERENCES conversations(id),sender_id TEXT NOT NULL REFERENCES users(id),sender_role TEXT NOT NULL CHECK(sender_role IN ('USER','MERCHANT')),content TEXT NOT NULL,product_id TEXT REFERENCES products(id),order_id TEXT REFERENCES orders(id),client_id TEXT NOT NULL,created_at TEXT NOT NULL,UNIQUE(sender_id,sender_role,client_id));
CREATE INDEX chat_messages_page ON chat_messages(conversation_id,id);
CREATE TABLE conversation_reads(conversation_id TEXT NOT NULL REFERENCES conversations(id),reader_role TEXT NOT NULL CHECK(reader_role IN ('USER','MERCHANT')),last_message_id INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(conversation_id,reader_role));
CREATE TABLE feedbacks(id TEXT PRIMARY KEY,user_id TEXT NOT NULL REFERENCES users(id),submitter_role TEXT NOT NULL,title TEXT NOT NULL,type TEXT NOT NULL,content TEXT NOT NULL,status TEXT NOT NULL CHECK(status IN ('OPEN','PROCESSING','RESOLVED')),client_id TEXT NOT NULL,created_at TEXT NOT NULL,updated_at TEXT NOT NULL,UNIQUE(user_id,client_id));
CREATE INDEX feedbacks_owner ON feedbacks(user_id,created_at);
CREATE TABLE feedback_replies(id INTEGER PRIMARY KEY AUTOINCREMENT,feedback_id TEXT NOT NULL REFERENCES feedbacks(id),sender_id TEXT NOT NULL REFERENCES users(id),sender_role TEXT NOT NULL,content TEXT NOT NULL,client_id TEXT NOT NULL,created_at TEXT NOT NULL,UNIQUE(sender_id,client_id));
