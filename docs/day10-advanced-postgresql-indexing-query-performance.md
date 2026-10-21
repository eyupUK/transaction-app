The original index contains three key columns:

source_account_id, created_at, id.

However, our query also requires:

amount, currency, status.

A covering index can store those additional values using **INCLUDE**.

Create the covering index:
```sql
CREATE INDEX IF NOT EXISTS
idx_transfers_history_covering
ON transfers (
source_account_id,
created_at DESC,
id DESC
)
INCLUDE (
amount,
currency,
status
);
```

### Find recent pending transfers of £800 or more, for a particular account, so they can be reviewed:
```sql
CREATE INDEX ...
ON transfers (...)
WHERE status = 'PENDING';
```

Run:
```sql
SELECT
    status,
    COUNT(*) AS transactions
FROM transfers
GROUP BY status
ORDER BY status;
```
Examine:
```sql
SELECT
    COUNT(*) AS high_value_pending
FROM transfers
WHERE status = 'PENDING'
  AND amount >= 800;
```
Baseline query
Run this before creating the partial index:
```sql

EXPLAIN (ANALYZE, BUFFERS)
SELECT
    id,
    amount,
    currency,
    created_at
FROM transfers
WHERE source_account_id =
    md5('day9-account-42')::uuid
  AND status = 'PENDING'
  AND amount >= 800
ORDER BY created_at DESC, id DESC
LIMIT 20;
```


Create the partial index:
```sql

CREATE INDEX IF NOT EXISTS
    idx_transfers_pending_high_value
ON transfers (
    source_account_id,
    created_at DESC,
    id DESC
)
INCLUDE (amount, currency)
WHERE status = 'PENDING'
  AND amount >= 800;
```

```text
This index combines:
Feature	            Purpose
-------             -------
source_account_id	Find a particular account
created_at DESC	    Return newest records first
id DESC	            Deterministic ordering
INCLUDE	            Supply extra requested columns
Partial WHERE	    Exclude transactions outside the high-value pending subset
```

Compare the plans:
```sql
ANALYZE transfers;
```
Then rerun the exact baseline query.


### Measure index storage costs
Indexes consume physical space.
Run:
```sql
SELECT
    indexrelname AS index_name,
    idx_scan AS index_scans,
    pg_size_pretty(
        pg_relation_size(indexrelid)
    ) AS index_size
FROM pg_stat_user_indexes
WHERE relname = 'transfers'
ORDER BY pg_relation_size(indexrelid) DESC;
```
Result like:
```text
            index_name            | index_scans | index_size 
----------------------------------+-------------+------------
 transfers_pkey                   |           6 | 16 kB
 idx_transfers_pending_high_value |           0 | 8192 bytes
(2 rows)
```

This shows index sizes and cumulative index-scan statistics.
idx_scan isn't the number of rows returned. It records index-scan usage and is affected by PostgreSQL's statistics collection and reset history.

###### What to investigate

Compare the sizes of:
- The transfer primary-key index
- Your original Day 9 composite index, if present
- The new covering index
- The new partial index

######   What would you expect?

  A covering index containing additional payload columns will generally be larger than an otherwise equivalent index with only the key columns.
  A partial index can be smaller because it includes fewer rows.
  However, the exact result depends on the number of qualifying rows, column types and physical storage.
  ######   Why too many indexes are a problem
  Every index creates additional maintenance work for relevant insertions, updates and deletions.
  For a financial system processing large numbers of transfers, this could affect transaction-processing throughput.

######   Engineering decision: 
Don't keep multiple overlapping indexes unless they support demonstrably different requirements and justify their maintenance costs.
  In your final report, recommend which indexes you would retain rather than automatically keeping all experimental indexes.


##### Understanding incorrect planner estimates
One of the most important things to inspect in an execution plan is the difference between estimated rows and actual rows.
Consider this hypothetical plan:
```text
Seq Scan on transfers
  (cost=0.00..900.00 rows=100 width=...)
  (actual time=0.050..12.000 rows=15000 loops=1)
```
PostgreSQL estimated 100 rows but actually returned 15,000.
The planner has significantly underestimated the query's cardinality.
Such mistakes can influence join choices, scan methods, memory allocation and sorting strategies.

###### Investigate PostgreSQL statistics
Run:
```sql
SELECT
    attname,
    null_frac,
    n_distinct,
    correlation
FROM pg_stats
WHERE schemaname = 'public'
  AND tablename = 'transfers'
  AND attname IN (
      'source_account_id',
      'status',
      'currency',
      'amount'
  );
```

These statistics help PostgreSQL estimate selectivity and costs.
For example, n_distinct estimates the number of distinct values, while correlation describes the statistical relationship between a column's logical ordering and the physical row order.
PostgreSQL also supports extended statistics for relationships between multiple columns when ordinary per-column statistics are insufficient.


###### What to learn

If a query is slow and estimated versus actual row counts differ substantially, don't immediately create another index.
Investigate whether the statistics are outdated, whether predicates are correlated, and whether the plan reflects the actual data distribution.

###### Try:
```sql
ANALYZE transfers;
```
Then rerun the query and observe whether the estimates change.
ANALYZE may improve estimates, but it isn't guaranteed to produce a different or faster plan.


