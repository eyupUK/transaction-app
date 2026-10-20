## Design summary

### Relationships and data types

- An account can be the source or destination of many transfers. Each transfer references both accounts, and composite foreign keys on account ID and currency prevent cross-currency transfers.
- A transfer can have ledger entries, and each ledger entry references one transfer and one account. `UNIQUE (transfer_id, entry_type)` allows at most one debit and one credit per transfer.
- `UUID` gives accounts, transfers, and ledger entries independent identifiers. `NUMERIC(19,2)` stores money as exact decimal values at a cents scale; bounded `VARCHAR` columns limit names and codes, with checks restricting currency, status, and entry type. `BIGINT` provides room for a nonnegative account revision counter, and `TIMESTAMPTZ` avoids timezone-ambiguous creation times.

### Constraint test results

Eleven invalid inserts were tested against PostgreSQL inside a transaction that was rolled back. All were rejected as expected: unsupported account currency and ledger entry type, negative balance and version, non-positive transfer and ledger amounts, invalid status, self-transfer, missing and currency-mismatched transfer accounts, and a duplicate ledger entry type. Check constraints returned SQLSTATE `23514`, foreign keys `23503`, and the duplicate unique key `23505`.

### Integrity rules not enforced

The schema does not require a transfer to have both a debit and a credit, or require their amounts to balance or equal the transfer amount. It also does not ensure a ledger entry's account is the transfer's source or destination, or that the ledger account currency matches the transfer currency. Transfer status is not tied to ledger creation, and account balances are not reconciled against ledger entries; these rules need application logic or additional database mechanisms.

## Connect to PostgreSQL

```bash
docker compose up -d
docker compose exec db \
  psql -U transaction_user -d transaction_db
```

## Create the tables

Run the SQL file from your terminal:

```bash
docker compose exec -T db \
  psql -U transaction_user -d transaction_db \
  -v ON_ERROR_STOP=1 < db/schema.sql
```

Then connect:

```bash
docker compose exec db \
  psql -U transaction_user -d transaction_db
```

Use PostgreSQL's metadata commands:

```sql
\dt
\d accounts
\d transfers
\d ledger_entries
```

## Seed the database

```bash
docker compose exec -T db \
  psql -U transaction_user -d transaction_db \
  -v ON_ERROR_STOP=1 < db/seed.sql
```

Verify the database contains the seed data:

```sql
SELECT * FROM accounts;
SELECT * FROM transfers;
SELECT * FROM ledger_entries;
```

## Query the data

Find all GBP accounts, ordered by balance from highest to lowest:

```sql
SELECT * FROM accounts
WHERE currency = 'GBP'
ORDER BY balance DESC;
```

Join transfers with account owners to show the sender, receiver, amount, currency, and status:

```sql
SELECT
    t.id AS transfer_id,
    sender.owner_name AS sender,
    receiver.owner_name AS receiver,
    t.amount,
    t.currency,
    t.status
FROM transfers t

         INNER JOIN accounts sender
                    ON t.source_account_id = sender.id

         INNER JOIN accounts receiver
                    ON t.destination_account_id = receiver.id;
```

Calculate totals by ledger entry type:

```sql
SELECT
    entry_type,
    SUM(amount) AS total_amount
FROM ledger_entries
GROUP BY entry_type
ORDER BY entry_type;
```

Find transfers whose ledger is missing one or both entries or whose debit and credit totals differ. The seeded transfer returns no rows:

```sql
SELECT
    t.id AS transfer_id,
    COALESCE(SUM(le.amount) FILTER (WHERE le.entry_type = 'DEBIT'), 0) AS debit_total,
    COALESCE(SUM(le.amount) FILTER (WHERE le.entry_type = 'CREDIT'), 0) AS credit_total
FROM transfers t
         LEFT JOIN ledger_entries le ON le.transfer_id = t.id
GROUP BY t.id
HAVING COUNT(le.id) <> 2
    OR COALESCE(SUM(le.amount) FILTER (WHERE le.entry_type = 'DEBIT'), 0)
       <> COALESCE(SUM(le.amount) FILTER (WHERE le.entry_type = 'CREDIT'), 0);
```

CASE WHEN lets you conditionally include values in an aggregate
```sql
SUM(
    CASE
        WHEN entry_type = 'DEBIT'
        THEN amount
        ELSE 0
    END
)
```

