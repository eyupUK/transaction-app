EXPLAIN
This shows PostgreSQL's estimated execution plan without executing the query.

```sql
EXPLAIN
SELECT *
FROM transfers
WHERE currency = 'GBP';
```

EXPLAIN ANALYZE
This executes the query and shows what actually happened.

```sql
EXPLAIN
(ANALYZE, BUFFERS)
SELECT *
FROM transfers
WHERE currency = 'GBP';
```

The most useful measurements are:

```text
| Measurement	        |   Meaning |
|-----------------------|-----------|
| cost=...	            | Estimated planner cost, not milliseconds |
| rows=...	            | Estimated output rows |
| actual ... rows=...	    | Rows observed during execution |
| loops=...	            | Number of times that plan node executed |
| Rows Removed by Filter	| Rows scanned but rejected |
| Buffers: shared hit	    | Buffer accesses satisfied from PostgreSQL shared buffers |
| Buffers: shared read	| Buffer accesses requiring pages not already in shared buffers |
| Planning Time	        | Time spent generating the execution plan |
| Execution Time	        | Time spent executing the plan |
```

For nodes executed repeatedly, reported actual row counts and timings are generally per-execution averages, so loops
matters.
Important: EXPLAIN ANALYZE really executes statements. For UPDATE or DELETE, that means data may change. Use a
transaction and ROLLBACK when investigating data-modifying statements.