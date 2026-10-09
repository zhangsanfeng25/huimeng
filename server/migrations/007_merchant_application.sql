ALTER TABLE merchants ADD COLUMN contact_name TEXT NOT NULL DEFAULT '';
ALTER TABLE merchants ADD COLUMN contact_phone TEXT NOT NULL DEFAULT '';
ALTER TABLE merchants ADD COLUMN business_address TEXT NOT NULL DEFAULT '';
ALTER TABLE merchants ADD COLUMN reviewed_at TEXT;
ALTER TABLE merchants ADD COLUMN reviewed_by TEXT REFERENCES users(id);
