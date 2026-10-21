
-- Day 9: Synthetic PostgreSQL performance dataset
-- For LOCAL DEVELOPMENT only.
-- Safe to rerun: deterministic UUIDs prevent duplicate IDs.

BEGIN;

-- Create 500 additional GBP accounts.
INSERT INTO accounts (
    id,
    owner_name,
    currency,
    balance
)
SELECT
    md5('day9-account-' || n)::uuid,
    'Performance Account ' || n,
    'GBP',
    10000.00
FROM generate_series(1, 500) AS gs(n)
    ON CONFLICT (id) DO NOTHING;

-- Create 50,000 synthetic transfers.
INSERT INTO transfers (
    id,
    source_account_id,
    destination_account_id,
    amount,
    currency,
    status,
    created_at
)
SELECT
    md5('day9-transfer-' || n)::uuid,
    md5('day9-account-' || ((n % 500) + 1))::uuid,
    md5('day9-account-' || (((n + 1) % 500) + 1))::uuid,
    ((n % 900) + 1)::NUMERIC(19,2),
    'GBP',
    'PENDING',
    TIMESTAMPTZ '2026-01-01 00:00:00+00'
        + n * INTERVAL '1 minute'
FROM generate_series(1, 50000) AS gs(n)
ON CONFLICT (id) DO NOTHING;

COMMIT;

ANALYZE accounts;
ANALYZE transfers;
