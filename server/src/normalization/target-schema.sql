-- Candidate schema for isolated rehearsal only. Never auto-applied by startup.
-- Original 001-011 migrations are retained; legacy notification_reads is archived.

CREATE TABLE addresses (
  id TEXT NOT NULL PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  recipient TEXT NOT NULL,
  phone TEXT NOT NULL,
  region TEXT NOT NULL,
  detail TEXT NOT NULL,
  is_default INTEGER NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  version INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE after_sale_events(
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  after_sale_id TEXT NOT NULL REFERENCES after_sales(id),
  actor_id TEXT NOT NULL REFERENCES users(id),
  action TEXT NOT NULL,
  note TEXT NOT NULL DEFAULT '',
  created_at TEXT NOT NULL
);

CREATE TABLE after_sale_items (
  after_sale_id TEXT NOT NULL REFERENCES after_sales(id),
  order_item_id TEXT NOT NULL REFERENCES order_items(id),
  quantity INTEGER NOT NULL CHECK(quantity>0),
  amount_cents INTEGER NOT NULL CHECK(amount_cents>0),
  restock_quantity INTEGER NOT NULL DEFAULT 0 CHECK(restock_quantity>=0),
  PRIMARY KEY(after_sale_id,order_item_id)
);

CREATE TABLE after_sales (
  id TEXT NOT NULL PRIMARY KEY,
  order_id TEXT NOT NULL REFERENCES orders(id),
  type TEXT NOT NULL CHECK(type IN ('REFUND_ONLY','RETURN_REFUND')),
  amount_cents INTEGER NOT NULL CHECK(amount_cents>=0),
  reason TEXT NOT NULL,
  description TEXT NOT NULL DEFAULT '',
  status TEXT NOT NULL CHECK(status IN ('MERCHANT_REVIEW','ADMIN_REVIEW','WAITING_RETURN','RETURNING','REFUND_PENDING','COMPLETED','REJECTED','CANCELLED')),
  merchant_note TEXT NOT NULL DEFAULT '',
  return_address TEXT NOT NULL DEFAULT '',
  carrier TEXT NOT NULL DEFAULT '',
  tracking_no TEXT NOT NULL DEFAULT '',
  version INTEGER NOT NULL DEFAULT 1,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE TABLE audit_logs (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  request_id TEXT NOT NULL,
  operator_id TEXT NOT NULL,
  operator_name TEXT NOT NULL,
  module TEXT NOT NULL,
  action TEXT NOT NULL,
  object_id TEXT NOT NULL,
  result TEXT NOT NULL,
  summary TEXT,
  created_at TEXT NOT NULL
);

CREATE TABLE cart_items (
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  product_id TEXT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
  quantity INTEGER NOT NULL CHECK(quantity > 0),
  selected INTEGER NOT NULL DEFAULT 1,
  updated_at TEXT NOT NULL,
  PRIMARY KEY(user_id, product_id)
);

CREATE TABLE categories (
  id TEXT NOT NULL PRIMARY KEY,
  name TEXT NOT NULL UNIQUE,
  parent_name TEXT NOT NULL,
  sort_order INTEGER NOT NULL DEFAULT 0,
  status TEXT NOT NULL DEFAULT 'ENABLED' CHECK(status IN ('ENABLED','DISABLED')),
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE TABLE chat_messages(
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  conversation_id TEXT NOT NULL REFERENCES conversations(id),
  sender_id TEXT NOT NULL REFERENCES users(id),
  sender_role TEXT NOT NULL CHECK(sender_role IN ('USER','MERCHANT')),
  content TEXT NOT NULL,
  product_id TEXT REFERENCES products(id),
  order_id TEXT REFERENCES orders(id),
  client_id TEXT NOT NULL,
  created_at TEXT NOT NULL,
  UNIQUE(sender_id,sender_role,client_id)
);

CREATE TABLE checkout_groups (
  id TEXT NOT NULL PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id),
  total_cents INTEGER NOT NULL,
  status TEXT NOT NULL CHECK(status IN ('PENDING','PAID','CLOSED')),
  version INTEGER NOT NULL DEFAULT 1,
  idempotency_key TEXT NOT NULL,
  request_hash TEXT NOT NULL,
  created_at TEXT NOT NULL,
  expires_at TEXT NOT NULL,
  UNIQUE(user_id, idempotency_key)
);

CREATE TABLE comments (
  id TEXT NOT NULL PRIMARY KEY,
  post_id TEXT NOT NULL REFERENCES community_posts(id) ON DELETE CASCADE,
  user_id TEXT NOT NULL REFERENCES users(id),
  content TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'PUBLIC',
  created_at TEXT NOT NULL
);

CREATE TABLE community_posts (
  id TEXT NOT NULL PRIMARY KEY,
  author_user_id TEXT NOT NULL REFERENCES users(id),
  author_role TEXT NOT NULL,
  title TEXT NOT NULL,
  content TEXT NOT NULL,
  cover_url TEXT,
  product_id TEXT REFERENCES products(id),
  status TEXT NOT NULL DEFAULT 'PUBLIC' CHECK(status IN ('PUBLIC','HIDDEN','DELETED')),
  report_count INTEGER NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE TABLE community_uploads (
  path TEXT NOT NULL PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  post_id TEXT REFERENCES community_posts(id) ON DELETE SET NULL,
  created_at TEXT NOT NULL
);

CREATE TABLE conversation_reads(
  conversation_id TEXT NOT NULL REFERENCES conversations(id),
  reader_role TEXT NOT NULL CHECK(reader_role IN ('USER','MERCHANT')),
  last_message_id INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY(conversation_id,reader_role)
);

CREATE TABLE conversations(
  id TEXT NOT NULL PRIMARY KEY,
  buyer_id TEXT NOT NULL REFERENCES users(id),
  merchant_id TEXT NOT NULL REFERENCES merchants(id),
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  UNIQUE(buyer_id,merchant_id)
);

CREATE TABLE favorites (
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  product_id TEXT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
  created_at TEXT NOT NULL,
  PRIMARY KEY(user_id, product_id)
);

CREATE TABLE feedback_replies(
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  feedback_id TEXT NOT NULL REFERENCES feedbacks(id),
  sender_id TEXT NOT NULL REFERENCES users(id),
  sender_role TEXT NOT NULL,
  content TEXT NOT NULL,
  client_id TEXT NOT NULL,
  created_at TEXT NOT NULL,
  UNIQUE(sender_id,client_id)
);

CREATE TABLE feedbacks(
  id TEXT NOT NULL PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id),
  submitter_role TEXT NOT NULL,
  title TEXT NOT NULL,
  type TEXT NOT NULL,
  content TEXT NOT NULL,
  status TEXT NOT NULL CHECK(status IN ('OPEN','PROCESSING','RESOLVED')),
  client_id TEXT NOT NULL,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  UNIQUE(user_id,client_id)
);

CREATE TABLE merchants (
  id TEXT NOT NULL PRIMARY KEY,
  owner_user_id TEXT NOT NULL UNIQUE REFERENCES users(id),
  shop_name TEXT NOT NULL,
  description TEXT NOT NULL DEFAULT '',
  approval_status TEXT NOT NULL DEFAULT 'PENDING' CHECK(approval_status IN ('PENDING','APPROVED','REJECTED')),
  business_status TEXT NOT NULL DEFAULT 'PENDING' CHECK(business_status IN ('PENDING','OPEN','SUSPENDED')),
  rejection_reason TEXT,
  applied_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  contact_name TEXT NOT NULL DEFAULT '',
  contact_phone TEXT NOT NULL DEFAULT '',
  business_address TEXT NOT NULL DEFAULT '',
  reviewed_at TEXT,
  reviewed_by TEXT REFERENCES users(id),
  version INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE notification_read_receipts (
  notification_id TEXT NOT NULL REFERENCES notifications(id) ON DELETE CASCADE,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  read_at TEXT,
  PRIMARY KEY (notification_id,user_id)
);
CREATE INDEX idx_notification_read_receipts_user ON notification_read_receipts(user_id);

CREATE TABLE notification_reads (
  notification_id TEXT NOT NULL REFERENCES notifications(id) ON DELETE CASCADE,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  read_at TEXT NOT NULL,
  PRIMARY KEY (notification_id, user_id)
);

CREATE TABLE notification_user_state (
  notification_id TEXT NOT NULL REFERENCES notifications(id) ON DELETE CASCADE,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  deleted_at TEXT,
  PRIMARY KEY (notification_id, user_id)
);

CREATE TABLE notifications (
  id TEXT NOT NULL PRIMARY KEY,
  user_id TEXT REFERENCES users(id) ON DELETE CASCADE,
  audience TEXT NOT NULL,
  title TEXT NOT NULL,
  content TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'SENT',
  created_at TEXT NOT NULL
);

CREATE TABLE order_events (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  order_id TEXT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
  actor_id TEXT NOT NULL,
  action TEXT NOT NULL,
  from_status TEXT,
  to_status TEXT NOT NULL,
  note TEXT,
  created_at TEXT NOT NULL
);

CREATE TABLE order_items (
  id TEXT NOT NULL PRIMARY KEY,
  order_id TEXT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
  product_id TEXT NOT NULL REFERENCES products(id),
  product_name TEXT NOT NULL,
  cover_url TEXT,
  unit_price_cents INTEGER NOT NULL,
  quantity INTEGER NOT NULL CHECK(quantity > 0)
);

CREATE TABLE orders (
  id TEXT NOT NULL PRIMARY KEY,
  checkout_group_id TEXT NOT NULL REFERENCES checkout_groups(id),
  merchant_id TEXT NOT NULL REFERENCES merchants(id),
  total_cents INTEGER NOT NULL CHECK(total_cents >= 0),
  version INTEGER NOT NULL DEFAULT 1,
  paid_at TEXT,
  shipped_at TEXT,
  completed_at TEXT,
  cancelled_at TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  logistics_status TEXT NOT NULL DEFAULT 'WAITING_PAYMENT'
);

CREATE TABLE payments (
  id TEXT NOT NULL PRIMARY KEY,
  checkout_group_id TEXT NOT NULL UNIQUE REFERENCES checkout_groups(id),
  amount_cents INTEGER NOT NULL,
  method TEXT NOT NULL DEFAULT 'DEMO',
  idempotency_key TEXT NOT NULL UNIQUE,
  paid_at TEXT NOT NULL
);

CREATE TABLE post_likes (
  post_id TEXT NOT NULL REFERENCES community_posts(id) ON DELETE CASCADE,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at TEXT NOT NULL,
  PRIMARY KEY (post_id, user_id)
);

CREATE TABLE product_revisions (
  id TEXT NOT NULL PRIMARY KEY,
  product_id TEXT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
  version INTEGER NOT NULL,
  name TEXT NOT NULL,
  summary TEXT NOT NULL,
  age_range TEXT NOT NULL,
  price_cents INTEGER NOT NULL CHECK(price_cents >= 0),
  cover_url TEXT,
  audio_url TEXT,
  ar_enabled INTEGER NOT NULL DEFAULT 0,
  ar_resource_url TEXT,
  review_status TEXT NOT NULL DEFAULT 'DRAFT' CHECK(review_status IN ('DRAFT','PENDING','APPROVED','REJECTED')),
  rejection_reason TEXT,
  submitted_at TEXT,
  reviewed_at TEXT,
  reviewed_by TEXT REFERENCES users(id),
  created_at TEXT NOT NULL,
  author_name TEXT NOT NULL DEFAULT '',
  illustrator_name TEXT NOT NULL DEFAULT '',
  publisher TEXT NOT NULL DEFAULT '',
  publication_date TEXT NOT NULL DEFAULT '',
  author_bio TEXT NOT NULL DEFAULT '',
  illustrator_bio TEXT NOT NULL DEFAULT '',
  editorial_review TEXT NOT NULL DEFAULT '',
  metadata_source TEXT NOT NULL DEFAULT '',
  UNIQUE(product_id, version)
);

CREATE TABLE products (
  id TEXT NOT NULL PRIMARY KEY,
  merchant_id TEXT NOT NULL REFERENCES merchants(id),
  category_id TEXT NOT NULL REFERENCES categories(id),
  published_revision_id TEXT REFERENCES product_revisions(id) DEFERRABLE INITIALLY DEFERRED,
  status TEXT NOT NULL DEFAULT 'DRAFT' CHECK(status IN ('DRAFT','PENDING_REVIEW','ONLINE','OFFLINE','REJECTED','PLATFORM_OFFLINE')),
  stock_available INTEGER NOT NULL DEFAULT 0 CHECK(stock_available >= 0),
  stock_reserved INTEGER NOT NULL DEFAULT 0 CHECK(stock_reserved >= 0),
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  version INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE ratings (
  id TEXT NOT NULL PRIMARY KEY,
  order_item_id TEXT NOT NULL UNIQUE REFERENCES order_items(id),
  stars INTEGER NOT NULL CHECK(stars BETWEEN 1 AND 5),
  content TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'PUBLIC' CHECK(status IN ('PUBLIC','HIDDEN')),
  created_at TEXT NOT NULL,
  deleted_at TEXT
);

CREATE TABLE "refunds" (
  id TEXT NOT NULL PRIMARY KEY,
  after_sale_id TEXT NOT NULL UNIQUE REFERENCES after_sales(id),
  amount_cents INTEGER NOT NULL CHECK(amount_cents>=0),
  method TEXT NOT NULL CHECK(method='DEMO'),
  status TEXT NOT NULL CHECK(status='COMPLETED'),
  created_at TEXT NOT NULL
);

CREATE TABLE schema_migrations(
  version TEXT NOT NULL PRIMARY KEY,
  applied_at TEXT NOT NULL
);

CREATE TABLE sessions (
  token_hash TEXT NOT NULL PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  active_role TEXT NOT NULL,
  expires_at TEXT NOT NULL,
  created_at TEXT NOT NULL
);

CREATE TABLE settings (
  key TEXT NOT NULL PRIMARY KEY,
  value TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE TABLE stock_movements (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  product_id TEXT NOT NULL REFERENCES products(id),
  order_id TEXT REFERENCES orders(id),
  type TEXT NOT NULL,
  available_delta INTEGER NOT NULL,
  reserved_delta INTEGER NOT NULL,
  created_at TEXT NOT NULL
);

CREATE TABLE sync_entity_map (
  user_id TEXT NOT NULL REFERENCES users(id),
  resource TEXT NOT NULL,
  local_id TEXT NOT NULL,
  remote_id TEXT NOT NULL,
  PRIMARY KEY(user_id,resource,local_id)
);

CREATE TABLE sync_media (
  user_id TEXT NOT NULL REFERENCES users(id),
  operation_id TEXT NOT NULL,
  sha256 TEXT NOT NULL,
  url TEXT NOT NULL,
  purpose TEXT NOT NULL,
  state TEXT NOT NULL CHECK(state IN ('PREPARED','READY')),
  created_at TEXT NOT NULL,
  PRIMARY KEY(user_id,operation_id)
);

CREATE TABLE sync_receipts (
  user_id TEXT NOT NULL REFERENCES users(id),
  operation_id TEXT NOT NULL,
  client_id TEXT NOT NULL,
  payload_hash TEXT NOT NULL,
  result_json TEXT NOT NULL,
  created_at TEXT NOT NULL,
  PRIMARY KEY(user_id,operation_id)
);

CREATE TABLE sync_snapshots (
  id TEXT NOT NULL PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id),
  resource TEXT NOT NULL,
  data_json TEXT NOT NULL,
  expires_at TEXT NOT NULL
);

CREATE TABLE user_roles (
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  role TEXT NOT NULL CHECK(role IN ('USER','MERCHANT','ADMIN','OPERATOR','SUPPORT')),
  PRIMARY KEY(user_id, role)
);

CREATE TABLE users (
  id TEXT NOT NULL PRIMARY KEY,
  username TEXT NOT NULL UNIQUE,
  email TEXT UNIQUE,
  phone TEXT UNIQUE,
  nickname TEXT NOT NULL,
  password_hash TEXT NOT NULL,
  avatar_url TEXT,
  status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK(status IN ('ACTIVE','DISABLED')),
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  version INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE order_address_snapshots(
 order_id TEXT NOT NULL PRIMARY KEY REFERENCES orders(id),field_mask INTEGER NOT NULL CHECK(field_mask BETWEEN 0 AND 1023),
 source_address_id TEXT,source_user_id TEXT,recipient TEXT,phone TEXT,region TEXT,detail TEXT,source_is_default INTEGER,
 source_created_at TEXT,source_updated_at TEXT,source_version INTEGER
);
CREATE TABLE after_sale_requests(buyer_id TEXT NOT NULL REFERENCES users(id),client_id TEXT NOT NULL,after_sale_id TEXT NOT NULL UNIQUE REFERENCES after_sales(id),PRIMARY KEY(buyer_id,client_id));
CREATE TABLE product_revision_images(revision_id TEXT NOT NULL REFERENCES product_revisions(id) ON DELETE CASCADE,position INTEGER NOT NULL CHECK(position>=0),url TEXT NOT NULL CHECK(length(trim(url))>0),PRIMARY KEY(revision_id,position));
CREATE TABLE product_revision_contents(revision_id TEXT NOT NULL REFERENCES product_revisions(id) ON DELETE CASCADE,position INTEGER NOT NULL CHECK(position>=0),title TEXT NOT NULL CHECK(length(trim(title))>0),PRIMARY KEY(revision_id,position));
CREATE TABLE community_post_images(post_id TEXT NOT NULL REFERENCES community_posts(id) ON DELETE CASCADE,position INTEGER NOT NULL CHECK(position>=0),url TEXT NOT NULL CHECK(length(trim(url))>0),PRIMARY KEY(post_id,position));
CREATE TABLE after_sale_images(after_sale_id TEXT NOT NULL REFERENCES after_sales(id),position INTEGER NOT NULL CHECK(position>=0),url TEXT NOT NULL CHECK(length(trim(url))>0),PRIMARY KEY(after_sale_id,position));

CREATE INDEX after_sale_items_order_item ON after_sale_items(order_item_id);

CREATE INDEX chat_messages_page ON chat_messages(conversation_id,id);

CREATE INDEX conversations_merchant ON conversations(merchant_id,updated_at);

CREATE INDEX feedbacks_owner ON feedbacks(user_id,created_at);

CREATE INDEX idx_community_uploads_user ON community_uploads(user_id);

CREATE INDEX idx_notification_reads_user ON notification_reads(user_id);

CREATE INDEX idx_notification_user_created ON notifications(user_id, created_at DESC);

CREATE INDEX idx_notification_user_state_user ON notification_user_state(user_id, deleted_at);

CREATE INDEX idx_post_likes_user ON post_likes(user_id);

CREATE INDEX idx_post_status_created ON community_posts(status, created_at DESC);

CREATE INDEX idx_product_merchant ON products(merchant_id);

CREATE INDEX idx_product_status ON products(status);

CREATE UNIQUE INDEX one_active_after_sale ON after_sales(order_id)
 WHERE status NOT IN ('REJECTED','CANCELLED','COMPLETED');

CREATE INDEX sync_snapshots_expiry ON sync_snapshots(expires_at);

CREATE INDEX idx_order_group_logistics ON orders(checkout_group_id,logistics_status);
CREATE INDEX idx_order_merchant_logistics ON orders(merchant_id,logistics_status);
CREATE INDEX idx_after_sales_order_status ON after_sales(order_id,status);

CREATE TRIGGER addresses_sync_version AFTER UPDATE ON addresses WHEN NEW.version=OLD.version BEGIN UPDATE addresses SET version=OLD.version+1 WHERE id=NEW.id; END;

CREATE TRIGGER merchants_sync_version AFTER UPDATE ON merchants WHEN NEW.version=OLD.version BEGIN UPDATE merchants SET version=OLD.version+1 WHERE id=NEW.id; END;

CREATE TRIGGER products_sync_version AFTER UPDATE ON products WHEN NEW.version=OLD.version BEGIN UPDATE products SET version=OLD.version+1 WHERE id=NEW.id; END;

CREATE TRIGGER users_sync_version AFTER UPDATE ON users WHEN NEW.version=OLD.version BEGIN UPDATE users SET version=OLD.version+1 WHERE id=NEW.id; END;
