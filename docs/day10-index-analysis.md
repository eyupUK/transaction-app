# Day 10 — PostgreSQL Index Performance Analysis

## 1. Objective

Evaluate how covering and partial B-tree indexes affect PostgreSQL query performance in a financial transaction platform.

The investigation compares execution plans, query execution times, buffer accesses, index size and row-filtering behaviour.

## 2. Environment

- **PostgreSQL:** 17.11
- **Database:** `transaction_db`
- **Total transfers:** 50,001, inferred from the sequential scan
- **Transactions for test account 42:** 100
- **High-value pending transactions for account 42:** 11
- **Measurement tool:** `EXPLAIN (ANALYZE, BUFFERS)`

### Reproduce the experiments

Start the local PostgreSQL container, create the schema and load the sample rows using the [README setup steps](../README.md#postgresql-exercises). From the repository root, load the performance dataset and run the [EXPLAIN script](../db/day10-explain-queries.sql):

```bash
docker compose exec -T db \
  psql -X -U transaction_user -d transaction_db \
  -v ON_ERROR_STOP=1 < db/day9-performance-data.sql

docker compose exec -T db \
  psql -X -a -U transaction_user -d transaction_db \
  -v ON_ERROR_STOP=1 < db/day10-explain-queries.sql
```

Use a local development database with no additional transfer indexes beyond those defined by the schema and Day 10 scripts. The script drops and recreates both Day 10 indexes so it can be rerun. It measures account history before and after creating the covering index, then high-value pending transfers before and after creating the partial index, with the covering index retained for the partial-index baseline. It also reports index sizes.

Run with autocommit because the script uses `VACUUM (ANALYZE)` to refresh statistics and the visibility map. Execution times, buffer counts, index sizes and planner choices can vary with the PostgreSQL version, cache state and local data; the tables below retain the original measured results.

GitHub Actions currently runs `mvn -B -ntp clean verify` to verify the Java build and JUnit tests. These SQL experiments are run manually and are not covered by CI. PostgreSQL-backed integration tests are future work.

## 3. Covering Index Experiment

**Query:** Retrieve the 20 most recent outgoing transfers for a specific account.

**Index:**

`idx_transfers_history_covering`

Key columns:

`source_account_id, created_at DESC, id DESC`

Included columns:

`amount, currency, status`

| Metric | Baseline | With covering index |
|---|---|---|
| Access method | Sequential Scan | Index Only Scan |
| Sort | Top-N heapsort | Not required |
| Rows removed by filter | 49,901 | 0 |
| Buffer hits | 667 | 1 |
| Buffer reads | 0 | 3 |
| Heap fetches | N/A | 0 |
| Execution time | 4.417 ms | 0.187 ms |

**Analysis**

Without the covering index, PostgreSQL examined 50,001 rows, identified 100 matching account transfers and performed a top-N heapsort to return the latest 20.

With the covering index, PostgreSQL retrieved the required rows in the requested order, without a separate sorting operation. All requested columns were available from the index, and no heap tuple fetches were necessary.

The indexed query executed approximately 23.6 times faster in the measured runs.

This result demonstrates the benefit of matching index structure to filtering, ordering and projection requirements.

## 4. Partial Index Experiment

**Query:** Retrieve recent pending transfers of £800 or more for a specific account.

**Index:**

`idx_transfers_pending_high_value`

Key columns:

`source_account_id, created_at DESC, id DESC`

Included columns:

`amount, currency`

Predicate:

`status = 'PENDING' AND amount >= 800`

| Metric | Baseline | With partial index |
|---|---|---|
| Access method | Covering Index Only Scan | Partial Index Only Scan |
| Matching rows | 11 | 11 |
| Rows removed by filter | 89 | 0 |
| Buffer hits | 4 | 3 |
| Buffer reads | 1 | 0 |
| Heap fetches | 0 | 0 |
| Execution time | 0.158 ms | 0.071 ms |

**Analysis**

Without the partial index, PostgreSQL selected the general covering index, retrieved account-specific index entries and applied the status and amount filters. It rejected 89 entries and returned 11.

With the partial index, qualifying rows had already been selected by the index predicate. PostgreSQL returned the 11 matching transactions without additional row filtering.

The specialised index was approximately 2.2 times faster in the observed runs.

## 5. Index Storage Comparison

| Index | Observed size |
|---|---:|
| `transfers_pkey` | 2,024 kB |
| `idx_transfers_history_covering` | 4,216 kB |
| `idx_transfers_pending_high_value` | 600 kB |

The covering index is relatively large because it contains several key and included columns for general account-history queries.

The partial index is smaller because it stores only high-value pending transfers matching its predicate.

Index size must be balanced against read performance, storage cost and maintenance overhead.

## 6. Query Planner Accuracy

For the account-history query, PostgreSQL estimated approximately 100 matching account transfers. The sequential scan actually found 100.

For the high-value pending query, PostgreSQL estimated 11 matching records and returned 11.

These results suggest that the planner's cardinality estimates were accurate for the tested predicates.

## 7. Production Considerations

Indexes increase database storage requirements and create maintenance overhead for data modifications.

The covering index is a reasonable candidate if recent account-history queries are frequent and performance-sensitive.

The partial index may be justified if high-value pending-transfer reviews are an important and frequently executed workload.

Before retaining both in production, I would measure query frequency, repeated execution latency, buffer usage and index-maintenance costs.

The observed execution times are from individual local runs, so further controlled benchmarking would be necessary before making production performance claims.

## 8. Conclusion

This investigation demonstrates that:

- Composite indexes can support selective filtering and ordered retrieval.
- Covering indexes may allow index-only scans without heap tuple fetches.
- Partial indexes can reduce unnecessary filtering for specialised workloads.
- PostgreSQL's planner can choose different execution strategies based on index availability and query predicates.
- Index usefulness should be established through actual execution-plan evidence, not assumptions.

**Overall finding:** Both experimental indexes improved their targeted queries in the recorded runs, but their production value depends on workload frequency and write-maintenance tradeoffs.