Build a ledger summary
Rather than repeat complicated calculations, use a Common Table Expression (CTE).
A CTE starts with WITH and lets you name an intermediate query.
```sql
WITH ledger_summary AS (
    SELECT
        t.id AS transfer_id,
        t.amount AS expected_amount,
        t.currency,

        COUNT(le.id) FILTER (
            WHERE le.entry_type = 'DEBIT'
        ) AS debit_count,

        COUNT(le.id) FILTER (
            WHERE le.entry_type = 'CREDIT'
        ) AS credit_count,

        COALESCE(SUM(le.amount) FILTER (
            WHERE le.entry_type = 'DEBIT'
        ), 0) AS debit_total,

        COALESCE(SUM(le.amount) FILTER (
            WHERE le.entry_type = 'CREDIT'
        ), 0) AS credit_total

    FROM transfers t
    LEFT JOIN ledger_entries le
        ON t.id = le.transfer_id

    WHERE t.status = 'COMPLETED'

    GROUP BY t.id, t.amount, t.currency
)

SELECT *
FROM ledger_summary
WHERE debit_count <> 1
   OR credit_count <> 1
   OR debit_total <> credit_total
   OR debit_total <> expected_amount;
```


Count transfers per currency
Requirement: Group transfers by currency, count them and calculate their total amount.
This combines COUNT(), SUM() and GROUP BY
```sql
SELECT
    currency,
    COUNT(*) AS transfer_count,
    SUM(amount) AS total_amount
FROM transfers
GROUP BY currency
ORDER BY currency;
```

find currencies with more than five transfers:
```sql
SELECT
    currency,
    COUNT(*) AS transfer_count
FROM transfers
GROUP BY currency
HAVING COUNT(*) > 5;
```

Synchronization strategies for concurrent balance updates.
When multiple transactions attempt to update the same account balance, you can use either pessimistic or optimistic locking to prevent race conditions and ensure data integrity.
Pessimistic locking:
```sql
BEGIN;

SELECT balance
FROM accounts
WHERE id = :account_id
FOR UPDATE;

-- Validate available balance.
-- Apply the appropriate balance update.

COMMIT;
```

Optimistic locking:
```sql
UPDATE accounts
SET balance = balance - 80.00,
    version = version + 1
WHERE id = :account_id
  AND version = :expected_version
  AND balance >= 80.00
RETURNING balance;
```

Final backend engineering challenge
Suppose your PostgreSQL database contains:
Account	Balance	Version
A	    £100.00	    5
B	    £200.00	    3
Two backend instances simultaneously attempt to withdraw £80 from Account A.
Both initially read version 5 and a balance of £100.
Explain what happens when both execute this SQL:
```sql
UPDATE accounts
SET balance = balance - 80.00,
version = version + 1
WHERE id = :account_id
AND version = 5
AND balance >= 80.00
RETURNING balance, version;
```
I'd like you to explain which request succeeds, what happens to the other request, and what the final account balance and version should be.

Answer: 
This demonstrates optimistic concurrency control using a version column.
Both instances initially read a balance of £100 and version 5.
Under PostgreSQL's default READ COMMITTED isolation, whichever update obtains the row lock first can update the balance to £20 and increment the version to 6.
The second update waits for the first transaction to finish. After the first commits, PostgreSQL re-evaluates the WHERE condition using the updated row.
Since the version is now 6 and the balance is only £20, the second update matches zero rows.
The application detects that no update occurred and determines whether the reason is a concurrent modification, insufficient funds or a missing account.
Under stronger isolation levels, the competing transaction may instead receive a serialization failure.
This protects the account against lost updates and prevents both £80 withdrawals from succeeding.

Pessimistic Locking:

If you switched to a Pessimistic Locking (FOR UPDATE) strategy, the database would shift the locking mechanism from the write phase to the initial read phase.
Instead of letting both instances read the data simultaneously, PostgreSQL would force the second instance to wait at the very beginning of its transaction.

```sql
BEGIN;
SELECT balance, version FROM accounts WHERE id = :account_id FOR UPDATE;
```


Comparison: Optimistic vs. Pessimistic


Feature	| Optimistic Concurrency (OCC)	| Pessimistic Locking (FOR UPDATE)
When Locking Happens	| During the final write step.	| During the initial read step.
Second Request Behavior	| Runs query immediately; updates 0 rows.	| Blocks and waits for the first request to finish.
How Failure is Caught	| Via technical tracking (version mismatch).	| Via business rules (insufficient balance check).
Performance Profile	| High throughput; works best with low contention.	| Lower throughput; causes thread waiting under heavy load.
Final Database State	| Balance: £20.00, Version: 6	| Balance: £20.00, Version: 6
