
BEGIN;

INSERT INTO accounts
(id, owner_name, currency, balance)
VALUES
    (
        '00000000-0000-0000-0000-000000000001',
        'Alice', 'GBP', 450.00
    ),
    (
        '00000000-0000-0000-0000-000000000002',
        'Bob', 'GBP', 250.00
    ),
    (
        '00000000-0000-0000-0000-000000000003',
        'Clara', 'EUR', 100.00
    )
    ON CONFLICT (id) DO NOTHING;

INSERT INTO transfers (
    id, source_account_id,
    destination_account_id,
    amount, currency, status
)
VALUES (
           '10000000-0000-0000-0000-000000000001',
           '00000000-0000-0000-0000-000000000001',
           '00000000-0000-0000-0000-000000000002',
           50.00, 'GBP', 'COMPLETED'
       )
    ON CONFLICT (id) DO NOTHING;

INSERT INTO ledger_entries (
    id, transfer_id, account_id,
    entry_type, amount
)
VALUES
    (
        '20000000-0000-0000-0000-000000000001',
        '10000000-0000-0000-0000-000000000001',
        '00000000-0000-0000-0000-000000000001',
        'DEBIT', 50.00
    ),
    (
        '20000000-0000-0000-0000-000000000002',
        '10000000-0000-0000-0000-000000000001',
        '00000000-0000-0000-0000-000000000002',
        'CREDIT', 50.00
    )
    ON CONFLICT (id) DO NOTHING;

COMMIT;

