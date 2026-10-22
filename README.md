# Java Transaction Platform

A learning project for exploring financial domain modelling, transaction analytics, application layering, concurrency, JVM memory behaviour, PostgreSQL query performance, and JDBC persistence with atomic transfers.

## Modules

- `transaction-domain` contains the transaction domain model:
  - `Money` is an immutable value object backed by `BigDecimal` and `Currency`. It supports GBP and EUR, normalizes amounts to two decimal places without rounding, rejects unsupported currencies, and provides currency-safe arithmetic and zero-value creation.
  - `Account` models a balance with validation for deposits, withdrawals, and opening balances.
  - `Transaction` represents a transfer with source and destination account IDs, amount, and timestamp. It validates required data and rejects invalid amounts and self-transfers.
  - `TransferPolicy` applies a maximum-transfer rule using whole-pence values.
- `transaction-application` contains application services, repository abstractions and examples:
  - `AccountService` uses `AccountRepository` for balance queries, deposits, and withdrawals.
  - `InMemoryAccountRepository` provides atomic in-memory account updates.
  - `JdbcAccountRepository` persists account updates in PostgreSQL, using `SELECT ... FOR UPDATE` and a database transaction to prevent lost updates.
  - `JdbcTransferService` updates both account balances, creates a transfer and its debit/credit ledger postings, and commits them together. It locks accounts in a consistent ID order and rolls back SQL or domain failures.
  - `SqlConnectionFactory` reads JDBC connection settings from `DB_URL`, `DB_USER`, and `DB_PASSWORD`.
  - `TransactionAnalytics` provides stream-based reporting for outgoing transfers, totals by currency, top transfers, and duplicate transaction IDs.
  - `UnsafeAccountStore` and `SynchronizedAccountProcessor` demonstrate unsynchronized and synchronized state updates.
  - `BoundedTransactionHistory` stores a capacity-limited, synchronized history and returns independent snapshots.
  - `MemoryLab` demonstrates temporary allocation and intentional object retention for JVM diagnostics.
  - `App` is the greeting entry point; `BackendDemo` demonstrates account deposits and withdrawals through the service and repository.

## Prerequisites

- JDK 21 or newer
- Maven 3.9 or newer
- Docker Compose (optional, for the PostgreSQL exercises and JDBC integration tests)

## Build and test

Run the Maven verification lifecycle from the repository root:

```bash
mvn clean verify
```

