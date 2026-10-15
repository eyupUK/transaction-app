
package io.github.eyupuk.transactions.concurrency;

import io.github.eyupuk.transactions.domain.Account;
import io.github.eyupuk.transactions.domain.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

class SynchronizedAccountProcessorTest {

    @Test
    void onlyOneConcurrentWithdrawalSucceeds()
            throws Exception {

        Account opening = new Account(
                UUID.randomUUID(),
                Money.of("100.00", "GBP")
        );

        SynchronizedAccountProcessor processor =
                new SynchronizedAccountProcessor(opening);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<Boolean> attempt = () -> {

            ready.countDown();

            if (!start.await(5, SECONDS)) {
                throw new IllegalStateException(
                        "Start signal timed out"
                );
            }

            try {
                processor.withdraw(
                        Money.of("80.00", "GBP")
                );
                return true;

            } catch (IllegalStateException e) {
                assertTrue(
                        e.getMessage().contains("Insufficient")
                );
                return false;
            }
        };

        try (ExecutorService executor =
                     Executors.newFixedThreadPool(2)) {

            Future<Boolean> first = executor.submit(attempt);
            Future<Boolean> second = executor.submit(attempt);

            assertTrue(ready.await(5, SECONDS));
            start.countDown();

            int successful = 0;

            if (first.get(5, SECONDS)) {
                successful++;
            }

            if (second.get(5, SECONDS)) {
                successful++;
            }

            assertEquals(1, successful);
        }

        assertEquals(
                Money.of("20.00", "GBP"),
                processor.snapshot().balance()
        );
    }

    @DisplayName("The processor returns the original account before any updates")
    @Test
    void returnsOriginalAccountBeforeUpdates() {
        Account opening = new Account(
                UUID.randomUUID(),
                Money.of("100.00", "GBP")
        );

        SynchronizedAccountProcessor processor =
                new SynchronizedAccountProcessor(opening);

        assertEquals(opening, processor.snapshot());
    }

    @DisplayName("Depositing £50 into a £100 account produces £150")
    @Test
    void deposit50Into100Produces150() {
        Account opening = new Account(
                UUID.randomUUID(),
                Money.of("100.00", "GBP")
        );

        SynchronizedAccountProcessor processor =
                new SynchronizedAccountProcessor(opening);

        processor.deposit(Money.of("50.00", "GBP"));

        assertEquals(
                Money.of("150.00", "GBP"),
                processor.snapshot().balance()
        );
    }

    @DisplayName("Withdrawing £30 from £100 produces £70")
    @Test
    void withdraw30From100Produces70() {
        Account opening = new Account(
                UUID.randomUUID(),
                Money.of("100.00", "GBP")
        );

        SynchronizedAccountProcessor processor =
                new SynchronizedAccountProcessor(opening);

        processor.withdraw(Money.of("30.00", "GBP"));

        assertEquals(
                Money.of("70.00", "GBP"),
                processor.snapshot().balance()
        );
    }

    @DisplayName("A £150 withdrawal is rejected and the balance remains £100")
    @Test
    void withdraw150From100IsRejected() {
        Account opening = new Account(
                UUID.randomUUID(),
                Money.of("100.00", "GBP")
        );

        SynchronizedAccountProcessor processor =
                new SynchronizedAccountProcessor(opening);

        assertThrows(IllegalStateException.class, () -> {
            processor.withdraw(Money.of("150.00", "GBP"));
        });

        assertEquals(
                Money.of("100.00", "GBP"),
                processor.snapshot().balance()
        );
    }

    @DisplayName("Zero and negative deposits or withdrawals are rejected")
    @ParameterizedTest(name = "amount={0}")
    @ValueSource(strings = {"0.00", "-10.00"})
    void zeroAndNegativeDepositsOrWithdrawalsAreRejected(String amount) {
        Account opening = new Account(
                UUID.randomUUID(),
                Money.of("100.00", "GBP")
        );

        SynchronizedAccountProcessor processor =
                new SynchronizedAccountProcessor(opening);

        assertThrows(IllegalArgumentException.class, () -> {
            processor.deposit(Money.of(amount, "GBP"));
        });

        assertThrows(IllegalArgumentException.class, () -> {
            processor.withdraw(Money.of(amount, "GBP"));
        });
    }

