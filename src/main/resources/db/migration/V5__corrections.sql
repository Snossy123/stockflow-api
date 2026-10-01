ALTER TABLE so_items ADD COLUMN cut boolean NOT NULL DEFAULT false;
ALTER TABLE so_items ADD COLUMN source_line_id bigint REFERENCES so_items (id);

ALTER TABLE payments ADD COLUMN kind varchar(16) NOT NULL DEFAULT 'payment';
UPDATE payments SET kind = 'receipt' WHERE party_type = 'customer';
UPDATE payments SET kind = 'payment' WHERE party_type = 'supplier';

ALTER TABLE expenses ADD COLUMN status varchar(16) NOT NULL DEFAULT 'approved';
ALTER TABLE expenses ADD COLUMN reversed_of_id bigint REFERENCES expenses (id);

INSERT INTO expense_types (name, affects_stock)
SELECT 'عجز جرد', false
WHERE NOT EXISTS (SELECT 1 FROM expense_types WHERE name = 'عجز جرد');
