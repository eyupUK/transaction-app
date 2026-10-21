
-- Local development only.
-- Reset our dedicated Day 11 test fixtures.

BEGIN;

DELETE FROM ledger_entries
WHERE transfer_id =
      '21000000-0000-0000-0000-000000000001';

DELETE FROM transfers
WHERE id =
      '21000000-0000-0000-0000-000000000001';

INSERT INTO accounts (
    id, owner_name, currency, balance, version
)
VALUES
    (
        '11000000-0000-0000-0000-000000000001',
        'Day11 Alice', 'GBP', 100.00, 0
    ),
    (
        '11000000-0000-0000-0000-000000000002',
        'Day11 Bob', 'GBP', 200.00, 0
    )
    ON CONFLICT (id) DO UPDATE
                            SET
                                owner_name = EXCLUDED.owner_name,
                            currency = EXCLUDED.currency,
                            balance = EXCLUDED.balance,
                            version = EXCLUDED.version;

COMMIT;
