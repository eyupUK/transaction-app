# Java Transaction Platform

A small Java project for exploring domain modelling, financial validation rules, transaction analytics, and thread-safety in a banking-style example.

## What this project covers

- `Money` is an immutable value object built from `BigDecimal` and `Currency`.
  - Supports `GBP` and `EUR` only.
  - Normalizes values to two decimal places.
  - Rejects values that require rounding.
  - Prevents arithmetic between different currencies.
- `Account` models a balance with validation for deposits, withdrawals, and opening balances.
- `Transaction` represents a transfer with source and destination account IDs, amount, and timestamp.
  - Rejects invalid amounts.
  - Prevents self-transfers.
  - Enforces required data.
- `TransferPolicy` is a simple maximum-transfer rule based on whole-pence values.
- `TransactionAnalytics` provides stream-based reporting for outgoing transfers, total amounts by currency, top transfers, and duplicate transaction IDs.
- `UnsafeAccountStore` and `SynchronizedAccountProcessor` demonstrate the difference between unsynchronized and synchronized state updates for concurrent access.

## Prerequisites

- JDK 21 or newer
- Maven 3.9 or newer

## Build and test

Run the Maven verification lifecycle:

```bash
mvn clean verify
```

This compiles the application and test sources, runs the JUnit suite, and packages the project.

## Run the sample application

```bash
java -cp target/classes io.github.eyupuk.transactions.App
```

Expected output:

```text
Java transaction platform: ready
```

## Project layout

```text
src/main/java/io/github/eyupuk/transactions/
  App.java
  analytics/
    TransactionAnalytics.java
  concurrency/
    UnsafeAccountStore.java
    SynchronizedAccountProcessor.java
  domain/
    Account.java
    Money.java
    Transaction.java
    TransferPolicy.java
src/test/java/                     # JUnit tests
pom.xml                           # Maven build configuration
docs/engineering-notes.md          # Learning notes
```

## Notes

This is a learning-oriented project rather than a production payments system. It focuses on domain correctness, validation, and introductory concurrency concepts rather than web APIs, persistence, or distributed transaction processing.
