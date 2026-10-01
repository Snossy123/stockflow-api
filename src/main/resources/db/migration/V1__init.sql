CREATE TABLE roles (
    id bigserial PRIMARY KEY,
    code varchar(32) NOT NULL UNIQUE,
    name varchar(64) NOT NULL
);

CREATE TABLE permissions (
    id bigserial PRIMARY KEY,
    role_id bigint NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    resource varchar(64) NOT NULL,
    action varchar(32) NOT NULL,
    UNIQUE (role_id, resource, action)
);

CREATE TABLE users (
    id bigserial PRIMARY KEY,
    name varchar(120) NOT NULL,
    username varchar(64) NOT NULL UNIQUE,
    password_hash varchar(255) NOT NULL,
    role_id bigint NOT NULL REFERENCES roles (id),
    phone varchar(32),
    status varchar(16) NOT NULL DEFAULT 'active',
    last_login_at timestamptz
);

CREATE TABLE categories (
    id bigserial PRIMARY KEY,
    name varchar(80) NOT NULL UNIQUE
);

CREATE TABLE units (
    id bigserial PRIMARY KEY,
    name varchar(40) NOT NULL UNIQUE
);

CREATE TABLE items (
    id bigserial PRIMARY KEY,
    code varchar(32) NOT NULL UNIQUE,
    name varchar(160) NOT NULL,
    category_id bigint NOT NULL REFERENCES categories (id),
    thickness varchar(32),
    unit_id bigint NOT NULL REFERENCES units (id),
    min_qty numeric(12, 3) NOT NULL DEFAULT 0
);

CREATE TABLE expense_types (
    id bigserial PRIMARY KEY,
    name varchar(80) NOT NULL UNIQUE,
    affects_stock boolean NOT NULL DEFAULT false
);

CREATE TABLE suppliers (
    id bigserial PRIMARY KEY,
    name varchar(160) NOT NULL,
    phone varchar(32)
);

CREATE TABLE customers (
    id bigserial PRIMARY KEY,
    name varchar(160) NOT NULL,
    phone varchar(32)
);

CREATE TABLE purchase_orders (
    id bigserial PRIMARY KEY,
    no varchar(32) NOT NULL UNIQUE,
    supplier_id bigint NOT NULL REFERENCES suppliers (id),
    doc_date date NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'draft',
    paid_now numeric(12, 2) NOT NULL DEFAULT 0,
    total numeric(12, 2) NOT NULL DEFAULT 0,
    created_by bigint REFERENCES users (id),
    approved_by bigint REFERENCES users (id),
    reversed_of_id bigint REFERENCES purchase_orders (id),
    created_at timestamptz NOT NULL DEFAULT now(),
    approved_at timestamptz
);

CREATE TABLE po_items (
    id bigserial PRIMARY KEY,
    po_id bigint NOT NULL REFERENCES purchase_orders (id) ON DELETE CASCADE,
    item_id bigint NOT NULL REFERENCES items (id),
    qty numeric(12, 3) NOT NULL,
    unit_cost numeric(12, 2) NOT NULL,
    total numeric(12, 2) NOT NULL
);

CREATE TABLE sales_orders (
    id bigserial PRIMARY KEY,
    no varchar(32) NOT NULL UNIQUE,
    customer_id bigint NOT NULL REFERENCES customers (id),
    doc_date date NOT NULL,
    kind varchar(16) NOT NULL DEFAULT 'sale',
    pricing varchar(16) NOT NULL DEFAULT 'avg',
    status varchar(16) NOT NULL DEFAULT 'draft',
    paid_now numeric(12, 2) NOT NULL DEFAULT 0,
    total numeric(12, 2) NOT NULL DEFAULT 0,
    cogs numeric(12, 2) NOT NULL DEFAULT 0,
    created_by bigint REFERENCES users (id),
    approved_by bigint REFERENCES users (id),
    reversed_of_id bigint REFERENCES sales_orders (id),
    created_at timestamptz NOT NULL DEFAULT now(),
    approved_at timestamptz
);

