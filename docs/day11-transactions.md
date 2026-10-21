# Day 11 — PostgreSQL Transactions, ACID & Isolation Levels

```text
ACID properties and database transaction boundaries.
BEGIN, COMMIT, ROLLBACK and SAVEPOINT.
Atomic transfers involving two accounts, one transfer record and two ledger entries.
Pessimistic row locking with SELECT FOR UPDATE.
PostgreSQL isolation levels and MVCC.
Concurrent transaction behaviour using two psql sessions.
Deadlock prevention and transaction retry principles.
```

```text
A
Atomicity
All changes commit together, or none of them do.

C
Consistency
Transactions must preserve constraints and required business invariants.

I
Isolation
Concurrent transactions must obey the selected isolation guarantees.

D
Durability
Once committed, changes survive a crash under the database's durability guarantees.
```

## Environment and reproduction

Observed on 22 October 2026 using PostgreSQL 17.11 in the local Docker Compose container. The experiments used a temporary database loaded with [the schema](../db/schema.sql) and [the Day 11 fixtures](../db/day11-setup.sql); the existing `transaction_db` was left unchanged.

To reproduce locally, start PostgreSQL and create the schema using the [README setup steps](../README.md#postgresql-exercises), then reset the dedicated fixtures:

```bash
docker compose exec -T db \
  psql -X -U transaction_user -d transaction_db \
  -v ON_ERROR_STOP=1 < db/day11-setup.sql
```

Run the setup before each independent experiment, after closing any open transactions. It resets Alice and Bob and deletes the fixture transfer and its ledger entries. The transfer script uses fixed transfer and ledger IDs, so reset before repeating a successful run.

For concurrent experiments, open two terminals with the following command, calling them sessions A and B. Use `\set VERBOSITY verbose` inside psql to display error SQLSTATEs.

```bash
docker compose exec db psql -X -U transaction_user -d transaction_db
```

These are manually verified SQL experiments. GitHub CI currently verifies the Java build and JUnit tests; PostgreSQL-backed integration tests remain future work.

## Initial balances

The fixture accounts are:

| Account | ID | Currency | Balance | Version |
|---|---|---|---:|---:|
| Day11 Alice | `11000000-0000-0000-0000-000000000001` | GBP | 100.00 | 0 |
| Day11 Bob | `11000000-0000-0000-0000-000000000002` | GBP | 200.00 | 0 |

Their combined balance is **GBP 300.00**. Initially, the fixture transfer `21000000-0000-0000-0000-000000000001` and its ledger entries have counts of **0**.

Use these checks before and after each experiment:

```sql
SELECT owner_name, currency, balance, version
FROM accounts
WHERE id IN (
    '11000000-0000-0000-0000-000000000001',
    '11000000-0000-0000-0000-000000000002'
)
ORDER BY id;

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
```

## Successful transfer

Run [the atomic transfer script](../db/day11-atomic-transfer.sql) once after setup:

```bash
docker compose exec -T db \
  psql -X -U transaction_user -d transaction_db \
  -v ON_ERROR_STOP=1 < db/day11-atomic-transfer.sql
```

The transaction committed a **GBP 80.00 transfer from Alice to Bob**.

| Account | Initial balance | Committed balance | Initial version | Committed version |
|---|---:|---:|---:|---:|
| Day11 Alice | 100.00 | 20.00 | 0 | 1 |
| Day11 Bob | 200.00 | 280.00 | 0 | 1 |

The combined balance remained **GBP 300.00**. The transfer count became **1**, with the expected source and destination accounts, amount `80.00`, currency `GBP` and status `COMPLETED`:

```sql
SELECT id, source_account_id, destination_account_id,
       amount, currency, status
FROM transfers
WHERE id = '21000000-0000-0000-0000-000000000001';
```

Both account updates, the transfer record and both ledger entries committed together. The account versions each increased once.

## Ledger checks

```sql
SELECT a.owner_name, l.entry_type, l.amount
FROM ledger_entries AS l
JOIN accounts AS a ON a.id = l.account_id
WHERE l.transfer_id = '21000000-0000-0000-0000-000000000001'
ORDER BY a.id;
```

| Account | Entry type | Amount |
|---|---|---:|
| Day11 Alice | DEBIT | 80.00 |
| Day11 Bob | CREDIT | 80.00 |

The aggregate check confirmed exactly **two postings**, equal debit and credit totals, and a zero signed net amount:

```sql
SELECT
    COUNT(*) AS postings,
    SUM(amount) FILTER (WHERE entry_type = 'DEBIT') AS debit_total,
    SUM(amount) FILTER (WHERE entry_type = 'CREDIT') AS credit_total,
    SUM(CASE WHEN entry_type = 'CREDIT' THEN amount ELSE -amount END)
        AS net_amount
FROM ledger_entries
WHERE transfer_id = '21000000-0000-0000-0000-000000000001';
```

| Postings | Debit total | Credit total | Net amount |
|---:|---:|---:|---:|
| 2 | 80.00 | 80.00 | 0.00 |

These checks establish the observed posting balance. The schema's constraints enforce valid individual entries and one entry per transfer/type; the transfer transaction supplies the matching debit and credit pair.

## Failed transfers and rollback

Each validation case started from a fresh fixture reset. Temporary copies of the transfer script changed only the indicated input. In interactive psql, run the copy through `END $$;`, omitting the final `COMMIT;`, so the failed transaction can be inspected before `ROLLBACK`.

| Test variation | Observed error | SQLSTATE |
|---|---|---|
| Set `v_amount` to `0.00` or `-1.00` | `Invalid transfer request` | `P0001` |
| Set `v_destination` equal to Alice's ID | `Invalid transfer request` | `P0001` |
| Set `v_destination` to absent ID `11000000-0000-0000-0000-000000000099` | `Account not found` | `P0001` |
| Change Bob's currency to EUR before attempting the GBP transfer | `Currency mismatch` | `P0001` |
| Set `v_amount` to `120.00`, exceeding Alice's GBP 100.00 balance | `Insufficient funds` | `P0001` |

For the currency test, the preparatory statement was committed before attempting the transfer:

```sql
UPDATE accounts
SET currency = 'EUR'
WHERE id = '11000000-0000-0000-0000-000000000002';
```

After each error, `SELECT 1;` returned SQLSTATE **`25P02`**, indicating an aborted transaction. After explicit `ROLLBACK;`, Alice still had **100.00**, Bob **200.00**, both versions were **0**, and the fixture transfer and ledger counts were **0**. In the currency test, Bob retained the separately committed EUR setting until the next fixture reset.

### Failure after writes

To check rollback after the balances and ledger had already changed, a temporary copy inserted the following immediately before step 8, inside the `DO` block:

```sql
RAISE NOTICE 'before abort: Alice=%, Bob=%, transfers=%, ledger_entries=%',
    (SELECT balance FROM accounts WHERE id = v_source),
    (SELECT balance FROM accounts WHERE id = v_destination),
    (SELECT COUNT(*) FROM transfers WHERE id = v_transfer_id),
    (SELECT COUNT(*) FROM ledger_entries WHERE transfer_id = v_transfer_id);

RAISE EXCEPTION 'Deliberate failure after ledger insert';
```

The notice reported **Alice=20.00, Bob=280.00, transfers=1, ledger_entries=2** inside the transaction. The exception then raised `P0001`. After `ROLLBACK`, the state was:

| Check | After rollback |
|---|---:|
| Alice balance / version | 100.00 / 0 |
| Bob balance / version | 200.00 / 0 |
| Combined balance | 300.00 |
| Fixture transfer count | 0 |
| Fixture ledger entry count | 0 |

This verified that the pending transfer, both account updates, version increments and both ledger entries were undone together. No `FAILED` transfer row was retained by this script.

Repeating the unchanged GBP 80.00 script after a successful run raised `Insufficient funds`, because Alice then had GBP 20.00. A separate attempt using GBP 10.00 with the same transfer ID reached the insert and raised unique-violation SQLSTATE **`23505`**. After rollback, both attempts preserved the previously committed balances, versions, single transfer and two ledger entries.

## Two-session lock behaviour

Reset the fixtures, then use the following sequence at `READ COMMITTED`:

1. **Session A:** Run the transfer script through `END $$;`, omitting its final `COMMIT;`. A has applied the transfer inside its open transaction and holds locks on both accounts.
2. **Session B:** Begin a transaction and read Alice normally, then request a row lock:

   ```sql
   BEGIN ISOLATION LEVEL READ COMMITTED;

   SELECT balance, version
   FROM accounts
   WHERE id = '11000000-0000-0000-0000-000000000001';

   SELECT balance, version
   FROM accounts
   WHERE id = '11000000-0000-0000-0000-000000000001'
   FOR UPDATE;
   ```

3. The ordinary `SELECT` returned **100.00 / version 0** immediately. The `FOR UPDATE` statement waited. Monitoring showed `wait_event_type = 'Lock'`, `wait_event = 'transactionid'`, and A in B's `pg_blocking_pids` result.
4. **Session A:** Execute `COMMIT;`. B's waiting `FOR UPDATE` then returned **20.00 / version 1**.
5. **Session B:** Try another guarded GBP 80.00 debit:

   ```sql
   UPDATE accounts
   SET balance = balance - 80.00,
       version = version + 1
   WHERE id = '11000000-0000-0000-0000-000000000001'
     AND balance >= 80.00
   RETURNING balance, version;

   ROLLBACK;
   ```

The guarded update returned **zero rows**. Final committed balances were Alice **20.00**, Bob **280.00**, both versions **1**, with **one completed transfer and two ledger entries**.

The experiment was also repeated with A issuing `ROLLBACK` instead of `COMMIT`. B resumed and returned **100.00 / version 0**. After B rolled back, both accounts retained their initial balances and versions, and transfer and ledger counts were **0**.

Ordinary reads observed committed row versions while the writer held its locks. Competing row-lock requests waited until the transaction ended. The transfer script locks both accounts in ascending `id` order before checking balances, giving transfers in either direction a consistent account-lock order.

## Isolation-level results

The comparison covered snapshot visibility and contention on Alice's row. Reset the fixtures for each level and use this sequence:

1. **Session A:** `BEGIN ISOLATION LEVEL READ COMMITTED;`, replacing the level for each row below. Run the Alice balance/version query to establish the first read before B changes anything.
2. **Session B:** Run the transfer script through `END $$;`, leaving its final `COMMIT` pending.
3. **Session A:** Repeat the ordinary balance/version query while B's changes are uncommitted.
4. **Session B:** `COMMIT;`.
5. **Session A:** Repeat the ordinary query, then attempt the transfer's `DO $$ ... END $$;` block inside A's existing transaction, omitting the script's outer `BEGIN` and `COMMIT`.
6. **Session A:** `ROLLBACK;`, then verify committed state from a fresh transaction.

All ordinary reads used:

```sql
SELECT balance, version
FROM accounts
WHERE id = '11000000-0000-0000-0000-000000000001';
```

| A's isolation level | First read: balance / version | While B is uncommitted | After B commits | A's subsequent GBP 80.00 transfer attempt |
|---|---|---|---|---|
| READ UNCOMMITTED | 100.00 / 0 | 100.00 / 0 | 20.00 / 1 | `P0001`: `Insufficient funds` |
| READ COMMITTED | 100.00 / 0 | 100.00 / 0 | 20.00 / 1 | `P0001`: `Insufficient funds` |
| REPEATABLE READ | 100.00 / 0 | 100.00 / 0 | 100.00 / 0 | `40001`: `could not serialize access due to concurrent update` |
| SERIALIZABLE | 100.00 / 0 | 100.00 / 0 | 100.00 / 0 | `40001`: `could not serialize access due to concurrent update` |

No level exposed B's uncommitted changes. PostgreSQL's `READ UNCOMMITTED` behaved like `READ COMMITTED` in these checks. At those levels, the next statement saw the newly committed balance, and the transfer's funds validation rejected a second GBP 80.00 debit.

At `REPEATABLE READ` and `SERIALIZABLE`, ordinary reads retained the snapshot established by A's first query. The subsequent transfer attempted to lock the row changed by B and raised `40001`. The ordinary reads themselves completed successfully; the conflicting lock attempt caused the error. A serialization failure requires rolling back and retrying the whole transaction with fresh state and business validation.

After A rolled back in every case, the committed result remained Alice **20.00 / version 1**, Bob **280.00 / version 1**, combined balance **GBP 300.00**, **one completed transfer** and **two balanced ledger entries**.
