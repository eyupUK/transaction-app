
package io.github.eyupuk.transactions.concurrency;

import io.github.eyupuk.transactions.domain.Account;
import io.github.eyupuk.transactions.domain.Money;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.*;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

class UnsafeAccountStoreTest {

    @Test
    void demonstratesTwoWithdrawalsUsingSameOldBalance()
            throws Exception {

        Account opening = new Account(
                UUID.randomUUID(),
                Money.of("100.00", "GBP")
        );

        UnsafeAccountStore store =
                new UnsafeAccountStore(opening);

        CountDownLatch bothRead = new CountDownLatch(2);

        Callable<Account> attempt = () -> {

            // Both workers capture the same balance.
            Account observed = store.snapshot();

            bothRead.countDown();

            // Neither may proceed until both have read.
            if (!bothRead.await(5, SECONDS)) {
                throw new IllegalStateException(
                        "Timed out waiting for both workers"
                );
            }

            Account updated = observed.withdraw(
                    Money.of("80.00", "GBP")
            );

            store.replace(updated);
            return updated;
        };

        try (ExecutorService executor =
                     Executors.newFixedThreadPool(2)) {

            Future<Account> first = executor.submit(attempt);
            Future<Account> second = executor.submit(attempt);

            // Both requests wrongly succeed.
            assertEquals(
                    Money.of("20.00", "GBP"),
                    first.get(5, SECONDS).balance()
            );

            assertEquals(
                    Money.of("20.00", "GBP"),
                    second.get(5, SECONDS).balance()
            );
        }

        // Stored state reflects just one subtraction.
        assertEquals(
                Money.of("20.00", "GBP"),
                store.snapshot().balance()
        );
    }
}
