INSERT INTO roles (id, code, name) VALUES
    (1, 'OWNER', 'صاحب المحل'),
    (2, 'ACCOUNTANT', 'محاسب'),
    (3, 'STOREKEEPER', 'أمين مخزن'),
    (4, 'SELLER', 'بياع');
SELECT setval('roles_id_seq', 4);

INSERT INTO permissions (role_id, resource, action) VALUES
    (1, 'dashboard', 'view'), (1, 'dashboard', 'create'),
    (1, 'expenses', 'view'), (1, 'expenses', 'create'),
    (1, 'purchases', 'view'), (1, 'purchases', 'create'), (1, 'purchases', 'approve'),
    (1, 'suppliers', 'view'), (1, 'suppliers', 'create'),
    (1, 'inventory', 'view'), (1, 'inventory', 'create'),
    (1, 'counts', 'view'), (1, 'counts', 'create'), (1, 'counts', 'approve'),
    (1, 'sales', 'view'), (1, 'sales', 'create'), (1, 'sales', 'approve'),
    (1, 'customers', 'view'), (1, 'customers', 'create'),
    (1, 'reports', 'view'),
    (1, 'settings', 'view'), (1, 'settings', 'create'),
    (1, 'users', 'view'), (1, 'users', 'create'),
    (2, 'dashboard', 'view'), (2, 'dashboard', 'create'),
    (2, 'expenses', 'view'), (2, 'expenses', 'create'),
    (2, 'purchases', 'view'), (2, 'purchases', 'create'), (2, 'purchases', 'approve'),
    (2, 'suppliers', 'view'), (2, 'suppliers', 'create'),
    (2, 'inventory', 'view'),
    (2, 'counts', 'view'),
    (2, 'sales', 'view'), (2, 'sales', 'create'), (2, 'sales', 'approve'),
    (2, 'customers', 'view'), (2, 'customers', 'create'),
    (2, 'reports', 'view'),
    (2, 'settings', 'view'),
    (3, 'dashboard', 'view'),
    (3, 'purchases', 'view'), (3, 'purchases', 'create'),
    (3, 'suppliers', 'view'),
    (3, 'inventory', 'view'), (3, 'inventory', 'create'),
    (3, 'counts', 'view'), (3, 'counts', 'create'), (3, 'counts', 'approve'),
    (3, 'settings', 'view'),
    (4, 'dashboard', 'view'),
    (4, 'inventory', 'view'),
    (4, 'sales', 'view'), (4, 'sales', 'create'),
    (4, 'customers', 'view');

INSERT INTO users (name, username, password_hash, role_id, phone, status) VALUES
    ('محمد عرفة', 'arafa.admin', 'arafa123', 1, '01000000001', 'active'),
    ('سارة محسب', 'accountant', 'arafa123', 2, '01000000002', 'active'),
    ('حسن المخزن', 'storekeeper', 'arafa123', 3, '01000000003', 'active'),
    ('علي البياع', 'seller', 'arafa123', 4, '01000000004', 'active');

INSERT INTO categories (name) VALUES
    ('خشب طبيعي'),
    ('ألواح مصنعة'),
    ('فائض');

INSERT INTO units (name) VALUES
    ('لوح'),
    ('قطعة'),
    ('متر طولي');

INSERT INTO items (code, name, category_id, thickness, unit_id, min_qty) VALUES
    ('MSK-40', 'موسكي روماني درجة أولى', 1, '4 سم', 1, 20),
    ('MSK-25', 'موسكي درجة ثانية', 1, '2.5 سم', 1, 15),
    ('LAT-20', 'لاتيه سويد', 1, '2 سم', 1, 20),
    ('ZAN-25', 'زان روماني', 1, '2.5 سم', 1, 8),
    ('MDF-18', 'MDF خام', 2, '18 مم', 1, 12),
    ('PLY-18', 'أبلاكاش تجاري', 2, '18 مم', 1, 10),
    ('SUR-MSK', 'بواقي موسكي', 3, 'فائض', 2, 5);

INSERT INTO expense_types (name, affects_stock) VALUES
    ('إيجار', false),
    ('كهربا', false),
    ('نقل', false),
    ('صيانة', false),
    ('هالك', true);

INSERT INTO suppliers (name, phone) VALUES
    ('شركة النيل للأخشاب', '01011112222'),
    ('مستورد الروماني', '01033334444');

INSERT INTO customers (name, phone) VALUES
    ('ورشة الحرفي', '01211112222'),
    ('مطابخ الأمانة', '01233334444'),
    ('نجارة عبد الله', '01255556666'),
    ('عميل نقدي', NULL);

INSERT INTO settings (key, value) VALUES
    ('default_pricing', 'avg'),
    ('default_min_qty', '10'),
    ('surplus_min_length', '0.30'),
    ('surplus_cost', '0'),
    ('surplus_item_code', 'SUR-MSK');

INSERT INTO doc_sequences (name, last_value) VALUES
    ('PO', 0),
    ('SO', 0),
    ('CNT', 0),
    ('EXP', 0);
