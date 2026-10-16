package io.github.eyupuk.transactions.infrastructure;

import io.github.eyupuk.transactions.application.AccountRepository;
import io.github.eyupuk.transactions.application.AccountService;
import io.github.eyupuk.transactions.domain.Account;
import io.github.eyupuk.transactions.domain.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class InMemoryAccountRepositoryTest {

    @DisplayName("Find all supplied accounts")
    @Test
    void findAllSuppliedAccounts() {
        Account account1 = new Account(
                UUID.randomUUID(),
                Money.of("100.00", "GBP")
        );
        Account account2 = new Account(
                UUID.randomUUID(),
                Money.of("200.00", "EUR")
        );
        Account account3 = new Account(
                UUID.randomUUID(),
                Money.of("150.00", "EUR")
        );

        AccountRepository repository =
                new InMemoryAccountRepository(List.of(account1, account2));

        assertTrue(repository.findById(account1.id()).isPresent());
        assertTrue(repository.findById(account2.id()).isPresent());
        assertFalse(repository.findById(account3.id()).isPresent());
    }

    @DisplayName("Reject duplicate IDs during repository construction")
    @Test
    void rejectDuplicateIDsDuringRepositoryConstruction() {
        UUID duplicateId = UUID.randomUUID();
        Account account1 = new Account(
                duplicateId,
                Money.of("100.00", "GBP")
        );
        Account account2 = new Account(
                duplicateId,
                Money.of("200.00", "EUR")
        );

        assertThrows(IllegalArgumentException.class, () -> {
            new InMemoryAccountRepository(List.of(account1, account2));
        });
    }
    @DisplayName("Reject an updated account with a different UUID")
    @Test
    void rejectUpdatedAccountWithDifferentUUID() {
        Account opening = new Account(
                UUID.randomUUID(),
                Money.of("100.00", "GBP")
        );

        AccountRepository repository =
                new InMemoryAccountRepository(List.of(opening));

        AccountService service = new AccountService(repository);

        assertThrows(IllegalStateException.class, () -> {
            service.deposit(opening.id(), Money.of("50.00", "GBP"));
            // Attempt to update with a different UUID
            repository.update(opening.id(), account -> new Account(UUID.randomUUID(), account.balance()));
        });

    }

    @DisplayName("Reject a null result and preserve the old mapping")
    @Test
    void rejectNullResultAndPreserveOldMapping() {
        Account opening = new Account(
                UUID.randomUUID(),
                Money.of("100.00", "GBP")
        );

        AccountRepository repository =
                new InMemoryAccountRepository(List.of(opening));

        AccountService service = new AccountService(repository);

        assertThrows(IllegalStateException.class, () -> {
            repository.update(opening.id(), account -> null);
        });

        // Ensure the balance remains unchanged
        Money balanceAfterFailedUpdate = service.getBalance(opening.id());
        assertEquals(Money.of("100.00", "GBP"), balanceAfterFailedUpdate);
    }

    /*
        Concurrency challenge
        Create 40 tasks, each depositing £1 into the same account starting from £0.
        Every deposit must succeed, and the final balance must be exactly £40.
        Use ExecutorService, coordinated start signals if helpful, and timed Future.get() calls.
        Don't swallow unexpected exceptions. In this test, an executor finishing is not enough—you must verify that all 40 tasks actually succeeded.
     */
    @DisplayName("40 simultaneous £1 deposits into £0 finish at £40")
    @Test
    void simultaneousDeposits() throws Exception{
        Account opening = new Account(
                UUID.randomUUID(),
                Money.of("0.00", "GBP")
        );

        AccountRepository repository =
                new InMemoryAccountRepository(List.of(opening));

        AccountService service = new AccountService(repository);

        int numberOfDeposits = 40;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfDeposits);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < numberOfDeposits; i++) {
            futures.add(executor.submit(() -> {
                try {
                    latch.await(); // Wait for the signal to start
                    service.deposit(opening.id(), Money.of("1.00", "GBP"));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }));
        }

        try {
            latch.countDown();

            for (Future<?> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }

            executor.shutdown();

            assertTrue(
                    executor.awaitTermination(5, TimeUnit.SECONDS)
            );

            assertEquals(
                    Money.of("40.00", "GBP"),
                    service.getBalance(opening.id())
            );
        } finally {
            executor.shutdownNow();
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "Executor did not terminate in time");

        Money finalBalance = service.getBalance(opening.id());
        assertEquals(Money.of("40.00", "GBP"), finalBalance);
    }

}