CREATE TABLE so_items (
    id bigserial PRIMARY KEY,
    so_id bigint NOT NULL REFERENCES sales_orders (id) ON DELETE CASCADE,
    item_id bigint NOT NULL REFERENCES items (id),
    length_desc varchar(80),
    board_length numeric(12, 3),
    cut_length numeric(12, 3),
    qty numeric(12, 3) NOT NULL,
    unit_price numeric(12, 2) NOT NULL,
    unit_cost numeric(12, 2) NOT NULL DEFAULT 0,
    total numeric(12, 2) NOT NULL,
    surplus_generated boolean NOT NULL DEFAULT false
);

CREATE TABLE payments (
    id bigserial PRIMARY KEY,
    party_type varchar(16) NOT NULL,
    party_id bigint NOT NULL,
    occurred_at timestamptz NOT NULL DEFAULT now(),
    amount numeric(12, 2) NOT NULL,
    method varchar(16) NOT NULL DEFAULT 'cash',
    ref varchar(80),
    created_by bigint REFERENCES users (id)
);

CREATE TABLE expenses (
    id bigserial PRIMARY KEY,
    type_id bigint NOT NULL REFERENCES expense_types (id),
    amount numeric(12, 2) NOT NULL,
    note varchar(255),
    occurred_at timestamptz NOT NULL DEFAULT now(),
    item_id bigint REFERENCES items (id),
    qty numeric(12, 3),
    stock_count_id bigint,
    created_by bigint REFERENCES users (id)
);

CREATE TABLE stock_movements (
    id bigserial PRIMARY KEY,
    item_id bigint NOT NULL REFERENCES items (id),
    kind varchar(24) NOT NULL,
    qty_in numeric(12, 3) NOT NULL DEFAULT 0,
    qty_out numeric(12, 3) NOT NULL DEFAULT 0,
    ref_type varchar(32) NOT NULL,
    ref_id bigint NOT NULL,
    unit_cost numeric(12, 2) NOT NULL DEFAULT 0,
    occurred_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE stock_counts (
    id bigserial PRIMARY KEY,
    no varchar(32) NOT NULL UNIQUE,
    status varchar(16) NOT NULL DEFAULT 'draft',
    started_at timestamptz NOT NULL DEFAULT now(),
    approved_at timestamptz,
    created_by bigint REFERENCES users (id),
    approved_by bigint REFERENCES users (id)
);

CREATE TABLE count_lines (
    id bigserial PRIMARY KEY,
    count_id bigint NOT NULL REFERENCES stock_counts (id) ON DELETE CASCADE,
    item_id bigint NOT NULL REFERENCES items (id),
    book_qty numeric(12, 3) NOT NULL,
    actual_qty numeric(12, 3) NOT NULL,
    reason varchar(160)
);

ALTER TABLE expenses
    ADD CONSTRAINT expenses_stock_count_fk
    FOREIGN KEY (stock_count_id) REFERENCES stock_counts (id);

CREATE TABLE settings (
    key varchar(64) PRIMARY KEY,
    value varchar(255) NOT NULL
);

CREATE TABLE audit_log (
    id bigserial PRIMARY KEY,
    user_id bigint REFERENCES users (id),
    action varchar(64) NOT NULL,
    entity varchar(64) NOT NULL,
    entity_id bigint,
    before_json text,
    after_json text,
    occurred_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE doc_sequences (
    name varchar(16) PRIMARY KEY,
    last_value bigint NOT NULL
);

CREATE TABLE login_events (
    id bigserial PRIMARY KEY,
    user_id bigint REFERENCES users (id),
    username varchar(64) NOT NULL,
    success boolean NOT NULL,
    occurred_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_stock_movements_item ON stock_movements (item_id);
CREATE INDEX idx_stock_movements_occurred ON stock_movements (occurred_at);
CREATE INDEX idx_payments_party ON payments (party_type, party_id);
CREATE INDEX idx_po_supplier ON purchase_orders (supplier_id);
CREATE INDEX idx_so_customer ON sales_orders (customer_id);
