## Java JDBC, PostgreSQL Repositories & Transaction Management

```text
Understand JDBC's Connection, PreparedStatement, ResultSet and SQLException.
Connect Java 21 to PostgreSQL 17 using pgJDBC.
Implement JdbcAccountRepository without modifying the existing AccountService.
Use SELECT ... FOR UPDATE to prevent lost updates.
Manage a multi-step transfer with setAutoCommit(false), commit() and rollback().
Test real database persistence, failure paths and concurrency.
```

Part 1 — Set up JDBC and a dedicated test database
Exercise 1 · 30 minutes
Step 1 — Create your branch
```bash
git switch main
git pull --ff-only
git switch -c feature/day12-jdbc-persistence
```


### Step 2 — Add the PostgreSQL JDBC dependency

The official pgJDBC project currently lists 42.7.14, released on 7 October 2026, as its latest stable version.

In transaction-application/pom.xml, add this inside <dependencies>:
```xml
<dependency>
<groupId>org.postgresql</groupId>
<artifactId>postgresql</artifactId>
<version>42.7.14</version>
</dependency>
```


Leave the transaction-domain module unchanged. A domain model shouldn't need a JDBC dependency merely to represent financial accounts.

Run:
```bash
mvn clean verify
```

### Step 3 — Create a separate JDBC test database
Your transaction_db already contains performance data and Day 11 experiments. We don't want automated tests unexpectedly changing those records.

Start Docker:
```bash
docker compose up -d
```

Create a new database (run once):
```bash
docker compose exec db \
psql -U transaction_user -d postgres \
-c "CREATE DATABASE transaction_jdbc_test;"
```

If you've already created it, PostgreSQL will report that it exists; simply continue.

Apply your existing schema:
```bash
docker compose exec -T db \
psql -U transaction_user -d transaction_jdbc_test \
-v ON_ERROR_STOP=1 < db/schema.sql
```


Load only the two Day 11 account fixtures:
```bash
docker compose exec -T db \
psql -U transaction_user -d transaction_jdbc_test \
-v ON_ERROR_STOP=1 < db/day11-setup.sql
```


Check:
```bash
docker compose exec db \
psql -U transaction_user -d transaction_jdbc_test \
-c "SELECT owner_name, balance FROM accounts ORDER BY id;"
```

Expected: Day11 Alice £100 and Day11 Bob £200.

Do not load the 50,000-record performance dataset into this database.

##### Understand the JDBC components

Before coding, understand the responsibilities of these four interfaces:
```text
JDBC component	    Responsibility
--------------      -----------------
Connection	        Represents a database connection and manages transactions
PreparedStatement	Executes parameterized SQL safely
ResultSet	        Exposes rows returned by a query
SQLException	    Reports database and driver errors, including SQLSTATE
```

JDBC example with the **try-with-resources:**

```java
try (Connection connection =
        DriverManager.getConnection(url, user, password)) {

        try (PreparedStatement statement =
            connection.prepareStatement(
                "SELECT COUNT(*) FROM accounts"
        )) {

            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                System.out.println(rs.getLong(1));
            }
        }
}
```

PreparedStatement uses parameter binding rather than concatenating external input into SQL. For instance:
```java
PreparedStatement statement = connection.prepareStatement(
    "SELECT id, balance, currency FROM accounts WHERE id = ?"
    );
statement.setObject(1, accountId);
```

##### The ? is a parameter placeholder.

##### The critical transaction difference
By default, JDBC connections operate in auto-commit mode. Every completed SQL statement is committed independently.

For a multi-statement operation:
```java
connection.setAutoCommit(false);

try {
    // Execute all related SQL statements.

    connection.commit();
} catch (SQLException | RuntimeException e) {
    connection.rollback();
    throw e;
}
```

The important rule is that every related operation must use the same connection, with a single commit or rollback boundary.

```text
infrastructure/
├── InMemoryAccountRepository.java
└── jdbc/
    ├── SqlConnectionFactory.java
    ├── JdbcPersistenceException.java
    ├── JdbcAccountMapper.java
    └── JdbcAccountRepository.java
```

###### SqlConnectionFactory.java
This gives our repository a way to obtain database connections without hardcoding credentials into its implementation.
The repository will receive SqlConnectionFactory through constructor injection.

###### JdbcPersistenceException.java
Our existing repository interface doesn't declare checked SQLException exceptions.
This keeps database-specific checked exceptions from leaking into the AccountService API.

###### JdbcAccountMapper.java
Notice that we're using getBigDecimal().

