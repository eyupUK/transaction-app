package io.github.eyupuk.transactions.performance;

import io.github.eyupuk.transactions.domain.Money;
import io.github.eyupuk.transactions.domain.Transaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

public class BoundedTransactionHistoryTest {

    @DisplayName("Reject zero and negative capacity")
    @ParameterizedTest(name = "Reject capacity {0}")
    @ValueSource(ints = {0, -1})
    void rejectZeroAndNegativeCapacity(int capacity) {

        IllegalArgumentException exc = assertThrows(IllegalArgumentException.class, () -> {
            new BoundedTransactionHistory(capacity);
        });
        assertTrue(exc.getMessage().contains("Capacity must be positive"));
    }

    @DisplayName("Start with an empty history")
    @Test
    void startWithEmptyHistory() {
        BoundedTransactionHistory history = new BoundedTransactionHistory(10);
        assertTrue(history.snapshot().isEmpty());
    }

    @DisplayName("Record transactions up to capacity")
    @Test
    void recordTransactionsUpToCapacity() {
        BoundedTransactionHistory history = new BoundedTransactionHistory(3);
        history.record(new Transaction(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Money.of("100", "GBP"), Instant.now()));
        history.record(new Transaction(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Money.of("200", "GBP"), Instant.now()));
        history.record(new Transaction(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Money.of("300", "GBP"), Instant.now()));
        assertEquals(3, history.snapshot().size());
    }

    @DisplayName("Evict the oldest transaction when full")
    @Test
    void evictOldestTransactionWhenFull() {
        BoundedTransactionHistory history = new BoundedTransactionHistory(3);
        Transaction t1 = new Transaction(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Money.of("100", "GBP"), Instant.now());
        Transaction t2 = new Transaction(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Money.of("200", "GBP"), Instant.now());
        Transaction t3 = new Transaction(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Money.of("300", "GBP"), Instant.now());
        Transaction t4 = new Transaction(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Money.of("400", "GBP"), Instant.now());

        history.record(t1);
        history.record(t2);
        history.record(t3);
        history.record(t4);

        assertTrue(history.snapshot().contains(t4));
        assertFalse(history.snapshot().contains(t1));
        assertEquals(List.of(t2, t3, t4), history.snapshot());
    }

    @DisplayName("Preserve insertion order")
    @Test
    void preserveInsertionOrder() {
        BoundedTransactionHistory history = new BoundedTransactionHistory(3);
        Transaction t1 = new Transaction(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Money.of("100", "GBP"), Instant.now());
        Transaction t2 = new Transaction(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Money.of("200", "GBP"), Instant.now());
        Transaction t3 = new Transaction(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Money.of("300", "GBP"), Instant.now());

        history.record(t1);
        history.record(t2);
        history.record(t3);

        List<Transaction> snapshot = history.snapshot();
        assertEquals(List.of(t1, t2, t3), snapshot);
    }

    @DisplayName("Return an unmodifiable snapshot")
    @Test
    void returnUnmodifiableSnapshot() {
        BoundedTransactionHistory history = new BoundedTransactionHistory(3);
        Transaction t1 = new Transaction(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Money.of("100", "GBP"), Instant.now());
        history.record(t1);
        List<Transaction> snapshot = history.snapshot();
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(t1));
    }

    @DisplayName("Ensure an earlier snapshot does not change after new records")
    @Test
    void ensureEarlierSnapshotDoesNotChangeAfterNewRecords() {
        BoundedTransactionHistory history = new BoundedTransactionHistory(3);
        Transaction t1 = new Transaction(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Money.of("100", "GBP"), Instant.now());
        Transaction t2 = new Transaction(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Money.of("200", "GBP"), Instant.now());
        history.record(t1);
        List<Transaction> snapshot1 = history.snapshot();
        history.record(t2);
        List<Transaction> snapshot2 = history.snapshot();
        assertEquals(List.of(t1), snapshot1);
        assertEquals(List.of(t1, t2), snapshot2);
    }

    @DisplayName("Reject null transactions")
    @Test
    void rejectNullTransactions() {
        BoundedTransactionHistory history = new BoundedTransactionHistory(3);
        assertThrows(NullPointerException.class, () -> history.record(null));
    }

    @DisplayName("Preserve the capacity bound under concurrent recording")
    @Test
    void preserveCapacityBoundUnderConcurrentRecording() throws Exception {
        BoundedTransactionHistory history = new BoundedTransactionHistory(3);
        Transaction t1 = new Transaction(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Money.of("100", "GBP"), Instant.now());
        Transaction t2 = new Transaction(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Money.of("200", "GBP"), Instant.now());
        Transaction t3 = new Transaction(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Money.of("300", "GBP"), Instant.now());
        Transaction t4 = new Transaction(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Money.of("400", "GBP"), Instant.now());

        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch latch = new CountDownLatch(1);
        Runnable recordTask = () -> {
            try {
                latch.await();
                history.record(t1);
                history.record(t2);
                history.record(t3);
                history.record(t4);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        executor.submit(recordTask);
        executor.submit(recordTask);
        executor.submit(recordTask);
        executor.submit(recordTask);
        latch.countDown();
        executor.shutdown();
        try {
            assertTrue(
                    executor.awaitTermination(1, TimeUnit.MINUTES),
                    "Executor did not terminate within one minute"
            );
        }
        finally {
            executor.shutdownNow();
        }
        List<Transaction> snapshot = history.snapshot();
        assertTrue(snapshot.size() <= 3);
    }
}
