CREATE TABLE sync_receipts (
 user_id TEXT NOT NULL REFERENCES users(id), operation_id TEXT NOT NULL,
 client_id TEXT NOT NULL, payload_hash TEXT NOT NULL, result_json TEXT NOT NULL,
 created_at TEXT NOT NULL, PRIMARY KEY(user_id,operation_id)
);
CREATE TABLE sync_entity_map (
 user_id TEXT NOT NULL REFERENCES users(id),resource TEXT NOT NULL,local_id TEXT NOT NULL,
 remote_id TEXT NOT NULL,PRIMARY KEY(user_id,resource,local_id)
);
CREATE TABLE sync_snapshots (
 id TEXT PRIMARY KEY,user_id TEXT NOT NULL REFERENCES users(id),resource TEXT NOT NULL,
 data_json TEXT NOT NULL,expires_at TEXT NOT NULL
);
CREATE INDEX sync_snapshots_expiry ON sync_snapshots(expires_at);
CREATE TABLE sync_media (
 user_id TEXT NOT NULL REFERENCES users(id),operation_id TEXT NOT NULL,
 sha256 TEXT NOT NULL,url TEXT NOT NULL,purpose TEXT NOT NULL,
 state TEXT NOT NULL CHECK(state IN ('PREPARED','READY')),created_at TEXT NOT NULL,
 PRIMARY KEY(user_id,operation_id)
);
ALTER TABLE addresses ADD COLUMN version INTEGER NOT NULL DEFAULT 1;
ALTER TABLE users ADD COLUMN version INTEGER NOT NULL DEFAULT 1;
ALTER TABLE merchants ADD COLUMN version INTEGER NOT NULL DEFAULT 1;
ALTER TABLE products ADD COLUMN version INTEGER NOT NULL DEFAULT 1;
CREATE TRIGGER addresses_sync_version AFTER UPDATE ON addresses WHEN NEW.version=OLD.version BEGIN UPDATE addresses SET version=OLD.version+1 WHERE id=NEW.id; END;
CREATE TRIGGER users_sync_version AFTER UPDATE ON users WHEN NEW.version=OLD.version BEGIN UPDATE users SET version=OLD.version+1 WHERE id=NEW.id; END;
CREATE TRIGGER merchants_sync_version AFTER UPDATE ON merchants WHEN NEW.version=OLD.version BEGIN UPDATE merchants SET version=OLD.version+1 WHERE id=NEW.id; END;
CREATE TRIGGER products_sync_version AFTER UPDATE ON products WHEN NEW.version=OLD.version BEGIN UPDATE products SET version=OLD.version+1 WHERE id=NEW.id; END;
