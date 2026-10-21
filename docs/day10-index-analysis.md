# Day 10 — Advanced PostgreSQL Indexing Report

## Environment

- PostgreSQL version:
- Total transfer records:
- Synthetic data loaded:
- Existing indexes:

## 1. Covering Index Experiment

**Query:** Retrieve the 20 most recent outgoing transfers.

**Index:** `idx_transfers_history_covering`

| Measurement               | Observed value |
|---------------------------|----------------|
| Scan type                 |                |
| Execution time            |                |
| Shared buffer hits        |                |
| Shared buffer reads       |                |
| Heap fetches, if reported |                |

**Analysis:**

[Explain whether the covering index was selected and whether heap access was avoided.]

## 2. Partial Index Experiment

**Query:** Find pending high-value transfers for a particular account.

**Index:** `idx_transfers_pending_high_value`

| Measurement         | Before index | After index |
|---------------------|--------------|-------------|
| Scan type           |              |             |
| Execution time      |              |             |
| Shared buffer hits  |              |             |
| Shared buffer reads |              |             |

**Analysis:**

[Explain whether the partial index improved the query and why.]

## 3. Index Storage Comparison

| Index                    | Size | Intended workload |
|--------------------------|------|-------------------|
| Primary-key index        |      |                   |
| Original composite index |      |                   |
| Covering index           |      |                   |
| Partial index            |      |                   |

## 4. Query Planner Statistics

[Record any important differences between estimated and actual row counts.]

[Explain whether refreshing statistics affected the execution plan.]

## 5. Tradeoffs and Recommendation

[Which indexes should remain in a production-like transaction platform?]

[Which indexes may be redundant or too expensive to maintain?]

## 6. Conclusion

[Explain what you learned about index selection, covering indexes, partial indexes and evidence-based database optimisation.]