CREATE TABLE accounting_periods (
    year_month varchar(7) PRIMARY KEY,
    status varchar(16) NOT NULL,
    closed_at timestamptz,
    closed_by bigint REFERENCES users (id),
    reopened_at timestamptz,
    reopened_by bigint REFERENCES users (id)
);

INSERT INTO permissions (role_id, resource, action) VALUES
    (1, 'periods', 'view'),
    (1, 'periods', 'close'),
    (1, 'periods', 'reopen'),
    (2, 'periods', 'view'),
    (2, 'periods', 'close');