###### JdbcAccountRepository.java
```text
This implementation deliberately uses a database transaction in update().
SELECT ... FOR UPDATE locks the selected account row, and the lock is held while the domain transformation executes and the updated balance is persisted.
If two application instances try to withdraw from the same account concurrently, the second waits for the first transaction to finish before applying its transformation to the current locked state.
The Account record stays immutable. The database row, not the Java object, is the shared mutable state.
The version column is incremented for every successful repository update. In this implementation, concurrency is controlled pessimistically through FOR UPDATE, rather than by a version comparison. We'll use optimistic version checking in a later exercise.
Important limitation
UnaryOperator<Account> should represent a short, deterministic domain transformation, such as account.withdraw(amount).
Avoid network calls or expensive work inside that callback. Database locks remain held while it runs. Also, rolling back the database does not undo external side effects performed by Java code.
```
###### JdbcTransferService.java
The architecture:
```text
One JDBC connection, one transaction
1. Open connection and disable auto-commit
2. Lock both accounts in consistent ID order
3. Apply existing Account.withdraw() and Account.deposit() rules
4. Insert the transfer record
5. Update both balances and record both ledger entries
6. Mark transfer completed and commit
```
```text
This is intentionally a JDBC-specific implementation. Later, we can extract a cleaner application-layer TransferService and isolate SQL behind repository and transaction abstractions.
Very important: failure handling
The code handles ordinary SQL and runtime failures by calling rollback().
If the rollback itself fails, we preserve that failure as a suppressed exception on the original error.
Also, a thrown exception during commit() can sometimes leave the client uncertain whether the server committed, especially if connectivity is lost at that moment. Retrying a financial transfer safely requires an explicit idempotency strategy, not simply resending the request.
For today, we're implementing single-database atomicity, not a complete production payment-processing system.
```

### JDBC transfer integration tests

[`JdbcTransferServiceTest`](../transaction-application/src/test/java/io/github/eyupuk/transactions/infrastructure/jdbc/JdbcTransferServiceTest.java) creates fresh account and transfer UUIDs for every test. Teardown deletes ledger entries, then transfers, then accounts in a single transaction, scoped to that test's accounts. These tests require the schema but do not depend on the Day 11 fixtures or test execution order.

Both JDBC integration suites obtain their connections through the shared test-only `JdbcTestDatabase` guard. Before opening a connection or creating fixtures, it uses pgJDBC's parser to require the effective database name to be exactly `transaction_jdbc_test`. This also rejects query parameters that override an otherwise safe database path. `JdbcTestDatabaseTest` covers valid, unsafe and malformed URLs without connecting to PostgreSQL.

With `DB_URL`, `DB_USER` and `DB_PASSWORD` set for the dedicated test database, run:

```bash
mvn -pl transaction-application -am \
  -Dtest=JdbcTransferServiceTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

| Case | Checked result |
|---|---|
| Successful GBP 80.00 transfer | Alice 20.00, Bob 280.00, versions incremented once, one completed transfer and matching debit/credit postings |
| Insufficient funds | Balances and versions unchanged; no transfer or ledger rows |
| Unknown source | Existing accounts unchanged; no transfer or ledger rows |
| Currency validation | Different account currencies, a mismatched request currency and unsupported USD each leave accounts unchanged |
| Self-transfer | Rejected before a JDBC connection is opened |
| Duplicate transfer ID | Unique violation `23505`; original transfer, postings, balances and versions preserved |
| Two concurrent GBP 80.00 withdrawals | Exactly one succeeds; Alice 20.00, Bob 280.00; only the successful transfer has postings |
| Failure during CREDIT insertion | Both balance updates, the pending transfer and the already inserted DEBIT posting roll back |

The duplicate-ID test retries with GBP 10.00 after the successful GBP 80.00 transfer. Alice can afford that request, so the test reaches the duplicate key instead of stopping at insufficient funds. Concurrent transfers use distinct transfer IDs, a coordinated start and timed waits for both task results; unexpected exceptions fail the test.

The atomicity test uses a test-only JDBC wrapper to bind a zero amount for the CREDIT posting, causing PostgreSQL's existing `ledger_entries_amount_check` to reject the actual insertion with SQLSTATE `23514`. Before changing that parameter, the wrapper queries the service's own transaction and verifies Alice at 20.00, Bob at 280.00, both versions at 1, a pending transfer and the GBP 80.00 DEBIT posting. The test therefore proves that writes happened before the database failure. Fresh connections then confirm balances of 100.00 and 200.00, versions of 0 and no transfer or ledger rows. The wrapper affects only that test's connection and requires no trigger or schema changes.