This builds both modules, runs their JUnit tests, and packages the module JARs. The JDBC integration tests run when `DB_URL` is set; otherwise they are skipped. See [PostgreSQL integration tests](#postgresql-integration-tests) for database setup and connection settings.

GitHub Actions runs the same Maven verification with JDK 21. The current workflow has no PostgreSQL service or database environment variables, so it runs the unit tests and skips the JDBC integration tests. The SQL experiments are also outside the current CI checks.

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
java -cp "transaction-application/target/classes:transaction-domain/target/classes" \
  io.github.eyupuk.transactions.BackendDemo
```

It uses `InMemoryAccountRepository` to deposit GBP 25.00 and withdraw GBP 40.00 from an account opened with GBP 100.00, printing balances of GBP 125.00 and GBP 85.00. On Windows, use `;` as the classpath separator.

## Run the memory lab

The memory lab accepts `temporary` or `retained` as its mode. The temporary mode allocates short-lived arrays; the retained mode keeps references to its arrays for inspection. With no argument, it uses `temporary`.

```bash
java -cp transaction-application/target/classes io.github.eyupuk.transactions.performance.MemoryLab temporary
java -cp transaction-application/target/classes io.github.eyupuk.transactions.performance.MemoryLab retained
```

Each run pauses briefly before exiting so JVM diagnostic tools can attach. See [the JVM performance investigation](docs/day5-jvm-performance.md) for profiling steps.

## PostgreSQL exercises

Docker Compose provides PostgreSQL 17 on `127.0.0.1:5433`. Use `transaction_db` for the SQL exercises and a separate `transaction_jdbc_test` database for the Java integration tests below. Start the container and create the exercise schema once in a fresh database:

```bash
docker compose up -d --wait
docker compose exec -T db \
  psql -X -U transaction_user -d transaction_db \
  -v ON_ERROR_STOP=1 < db/schema.sql
```

Load the small sample dataset:

```bash
docker compose exec -T db \
  psql -X -U transaction_user -d transaction_db \
  -v ON_ERROR_STOP=1 < db/seed.sql
```

For query-performance experiments, `db/day9-performance-data.sql` creates a larger dataset. The `db/day10-covering-index.sql` and `db/day10-partial-index.sql` files define the experimental indexes, and `db/day10-explain-queries.sql` runs their baseline and indexed query comparisons. See the [Day 10 analysis](docs/day10-index-analysis.md#reproduce-the-experiments) for the run commands and measured results. The performance guides and reports are under `docs/`; run data-loading scripts only after creating the schema.

See the [Day 11 transactions investigation](docs/day11-transactions.md) for atomic transfers, ledger checks, rollback and savepoint experiments, row locking, isolation levels, and the schema's ledger-balancing limitation.

See the [Day 12 JDBC persistence guide](docs/day12-jdbc-persistence.md) for connection handling, account repositories, the transfer transaction and integration-test design.

## PostgreSQL integration tests

Start PostgreSQL and initialize the dedicated test database. Run the database creation and schema commands once; if that database is already initialized, continue with the connection settings.

```bash
docker compose up -d --wait
docker compose exec -T db \
  psql -X -U transaction_user -d postgres \
  -v ON_ERROR_STOP=1 -c "CREATE DATABASE transaction_jdbc_test;"

docker compose exec -T db \
  psql -X -U transaction_user -d transaction_jdbc_test \
  -v ON_ERROR_STOP=1 < db/schema.sql
```

Set the connection settings for the local Compose container, then run the full build:

```bash
export DB_URL='jdbc:postgresql://127.0.0.1:5433/transaction_jdbc_test'
export DB_USER='transaction_user'
export DB_PASSWORD='local_dev_only_password'

mvn clean verify
```

To run only the JDBC integration tests:

```bash
mvn -pl transaction-application -am \
  -Dtest=JdbcAccountRepositoryTest,JdbcTransferServiceTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Both JDBC suites reject URLs whose effective database name is not exactly `transaction_jdbc_test`, before opening a connection or creating fixtures. The shared test guard uses pgJDBC's URL parser, including database-name overrides in query parameters. Its regression tests run without PostgreSQL.

The integration tests create accounts with unique UUIDs and clean up their own records afterward, deleting ledger entries before transfers and accounts. They require only the schema; seed and Day 11 fixture data are unnecessary.

Coverage includes persisted deposits and withdrawals, unknown accounts, insufficient funds, currency validation, duplicate transfer IDs and concurrent updates. The transfer rollback test forces a PostgreSQL constraint failure during CREDIT insertion after verifying both balance updates and the DEBIT posting, then checks that the entire transaction was undone.

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
      jdbc/
    performance/
  src/test/java/io/github/eyupuk/transactions/
    infrastructure/jdbc/                 # PostgreSQL integration tests
db/                                      # PostgreSQL schema, seed and performance SQL
compose.yaml                             # Local PostgreSQL container
docs/                                    # Engineering notes and database/JVM exercises
```

## Notes

This is a learning-oriented project rather than a production payments system. It covers domain correctness, application boundaries, validation, analytics, concurrency, JVM diagnostics, database design and JDBC transaction management.

The transfer logic keeps balance updates and ledger postings consistent within its transaction. The schema does not independently enforce balanced postings for every possible writer. Production use would require stronger enforcement of these invariants, controlled write access and an idempotency strategy for retries after uncertain commit outcomes.
