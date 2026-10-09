CREATE TABLE IF NOT EXISTS notification_user_state (
  notification_id TEXT NOT NULL REFERENCES notifications(id) ON DELETE CASCADE,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  read_at TEXT,
  deleted_at TEXT,
  PRIMARY KEY (notification_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_notification_user_state_user ON notification_user_state(user_id, deleted_at);
INSERT OR IGNORE INTO notification_user_state(notification_id,user_id,read_at)
  SELECT notification_id,user_id,read_at FROM notification_reads;
