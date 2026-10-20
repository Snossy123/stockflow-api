ALTER TABLE so_items
    ADD COLUMN cut_qty numeric(12, 3) NOT NULL DEFAULT 0;

UPDATE so_items
SET cut_qty = qty
WHERE cut = true;

UPDATE items
SET code = 'SUR-MSK-40',
    name = 'موسكي روماني درجة أولى',
    thickness = '4 سم',
    min_qty = 0
WHERE code = 'SUR-MSK'
  AND NOT EXISTS (SELECT 1 FROM items WHERE code = 'SUR-MSK-40');