    @DisplayName("A EUR withdrawal from a GBP account fails without changing the balance")
    @Test
    void eurWithdrawalFromGbpAccountFails() {
        Account opening = new Account(
                UUID.randomUUID(),
                Money.of("100.00", "GBP")
        );

        SynchronizedAccountProcessor processor =
                new SynchronizedAccountProcessor(opening);

        assertThrows(IllegalArgumentException.class, () -> {
            processor.withdraw(Money.of("50.00", "EUR"));
        });

        assertEquals(
                Money.of("100.00", "GBP"),
                processor.snapshot().balance()
        );
    }

    @DisplayName("A previously returned Account remains unchanged after a subsequent deposit")
    @Test
    void previouslyReturnedAccountRemainsUnchangedAfterDeposit() {
        Account opening = new Account(
                UUID.randomUUID(),
                Money.of("100.00", "GBP")
        );

        SynchronizedAccountProcessor processor =
                new SynchronizedAccountProcessor(opening);

        Account snapshot = processor.snapshot();

        processor.deposit(Money.of("50.00", "GBP"));

        assertEquals(
                Money.of("100.00", "GBP"),
                snapshot.balance()
        );
    }

    @DisplayName("Exactly one of two simultaneous £80 withdrawals succeeds against £100")
    @Test
    void exactlyOneOfTwoSimultaneousWithdrawalsSucceeds() {
        Account opening = new Account(
                UUID.randomUUID(),
                Money.of("100.00", "GBP")
        );

        SynchronizedAccountProcessor processor =
                new SynchronizedAccountProcessor(opening);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<Boolean>> results = new ArrayList<>();

        for (int i = 0; i < 2; i++) {
            results.add(executor.submit(() -> {
                try {
                    processor.withdraw(Money.of("80.00", "GBP"));
                    return true;
                } catch (IllegalStateException e) {
                    return false;
                }
            }));
        }

        executor.shutdown();
        try {
            executor.awaitTermination(1, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long successCount = results.stream().filter(future -> {
            try {
                return future.get();
            } catch (InterruptedException | ExecutionException e) {
                return false;
            }
        }).count();

        assertEquals(1, successCount);
    }

    @DisplayName("A null initial Account is rejected")
    @Test
    void nullInitialAccountIsRejected() {
        assertThrows(NullPointerException.class, () -> {
            new SynchronizedAccountProcessor(null);
        });
    }

        /*
        40 simultaneous requests
        Create a processor with £20 and submit 40 withdrawals of £1 each.
        Expected:
        Measurement	Result
        Initial balance	£20
        Requested withdrawals	40
        Successful withdrawals	20
        Rejected withdrawals	20
        Final balance	£0
        Use ExecutorService and Future<Boolean> to count the results.
        Do not introduce artificial sleeps. The correct processor must preserve the account balance invariant regardless of how the tasks are scheduled.
    */
    @DisplayName("40 simultaneous £1 withdrawals from £20 account results in 20 successes and 20 rejections")
    @Test
    void fortySimultaneousWithdrawalsFromTwentyAccount() throws Exception {
        Account opening = new Account(
                UUID.randomUUID(),
                Money.of("20.00", "GBP")
        );

        SynchronizedAccountProcessor processor =
                new SynchronizedAccountProcessor(opening);

        ExecutorService executor = Executors.newFixedThreadPool(40);
        List<Future<Boolean>> results = new ArrayList<>();

        for (int i = 0; i < 40; i++) {
            results.add(executor.submit(() -> {
                try {
                    processor.withdraw(Money.of("1.00", "GBP"));
                    return true;
                } catch (IllegalStateException e) {
                    assertTrue(e.getMessage().contains("Insufficient"));
                    return false;
                }
            }));
        }


        executor.shutdown();

        try {
            assertTrue(
                    executor.awaitTermination(1, TimeUnit.MINUTES),
                    "Executor did not terminate within one minute"
            );

            int successCount = 0;

            for (Future<Boolean> future : results) {
                if (future.get(5, TimeUnit.SECONDS)) {
                    successCount++;
                }
            }

            assertEquals(20, successCount);

            assertEquals(
                    Money.of("0.00", "GBP"),
                    processor.snapshot().balance()
            );

        } finally {
            executor.shutdownNow();
        }


    }
}
