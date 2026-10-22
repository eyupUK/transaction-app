package io.github.eyupuk.transactions.infrastructure.jdbc;

import io.github.eyupuk.transactions.application.AccountService;
import io.github.eyupuk.transactions.domain.Account;
import io.github.eyupuk.transactions.domain.Money;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(
        named = "DB_URL",
        matches = ".+"
)
class JdbcAccountRepositoryTest {

    private SqlConnectionFactory connections;
    private JdbcAccountRepository repository;
    private AccountService service;
    private UUID accountId;

    @BeforeEach
    void createAccount() throws SQLException {
        connections = JdbcTestDatabase.fromEnvironment();
        repository = new JdbcAccountRepository(connections);
        service = new AccountService(repository);
        accountId = UUID.randomUUID();

        try (var connection = connections.open();
             var statement = connection.prepareStatement("""
                     INSERT INTO accounts (id, owner_name, currency, balance)
                     VALUES (?, 'JDBC repository test', 'GBP', 100.00)
                     """)) {
            statement.setObject(1, accountId);
            assertEquals(1, statement.executeUpdate());
        }
    }

    @AfterEach
    void deleteAccount() throws SQLException {
        if (connections == null || accountId == null) {
            return;
        }

        try (var connection = connections.open();
             var statement = connection.prepareStatement(
                     "DELETE FROM accounts WHERE id = ?")) {
            statement.setObject(1, accountId);
            statement.executeUpdate();
        }
    }

    @Test
    void canReadExistingAccountFromPostgres() {
        assertEquals(
                new Account(accountId, Money.of("100.00", "GBP")),
                repository.findById(accountId).orElseThrow()
        );
    }

    @Test
    void persistsDepositAndReturnsUpdatedAccount() throws SQLException {
        Account updated = service.deposit(
                accountId, Money.of("25.50", "GBP")
        );

        assertEquals(
                new Account(accountId, Money.of("125.50", "GBP")),
                updated
        );
        assertStoredState("125.50", 1);
    }

    @Test
    void persistsWithdrawalAndReturnsUpdatedAccount() throws SQLException {
        Account updated = service.withdraw(
                accountId, Money.of("40.25", "GBP")
        );

        assertEquals(
                new Account(accountId, Money.of("59.75", "GBP")),
                updated
        );
        assertStoredState("59.75", 1);
    }

    @Test
    void rejectsBalanceReadForUnknownAccount() {
        UUID unknownId = UUID.randomUUID();

        assertTrue(repository.findById(unknownId).isEmpty());
        assertThrows(
                NoSuchElementException.class,
                () -> service.getBalance(unknownId)
        );
    }

    @Test
    void rejectsDepositForUnknownAccount() throws SQLException {
        UUID unknownId = UUID.randomUUID();

        assertThrows(
                NoSuchElementException.class,
                () -> service.deposit(unknownId, Money.of("10.00", "GBP"))
        );

        assertTrue(repository.findById(unknownId).isEmpty());
        assertStoredState("100.00", 0);
    }

    @Test
    void rejectsWithdrawalForUnknownAccount() throws SQLException {
        UUID unknownId = UUID.randomUUID();

        assertThrows(
                NoSuchElementException.class,
                () -> service.withdraw(unknownId, Money.of("10.00", "GBP"))
        );

        assertTrue(repository.findById(unknownId).isEmpty());
        assertStoredState("100.00", 0);
    }

    @Test
    void insufficientFundsPreservesBalanceAndVersion() throws SQLException {
        var failure = assertThrows(
                IllegalStateException.class,
                () -> service.withdraw(accountId, Money.of("150.00", "GBP"))
        );

        assertTrue(failure.getMessage().startsWith("Insufficient balance:"));
        assertStoredState("100.00", 0);
    }

    @Test
    void concurrentDepositsDoNotLoseUpdates() throws Exception {
        int deposits = 20;

        List<Account> updatedAccounts = runConcurrently(
                deposits,
                () -> service.deposit(accountId, Money.of("1.00", "GBP"))
        );

        for (Account updated : updatedAccounts) {
            assertEquals(accountId, updated.id());
        }
        assertEquals(
                deposits,
                updatedAccounts.stream().map(Account::balance).distinct().count()
        );
        assertStoredState("120.00", deposits);
    }

    @Test
    void onlyOneConcurrentWithdrawalSucceeds() throws Exception {
        List<Boolean> outcomes = runConcurrently(2, () -> {
            try {
                service.withdraw(accountId, Money.of("80.00", "GBP"));
                return true;
            } catch (IllegalStateException failure) {
                assertTrue(
                        failure.getMessage().startsWith("Insufficient balance:")
                );
                return false;
            }
        });

        assertEquals(
                1,
                outcomes.stream().filter(Boolean::booleanValue).count()
        );
        assertStoredState("20.00", 1);
    }

    private void assertStoredState(String balance, long version)
            throws SQLException {
        assertEquals(Money.of(balance, "GBP"), service.getBalance(accountId));

        try (var connection = connections.open();
             var statement = connection.prepareStatement(
                     "SELECT version FROM accounts WHERE id = ?")) {
            statement.setObject(1, accountId);
            try (var result = statement.executeQuery()) {
                assertTrue(result.next(), "Test account must still exist");
                assertEquals(version, result.getLong("version"));
            }
        }
    }

    private static <T> List<T> runConcurrently(
            int taskCount, Callable<T> task) throws Exception {
        var ready = new CountDownLatch(taskCount);
        var start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(taskCount);
        List<Future<T>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < taskCount; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, SECONDS)) {
                        throw new IllegalStateException("Start signal timed out");
                    }
                    return task.call();
                }));
            }

            assertTrue(ready.await(10, SECONDS), "All tasks must be ready");
            start.countDown();

            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(20, SECONDS));
            }
            return results;
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(
                    executor.awaitTermination(20, SECONDS),
                    "Concurrent tasks must terminate before fixture cleanup"
            );
        }
    }
}
