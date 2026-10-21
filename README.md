# Java Transaction Platform

A learning project for exploring financial domain modelling, transaction analytics, application layering, concurrency, JVM memory behaviour, and PostgreSQL schema and query performance.

## Modules

- `transaction-domain` contains the transaction domain model:
  - `Money` is an immutable value object backed by `BigDecimal` and `Currency`. It supports GBP and EUR, normalizes amounts to two decimal places without rounding, rejects unsupported currencies, and provides currency-safe arithmetic and zero-value creation.
  - `Account` models a balance with validation for deposits, withdrawals, and opening balances.
  - `Transaction` represents a transfer with source and destination account IDs, amount, and timestamp. It validates required data and rejects invalid amounts and self-transfers.
  - `TransferPolicy` applies a maximum-transfer rule using whole-pence values.
- `transaction-application` contains application services, repository abstractions and examples:
  - `AccountService` uses `AccountRepository` for balance queries, deposits, and withdrawals.
  - `InMemoryAccountRepository` provides atomic in-memory account updates.
  - `TransactionAnalytics` provides stream-based reporting for outgoing transfers, totals by currency, top transfers, and duplicate transaction IDs.
  - `UnsafeAccountStore` and `SynchronizedAccountProcessor` demonstrate unsynchronized and synchronized state updates.
  - `BoundedTransactionHistory` stores a capacity-limited, synchronized history and returns independent snapshots.
  - `MemoryLab` demonstrates temporary allocation and intentional object retention for JVM diagnostics.
  - `App` is the greeting entry point; `BackendDemo` demonstrates account deposits and withdrawals through the service and repository.

## Prerequisites

- JDK 21 or newer
- Maven 3.9 or newer
- Docker Compose (optional, for the PostgreSQL exercises)

## Build and test

Run the Maven verification lifecycle from the repository root:

```bash
mvn clean verify
```

This builds both modules, runs their JUnit tests, and packages the module JARs.

GitHub Actions runs the same Maven verification. The PostgreSQL experiments are run manually and are not currently covered by CI.

## Run the application examples

After building, run the greeting application:

```bash
java -cp transaction-application/target/classes io.github.eyupuk.transactions.App
```

Expected output:

```text
Java transaction platform: ready
```

Run the account service example:

```bash
java -cp transaction-application/target/classes io.github.eyupuk.transactions.BackendDemo
```

It deposits GBP 25.00 and withdraws GBP 40.00 from an account opened with GBP 100.00, printing the updated and final balances.

## Run the memory lab

The memory lab accepts `temporary` or `retained` as its mode. The temporary mode allocates short-lived arrays; the retained mode keeps references to its arrays for inspection. With no argument, it uses `temporary`.

```bash
java -cp transaction-application/target/classes io.github.eyupuk.transactions.performance.MemoryLab temporary
java -cp transaction-application/target/classes io.github.eyupuk.transactions.performance.MemoryLab retained
```

Each run pauses briefly before exiting so JVM diagnostic tools can attach. See [the JVM performance investigation](docs/day5-jvm-performance.md) for profiling steps.

## PostgreSQL exercises

The Java repository implementation is in-memory; the SQL and Docker Compose files are separate database design and performance exercises. Start the local PostgreSQL 17 container and create the schema with:

```bash
docker compose up -d
docker compose exec -T db \
  psql -U transaction_user -d transaction_db \
  -v ON_ERROR_STOP=1 < db/schema.sql
```

Load the small sample dataset:

```bash
docker compose exec -T db \
  psql -U transaction_user -d transaction_db \
  -v ON_ERROR_STOP=1 < db/seed.sql
```

For query-performance experiments, `db/day9-performance-data.sql` creates a larger dataset. The `db/day10-covering-index.sql` and `db/day10-partial-index.sql` files define the experimental indexes, and `db/day10-explain-queries.sql` runs their baseline and indexed query comparisons. See the [Day 10 analysis](docs/day10-index-analysis.md#reproduce-the-experiments) for the run commands and measured results. The performance guides and reports are under `docs/`; run data-loading scripts only after creating the schema.

See the [Day 11 transactions investigation](docs/day11-transactions.md) for atomic transfers, ledger checks, rollback and savepoint experiments, row locking, isolation levels, and the schema's ledger-balancing limitation.

## Project layout

```text
pom.xml                                  # Maven parent and module configuration
transaction-domain/
  pom.xml
  src/main/java/io/github/eyupuk/transactions/domain/
  src/test/java/io/github/eyupuk/transactions/domain/
transaction-application/
  pom.xml
  src/main/java/io/github/eyupuk/transactions/
    analytics/
    application/
    concurrency/
    infrastructure/
    performance/
  src/test/java/io/github/eyupuk/transactions/
db/                                      # PostgreSQL schema, seed and performance SQL
compose.yaml                             # Local PostgreSQL container
docs/                                    # Engineering notes and database/JVM exercises
```

## Notes

This is a learning-oriented project rather than a production payments system. It focuses on domain correctness, application boundaries, validation, analytics, introductory concurrency, JVM diagnostics, and database design rather than a production database integration, web APIs, or distributed transaction processing.
