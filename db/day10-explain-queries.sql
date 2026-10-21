-- Day 10: Reproduce the covering- and partial-index comparisons.
-- LOCAL DEVELOPMENT only: drops and recreates the two Day 10 indexes.
-- Load schema.sql, seed.sql and day9-performance-data.sql first.
-- Use autocommit: VACUUM cannot run inside a transaction block.
-- Other indexes on transfers can change the baseline plans.

DROP INDEX IF EXISTS idx_transfers_pending_high_value;
DROP INDEX IF EXISTS idx_transfers_history_covering;

-- Refresh statistics and the visibility map for the index-only scans.
VACUUM (ANALYZE) transfers;

-- 1. Account-history baseline: neither Day 10 index is present.
EXPLAIN (ANALYZE, BUFFERS)
SELECT
    id,
    amount,
    currency,
    status,
    created_at
FROM transfers
WHERE source_account_id = md5('day9-account-42')::uuid
ORDER BY created_at DESC, id DESC
LIMIT 20;

-- Same definition as day10-covering-index.sql.
CREATE INDEX IF NOT EXISTS idx_transfers_history_covering
    ON transfers (
        source_account_id,
        created_at DESC,
        id DESC
    )
    INCLUDE (amount, currency, status);

-- 2. Account history with the covering index: rerun the identical query.
EXPLAIN (ANALYZE, BUFFERS)
SELECT
    id,
    amount,
    currency,
    status,
    created_at
FROM transfers
WHERE source_account_id = md5('day9-account-42')::uuid
ORDER BY created_at DESC, id DESC
LIMIT 20;

-- 3. High-value pending baseline: covering index present, partial index absent.
EXPLAIN (ANALYZE, BUFFERS)
SELECT
    id,
    amount,
    currency,
    created_at
FROM transfers
WHERE source_account_id = md5('day9-account-42')::uuid
  AND status = 'PENDING'
  AND amount >= 800
ORDER BY created_at DESC, id DESC
LIMIT 20;

-- Same definition as day10-partial-index.sql.
CREATE INDEX IF NOT EXISTS idx_transfers_pending_high_value
    ON transfers (
        source_account_id,
        created_at DESC,
        id DESC
    )
    INCLUDE (amount, currency)
    WHERE status = 'PENDING'
      AND amount >= 800;

ANALYZE transfers;

-- 4. High-value pending with the partial index: rerun the identical query.
EXPLAIN (ANALYZE, BUFFERS)
SELECT
    id,
    amount,
    currency,
    created_at
FROM transfers
WHERE source_account_id = md5('day9-account-42')::uuid
  AND status = 'PENDING'
  AND amount >= 800
ORDER BY created_at DESC, id DESC
LIMIT 20;

-- Compare storage after both experimental indexes have been created.
SELECT
    indexrelname AS index_name,
    pg_size_pretty(pg_relation_size(indexrelid)) AS index_size
FROM pg_stat_user_indexes
WHERE schemaname = 'public'
  AND relname = 'transfers'
ORDER BY pg_relation_size(indexrelid) DESC;
