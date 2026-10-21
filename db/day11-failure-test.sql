-- Day 11: Roll back a transfer after both balances and ledger entries change.
-- Local development only. Run after day11-setup.sql.
-- Mirrors day11-atomic-transfer.sql through the ledger insert, then aborts.
-- Expected error: P0001, Deliberate failure after ledger insert.
-- Run with psql -v ON_ERROR_STOP=0 so ROLLBACK and the final checks execute.

BEGIN;

DO $$
DECLARE
    v_source UUID := '11000000-0000-0000-0000-000000000001';
    v_destination UUID := '11000000-0000-0000-0000-000000000002';
    v_transfer_id UUID := '21000000-0000-0000-0000-000000000001';
    v_amount NUMERIC(19,2) := 80.00;
    v_source_currency TEXT;
    v_destination_currency TEXT;
    v_source_balance NUMERIC(19,2);
    v_locked_count INTEGER;
    v_updated_count INTEGER;
BEGIN
    IF v_amount <= 0 OR v_source = v_destination THEN
        RAISE EXCEPTION 'Invalid transfer request';
    END IF;

    PERFORM id
    FROM accounts
    WHERE id IN (v_source, v_destination)
    ORDER BY id
    FOR UPDATE;

    GET DIAGNOSTICS v_locked_count = ROW_COUNT;
    IF v_locked_count <> 2 THEN
        RAISE EXCEPTION 'Account not found';
    END IF;

    SELECT currency, balance
    INTO v_source_currency, v_source_balance
    FROM accounts
    WHERE id = v_source;

    SELECT currency
    INTO v_destination_currency
    FROM accounts
    WHERE id = v_destination;

    IF v_source_currency <> v_destination_currency THEN
        RAISE EXCEPTION 'Currency mismatch';
    END IF;

    IF v_source_balance < v_amount THEN
        RAISE EXCEPTION 'Insufficient funds';
    END IF;

    INSERT INTO transfers (
        id, source_account_id, destination_account_id, amount, currency, status
    )
    VALUES (
        v_transfer_id, v_source, v_destination, v_amount, v_source_currency,
        'PENDING'
    );

    UPDATE accounts
    SET balance = balance - v_amount,
        version = version + 1
    WHERE id = v_source
      AND balance >= v_amount;

    GET DIAGNOSTICS v_updated_count = ROW_COUNT;
    IF v_updated_count <> 1 THEN
        RAISE EXCEPTION 'Debit failed';
    END IF;

    UPDATE accounts
    SET balance = balance + v_amount,
        version = version + 1
    WHERE id = v_destination;

    GET DIAGNOSTICS v_updated_count = ROW_COUNT;
    IF v_updated_count <> 1 THEN
        RAISE EXCEPTION 'Credit failed';
    END IF;

    INSERT INTO ledger_entries (
        id, transfer_id, account_id, entry_type, amount
    )
    VALUES
        ('31000000-0000-0000-0000-000000000001',
         v_transfer_id, v_source, 'DEBIT', v_amount),
        ('31000000-0000-0000-0000-000000000002',
         v_transfer_id, v_destination, 'CREDIT', v_amount);

    RAISE NOTICE 'before abort: Alice=%, Bob=%, transfers=%, ledger_entries=%',
        (SELECT balance FROM accounts WHERE id = v_source),
        (SELECT balance FROM accounts WHERE id = v_destination),
        (SELECT COUNT(*) FROM transfers WHERE id = v_transfer_id),
        (SELECT COUNT(*) FROM ledger_entries WHERE transfer_id = v_transfer_id);

    RAISE EXCEPTION 'Deliberate failure after ledger insert';
END $$;

ROLLBACK;

-- Expected: Alice 100.00 / version 0; Bob 200.00 / version 0.
SELECT owner_name, currency, balance, version
FROM accounts
WHERE id IN (
    '11000000-0000-0000-0000-000000000001',
    '11000000-0000-0000-0000-000000000002'
)
ORDER BY id;

-- Expected: 300.00 combined balance, zero transfers and zero ledger entries.
SELECT SUM(balance) AS combined_balance
FROM accounts
WHERE id IN (
    '11000000-0000-0000-0000-000000000001',
    '11000000-0000-0000-0000-000000000002'
);

SELECT COUNT(*) AS transfer_count
FROM transfers
WHERE id = '21000000-0000-0000-0000-000000000001';

SELECT COUNT(*) AS ledger_entry_count
FROM ledger_entries
WHERE transfer_id = '21000000-0000-0000-0000-000000000001';
