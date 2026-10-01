ALTER TABLE sales_orders ADD COLUMN posting_date date;
ALTER TABLE purchase_orders ADD COLUMN posting_date date;

UPDATE sales_orders
SET posting_date = doc_date
WHERE status IN ('approved', 'reversed') AND posting_date IS NULL;

UPDATE purchase_orders
SET posting_date = doc_date
WHERE status IN ('approved', 'reversed') AND posting_date IS NULL;

CREATE INDEX idx_sales_orders_posting ON sales_orders (status, posting_date);
CREATE INDEX idx_purchase_orders_posting ON purchase_orders (status, posting_date);
