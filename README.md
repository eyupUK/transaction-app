# Java Transaction Platform

A small Java project for exploring domain modelling, financial validation rules, transaction analytics, concurrency, and JVM memory behaviour in a banking-style example.

## Modules

- `transaction-domain` contains the transaction domain model:
  - `Money` is an immutable value object backed by `BigDecimal` and `Currency`. It supports GBP and EUR, normalizes amounts to two decimal places without rounding, rejects unsupported currencies, and provides currency-safe arithmetic and zero-value creation.
  - `Account` models a balance with validation for deposits, withdrawals, and opening balances.
  - `Transaction` represents a transfer with source and destination account IDs, amount, and timestamp. It validates required data and rejects invalid amounts and self-transfers.
  - `TransferPolicy` applies a maximum-transfer rule using whole-pence values.
- `transaction-application` contains the application entry point and supporting examples:
  - `TransactionAnalytics` provides stream-based reporting for outgoing transfers, totals by currency, top transfers, and duplicate transaction IDs.
  - `UnsafeAccountStore` and `SynchronizedAccountProcessor` demonstrate unsynchronized and synchronized state updates.
  - `BoundedTransactionHistory` stores a capacity-limited, synchronized history and returns independent snapshots.
  - `MemoryLab` demonstrates temporary allocation and intentional object retention for JVM diagnostics.

## Prerequisites

- JDK 21 or newer
- Maven 3.9 or newer

## Build and test

Run the Maven verification lifecycle from the repository root:

```bash
mvn clean verify
```

This builds both modules, runs their JUnit tests, and packages the module JARs.

## Run the sample application

After building:

```bash
java -cp transaction-application/target/classes io.github.eyupuk.transactions.App
```

Expected output:

```text
Java transaction platform: ready
```

## Run the memory lab

The memory lab accepts `temporary` or `retained` as its mode. The temporary mode allocates short-lived arrays; the retained mode keeps references to its arrays for inspection. With no argument, it uses `temporary`.

```bash
java -cp transaction-application/target/classes io.github.eyupuk.transactions.performance.MemoryLab temporary
java -cp transaction-application/target/classes io.github.eyupuk.transactions.performance.MemoryLab retained
```

Each run pauses briefly before exiting so JVM diagnostic tools can attach. See [the JVM performance investigation](docs/day5-jvm-performance.md) for the profiling exercise and [the engineering notes](docs/engineering-notes.md) for learning notes.

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
    concurrency/
    performance/
  src/test/java/io/github/eyupuk/transactions/
    analytics/
    concurrency/
    performance/
docs/
  day5-jvm-performance.md
  engineering-notes.md
```

## Notes

This is a learning-oriented project rather than a production payments system. It focuses on domain correctness, validation, analytics, and introductory concurrency and JVM diagnostics rather than web APIs, persistence, or distributed transaction processing.
