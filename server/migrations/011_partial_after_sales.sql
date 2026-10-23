DROP INDEX one_active_after_sale;
CREATE UNIQUE INDEX one_active_after_sale ON after_sales(order_id)
 WHERE status NOT IN ('REJECTED','CANCELLED','COMPLETED');
CREATE TABLE after_sale_items (
 after_sale_id TEXT NOT NULL REFERENCES after_sales(id),
 order_item_id TEXT NOT NULL REFERENCES order_items(id),
 quantity INTEGER NOT NULL CHECK(quantity>0),
 amount_cents INTEGER NOT NULL CHECK(amount_cents>0),
 restock_quantity INTEGER NOT NULL DEFAULT 0 CHECK(restock_quantity>=0),
 PRIMARY KEY(after_sale_id,order_item_id)
);
CREATE INDEX after_sale_items_order_item ON after_sale_items(order_item_id);
-- Historical full-order cases restored all quantities; preserve their behavior.
INSERT INTO after_sale_items(after_sale_id,order_item_id,quantity,amount_cents,restock_quantity)
 SELECT a.id,i.id,i.quantity,i.subtotal_cents,i.quantity
 FROM after_sales a JOIN order_items i ON i.order_id=a.order_id WHERE i.subtotal_cents>0;
CREATE TABLE refunds_partial (
 id TEXT PRIMARY KEY, after_sale_id TEXT NOT NULL UNIQUE REFERENCES after_sales(id),
 order_id TEXT NOT NULL REFERENCES orders(id), amount_cents INTEGER NOT NULL CHECK(amount_cents>=0),
 method TEXT NOT NULL CHECK(method='DEMO'),status TEXT NOT NULL CHECK(status='COMPLETED'),created_at TEXT NOT NULL
);
INSERT INTO refunds_partial SELECT * FROM refunds;
DROP TABLE refunds;
ALTER TABLE refunds_partial RENAME TO refunds;
CREATE INDEX refunds_order ON refunds(order_id);
