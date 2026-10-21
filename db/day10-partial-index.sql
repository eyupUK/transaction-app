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