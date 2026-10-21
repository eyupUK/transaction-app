CREATE INDEX IF NOT EXISTS idx_transfers_history_covering
    ON transfers (
    source_account_id,
    created_at DESC,
    id DESC
    )
    INCLUDE (amount, currency, status);

VACUUM (ANALYZE) transfers;