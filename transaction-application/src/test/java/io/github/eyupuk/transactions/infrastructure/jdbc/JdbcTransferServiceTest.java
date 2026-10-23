package io.github.eyupuk.transactions.infrastructure.jdbc;

import io.github.eyupuk.transactions.domain.Money;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class JdbcTransferServiceTest {

    private SqlConnectionFactory connections;
    private JdbcTransferService service;
    private UUID alice;
    private UUID bob;
    private UUID transferId;

    @BeforeEach
    void createAccounts() throws SQLException {
        connections = JdbcTestDatabase.fromEnvironment();

        // Clean up previous test data, if any, before seeding new accounts
        if (alice != null && bob != null) {
            deleteTestRecords();
        }

        service = new JdbcTransferService(connections);
        alice = UUID.randomUUID();
        bob = UUID.randomUUID();
        transferId = UUID.randomUUID();

        seedAccount(alice, "Test Alice", "100.00");
        seedAccount(bob, "Test Bob", "200.00");
    }

    @AfterEach
    void deleteTestRecords() throws SQLException {
        if (connections == null || alice == null || bob == null) {
            return;
        }

        try (Connection connection = connections.open()) {
            connection.setAutoCommit(false);

            execute(connection, """
                    DELETE FROM ledger_entries
                    WHERE account_id IN (?, ?)
                       OR transfer_id IN (
                           SELECT id FROM transfers
                           WHERE source_account_id IN (?, ?)
                              OR destination_account_id IN (?, ?)
                       )
                    """, alice, bob, alice, bob, alice, bob);
            execute(connection, """
                    DELETE FROM transfers
                    WHERE source_account_id IN (?, ?)
                       OR destination_account_id IN (?, ?)
                    """, alice, bob, alice, bob);
            execute(connection,
                    "DELETE FROM accounts WHERE id IN (?, ?)", alice, bob);

            connection.commit();
        }
    }

    @Test
    void transferCommitsBothBalancesAndLedger() throws SQLException {
        UUID committedId = service.transfer(
                transferId, alice, bob, Money.of("80.00", "GBP")
        );

        assertEquals(transferId, committedId);
        assertAccountState(alice, "GBP", "20.00", 1);
        assertAccountState(bob, "GBP", "280.00", 1);
        assertRowCounts(1, 2);
        assertCompletedTransfer(transferId);
    }

    @Test
    void insufficientFundsLeavesNoChanges() throws SQLException {
        var failure = assertThrows(
                IllegalStateException.class,
                () -> service.transfer(
                        transferId, alice, bob, Money.of("150.00", "GBP")
                )
        );

        assertTrue(failure.getMessage().startsWith("Insufficient balance:"));
        assertInitialState();
    }

    @Test
    void unknownSourceLeavesNoChanges() throws SQLException {
        assertThrows(
                NoSuchElementException.class,
                () -> service.transfer(
                        transferId, UUID.randomUUID(), bob,
                        Money.of("80.00", "GBP")
                )
        );

        assertInitialState();
    }

    @Test
    void mismatchedAccountCurrenciesLeaveNoChanges() throws SQLException {
        try (Connection connection = connections.open()) {
            execute(connection,
                    "UPDATE accounts SET currency = 'EUR' WHERE id = ?", bob);
        }

        var failure = assertThrows(
                IllegalArgumentException.class,
                () -> service.transfer(
                        transferId, alice, bob, Money.of("80.00", "GBP")
                )
        );

        assertTrue(failure.getMessage().startsWith("Currency mismatch:"));
        assertAccountState(alice, "GBP", "100.00", 0);
        assertAccountState(bob, "EUR", "200.00", 0);
        assertRowCounts(0, 0);
    }

    @Test
    void mismatchedRequestCurrencyLeavesNoChanges() throws SQLException {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.transfer(
                        transferId, alice, bob, Money.of("80.00", "EUR")
                )
        );

        assertInitialState();
    }

    @Test
    void unsupportedCurrencyIsRejectedBeforeTransfer() throws SQLException {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.transfer(
                        transferId, alice, bob, Money.of("80.00", "USD")
                )
        );

        assertInitialState();
    }

    @Test
    void selfTransferIsRejectedBeforeOpeningConnection() throws SQLException {
        var rejectingService = new JdbcTransferService(() -> {
            throw new AssertionError("Self-transfer must not open a connection");
        });

        var failure = assertThrows(
                IllegalArgumentException.class,
                () -> rejectingService.transfer(
                        transferId, alice, alice, Money.of("80.00", "GBP")
                )
        );

        assertEquals("Self-transfer", failure.getMessage());
        assertInitialState();
    }

    @Test
    void duplicateTransferIdDoesNotRepeatBalanceUpdates() throws SQLException {
        service.transfer(transferId, alice, bob, Money.of("80.00", "GBP"));

        // Alice can afford this amount, so the retry must reach the unique key.
        var failure = assertThrows(
                JdbcPersistenceException.class,
                () -> service.transfer(
                        transferId, alice, bob, Money.of("10.00", "GBP")
                )
        );

        SQLException cause = assertInstanceOf(SQLException.class, failure.getCause());
        assertEquals("23505", cause.getSQLState());
        assertAccountState(alice, "GBP", "20.00", 1);
        assertAccountState(bob, "GBP", "280.00", 1);
        assertRowCounts(1, 2);
        assertCompletedTransfer(transferId);
    }

    @Test
    void concurrentWithdrawalsCannotOverspend() throws Exception {
        UUID secondTransferId = UUID.randomUUID();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);

        boolean firstSucceeded;
        boolean secondSucceeded;
        try {
            var first = executor.submit(
                    () -> attemptTransfer(transferId, ready, start)
            );
            var second = executor.submit(
                    () -> attemptTransfer(secondTransferId, ready, start)
            );

            assertTrue(ready.await(10, SECONDS), "Both transfers must be ready");
            start.countDown();
            firstSucceeded = first.get(20, SECONDS);
            secondSucceeded = second.get(20, SECONDS);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(
                    executor.awaitTermination(20, SECONDS),
                    "Transfers must terminate before fixture cleanup"
            );
        }

        assertEquals(1, (firstSucceeded ? 1 : 0) + (secondSucceeded ? 1 : 0));
        assertAccountState(alice, "GBP", "20.00", 1);
        assertAccountState(bob, "GBP", "280.00", 1);
        assertRowCounts(1, 2);
        assertCompletedTransfer(firstSucceeded ? transferId : secondTransferId);

        UUID rejectedId = firstSucceeded ? secondTransferId : transferId;
        try (Connection connection = connections.open()) {
            assertEquals(0, count(connection,
                    "SELECT COUNT(*) FROM transfers WHERE id = ?", rejectedId));
            assertEquals(0, count(connection,
                    "SELECT COUNT(*) FROM ledger_entries WHERE transfer_id = ?",
                    rejectedId));
        }
    }

    @Test
    void creditInsertionFailureRollsBackBalancesTransferAndDebitPosting()
            throws SQLException {
        var creditFailureInjected = new AtomicBoolean();
        var failingService = new JdbcTransferService(() ->
                rejectCreditAmount(connections.open(), creditFailureInjected)
        );

        var failure = assertThrows(
                JdbcPersistenceException.class,
                () -> failingService.transfer(
                        transferId, alice, bob, Money.of("80.00", "GBP")
                )
        );

        assertTrue(creditFailureInjected.get(), "Must reach the CREDIT insertion");
        SQLException cause = assertInstanceOf(SQLException.class, failure.getCause());
        assertEquals("23514", cause.getSQLState());
        assertTrue(cause.getMessage().contains("ledger_entries_amount_check"));
        assertInitialState();
    }

    private boolean attemptTransfer(
            UUID candidateId, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        if (!start.await(10, SECONDS)) {
            throw new IllegalStateException("Start signal timed out");
        }

        try {
            service.transfer(candidateId, alice, bob, Money.of("80.00", "GBP"));
            return true;
        } catch (IllegalStateException failure) {
            assertTrue(failure.getMessage().startsWith("Insufficient balance:"));
            return false;
        }
    }

    private void seedAccount(UUID id, String owner, String balance)
            throws SQLException {
        try (Connection connection = connections.open();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO accounts (id, owner_name, currency, balance)
                     VALUES (?, ?, 'GBP', ?)
                     """)) {
            statement.setObject(1, id);
            statement.setString(2, owner);
            statement.setBigDecimal(3, new BigDecimal(balance));
            assertEquals(1, statement.executeUpdate());
        }
    }

    private void assertInitialState() throws SQLException {
        assertAccountState(alice, "GBP", "100.00", 0);
        assertAccountState(bob, "GBP", "200.00", 0);
        assertRowCounts(0, 0);
    }

    private void assertAccountState(
            UUID id, String currency, String balance, long version)
            throws SQLException {
        try (Connection connection = connections.open()) {
            assertAccountState(connection, id, currency, balance, version);
        }
    }

    private void assertAccountState(
            Connection connection, UUID id, String currency,
            String balance, long version) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT balance, currency, version FROM accounts WHERE id = ?
                """)) {
            statement.setObject(1, id);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next(), "Test account must exist");
                assertEquals(new BigDecimal(balance), result.getBigDecimal("balance"));
                assertEquals(currency, result.getString("currency"));
                assertEquals(version, result.getLong("version"));
            }
        }
    }

    private void assertRowCounts(int transfers, int postings) throws SQLException {
        try (Connection connection = connections.open()) {
            assertEquals(transfers, count(connection, """
                    SELECT COUNT(*) FROM transfers
                    WHERE source_account_id IN (?, ?)
                       OR destination_account_id IN (?, ?)
                    """, alice, bob, alice, bob));
            assertEquals(postings, count(connection, """
                    SELECT COUNT(*) FROM ledger_entries
                    WHERE account_id IN (?, ?)
                    """, alice, bob));
        }
    }

    private void assertCompletedTransfer(UUID id) throws SQLException {
        try (Connection connection = connections.open()) {
            assertTransferRow(connection, id, "COMPLETED");
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT account_id, entry_type, amount
                    FROM ledger_entries WHERE transfer_id = ?
                    ORDER BY entry_type
                    """)) {
                statement.setObject(1, id);
                try (ResultSet result = statement.executeQuery()) {
                    assertLedgerRow(result, bob, "CREDIT");
                    assertLedgerRow(result, alice, "DEBIT");
                    assertFalse(result.next(), "Exactly two ledger entries expected");
                }
            }
        }
    }

    private void assertTransferRow(Connection connection, UUID id, String status)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT source_account_id, destination_account_id,
                       amount, currency, status
                FROM transfers WHERE id = ?
                """)) {
            statement.setObject(1, id);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next(), "Transfer must exist");
                assertEquals(alice, result.getObject("source_account_id", UUID.class));
                assertEquals(bob, result.getObject("destination_account_id", UUID.class));
                assertEquals(new BigDecimal("80.00"), result.getBigDecimal("amount"));
                assertEquals("GBP", result.getString("currency"));
                assertEquals(status, result.getString("status"));
            }
        }
    }

    private void assertLedgerRow(ResultSet result, UUID accountId, String type)
            throws SQLException {
        assertTrue(result.next(), "Expected " + type + " posting");
        assertEquals(accountId, result.getObject("account_id", UUID.class));
        assertEquals(type, result.getString("entry_type"));
        assertEquals(new BigDecimal("80.00"), result.getBigDecimal("amount"));
    }

    private static int count(Connection connection, String sql, UUID... ids)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindIds(statement, ids);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getInt(1);
            }
        }
    }

    private static void execute(Connection connection, String sql, UUID... ids)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindIds(statement, ids);
            statement.executeUpdate();
        }
    }

    private static void bindIds(PreparedStatement statement, UUID... ids)
            throws SQLException {
        for (int i = 0; i < ids.length; i++) {
            statement.setObject(i + 1, ids[i]);
        }
    }

    private Connection rejectCreditAmount(
            Connection connection, AtomicBoolean injected) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, method, arguments) -> {
                    Object result = invoke(method, connection, arguments);
                    if (method.getName().equals("prepareStatement")
                            && arguments[0] instanceof String sql
                            && sql.stripLeading().startsWith("INSERT INTO ledger_entries")) {
                        return rejectCreditAmount(
                                connection, (PreparedStatement) result, injected
                        );
                    }
                    return result;
                }
        );
    }

    private PreparedStatement rejectCreditAmount(
            Connection connection, PreparedStatement statement,
            AtomicBoolean injected) {
        var credit = new AtomicBoolean();
        return (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("setString")
                            && arguments[0].equals(4)) {
                        credit.set("CREDIT".equals(arguments[1]));
                    }
                    if (method.getName().equals("setBigDecimal")
                            && arguments[0].equals(5) && credit.get()) {
                        assertWritesVisibleBeforeCredit(connection);
                        injected.set(true);
                        // PostgreSQL rejects zero via its real amount CHECK.
                        Object[] invalidAmount = arguments.clone();
                        invalidAmount[1] = BigDecimal.ZERO;
                        return invoke(method, statement, invalidAmount);
                    }
                    return invoke(method, statement, arguments);
                }
        );
    }

    private void assertWritesVisibleBeforeCredit(Connection connection)
            throws SQLException {
        assertAccountState(connection, alice, "GBP", "20.00", 1);
        assertAccountState(connection, bob, "GBP", "280.00", 1);
        assertTransferRow(connection, transferId, "PENDING");

        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT account_id, entry_type, amount
                FROM ledger_entries WHERE transfer_id = ?
                """)) {
            statement.setObject(1, transferId);
            try (ResultSet result = statement.executeQuery()) {
                assertLedgerRow(result, alice, "DEBIT");
                assertFalse(result.next(), "CREDIT must not have been written yet");
            }
        }
    }

    private static Object invoke(Method method, Object target, Object[] arguments)
            throws Throwable {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException failure) {
            throw failure.getCause();
        }
    }
}
