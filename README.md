# Java Transaction Platform

A small Java domain-model project for exploring money, accounts, and transfer validation. It is built with Maven and JUnit; it does not yet include a web framework, database, or transaction-processing service.

## Prerequisites

- JDK 21 or newer (the Maven build targets Java 21; JDK 25 is also supported).
- Maven 3.9 or newer.

## Build and test

Run the full Maven verification lifecycle:

```bash
mvn clean verify
```

Run the sample application:

```bash
java -cp target/classes io.github.eyupuk.transactions.App
```

Expected output:

```text
Java transaction platform: ready
```

Maven packages the project as a JAR under `target/`.

## Domain model

- `Money` is an immutable value type using `BigDecimal` and `Currency`. It supports GBP and EUR, normalizes amounts to two decimal places, rejects values that require rounding, and prevents arithmetic between different currencies.
- `Account` is an immutable account model. Deposits and withdrawals return updated accounts; invalid amounts, currency mismatches, negative opening balances, and withdrawals exceeding the balance are rejected.
- `Transaction` captures the source and destination account IDs, amount, and creation time. It rejects missing values, transfers to the same account, and non-positive amounts.
- `TransferPolicy` is a standalone introductory policy that accepts positive whole-pence amounts up to and including a configured maximum.

The JUnit tests cover these domain rules and the application greeting.

## Project layout

```text
src/main/java/io/github/eyupuk/transactions/
  App.java
  domain/
    Account.java
    Money.java
    Transaction.java
    TransferPolicy.java
src/test/java/                  # JUnit tests
docs/engineering-notes.md       # Learning notes
```
