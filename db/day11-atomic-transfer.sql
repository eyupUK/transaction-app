-- Day 11: Transfer £80 from Alice to Bob.
-- Run once after day11-setup.sql.

BEGIN;

DO
$$
DECLARE
v_source UUID :=
        '11000000-0000-0000-0000-000000000001';
    v_destination
UUID :=
        '11000000-0000-0000-0000-000000000002';
    v_transfer_id
UUID :=
        '21000000-0000-0000-0000-000000000001';

    v_amount
NUMERIC(19,2) := 80.00;

    v_source_currency
TEXT;
    v_destination_currency
TEXT;
    v_source_balance
NUMERIC(19,2);

    v_locked_count
INTEGER;
    v_updated_count
INTEGER;
BEGIN
    -- 1. Validate input.
    IF
v_amount <= 0 OR v_source = v_destination THEN
        RAISE EXCEPTION 'Invalid transfer request';
END IF;

    -- 2. Lock both accounts in a consistent order.
    PERFORM
id
    FROM accounts
    WHERE id IN (v_source, v_destination)
    ORDER BY id
    FOR
UPDATE;

GET DIAGNOSTICS v_locked_count = ROW_COUNT;

IF
v_locked_count <> 2 THEN
        RAISE EXCEPTION 'Account not found';
END IF;

    -- 3. Read locked account state.
SELECT currency, balance
INTO v_source_currency, v_source_balance
FROM accounts
WHERE id = v_source;

SELECT currency
INTO v_destination_currency
FROM accounts
WHERE id = v_destination;

IF
v_source_currency <> v_destination_currency THEN
        RAISE EXCEPTION 'Currency mismatch';
END IF;

    IF
v_source_balance < v_amount THEN
        RAISE EXCEPTION 'Insufficient funds';
END IF;

    -- 4. Record the pending transfer.
INSERT INTO transfers (id,
                       source_account_id,
                       destination_account_id,
                       amount,
                       currency,
                       status)
VALUES (v_transfer_id,
        v_source,
        v_destination,
        v_amount,
        v_source_currency,
        'PENDING');

-- 5. Debit the source account.
UPDATE accounts
SET balance = balance - v_amount,
    version = version + 1
WHERE id = v_source
  AND balance >= v_amount;

GET DIAGNOSTICS v_updated_count = ROW_COUNT;

IF
v_updated_count <> 1 THEN
        RAISE EXCEPTION 'Debit failed';
END IF;

    -- 6. Credit the destination account.
UPDATE accounts
SET balance = balance + v_amount,
    version = version + 1
WHERE id = v_destination;

GET DIAGNOSTICS v_updated_count = ROW_COUNT;

IF
v_updated_count <> 1 THEN
        RAISE EXCEPTION 'Credit failed';
END IF;

    -- 7. Record the debit and credit postings.
INSERT INTO ledger_entries (id,
                            transfer_id,
                            account_id,
                            entry_type,
                            amount)
VALUES ('31000000-0000-0000-0000-000000000001',
        v_transfer_id,
        v_source,
        'DEBIT',
        v_amount),
       ('31000000-0000-0000-0000-000000000002',
        v_transfer_id,
        v_destination,
        'CREDIT',
        v_amount);

-- 8. Complete the transfer.
UPDATE transfers
SET status = 'COMPLETED'
WHERE id = v_transfer_id;
END $$;

COMMIT;
