
package io.github.eyupuk.transactions.analytics;

import io.github.eyupuk.transactions.domain.Money;
import io.github.eyupuk.transactions.domain.Transaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class TransactionAnalyticsTest {

    private static final UUID A = new UUID(0L, 1L);
    private static final UUID B = new UUID(0L, 2L);
    private static final UUID C = new UUID(0L, 3L);

    private static Transaction transaction(
            long id,
            UUID from,
            UUID to,
            String amount,
            String currency,
            String timestamp) {

        return new Transaction(
                new UUID(0L, id),
                from,
                to,
                Money.of(amount, currency),
                Instant.parse(timestamp)
        );
    }

    private static List<Transaction> sampleTransactions() {
        return List.of(
                transaction(101, A, B, "100.50", "GBP",
                        "2026-10-01T10:00:00Z"),
                transaction(102, A, C, "20.25", "GBP",
                        "2026-10-02T10:00:00Z"),
                transaction(103, B, A, "50.00", "GBP",
                        "2026-10-03T10:00:00Z"),
                transaction(104, C, B, "70.00", "EUR",
                        "2026-10-04T10:00:00Z"),
                transaction(105, A, C, "15.00", "EUR",
                        "2026-10-05T10:00:00Z")
        );
    }

    @Test
    void filtersOutgoingTransactions() {
        List<Transaction> transactions = sampleTransactions();

        List<Transaction> result =
                TransactionAnalytics.outgoingFrom(transactions, A);

        assertEquals(3, result.size());
        assertEquals(
                List.of(
                        transactions.get(0),
                        transactions.get(1),
                        transactions.get(4)
                ),
                result
        );
    }

    // TODO: Write tests for the other methods.
    @DisplayName("Unknown account returns an empty list")
    @Test
    void unknownAccountReturnsEmptyList() {
        List<Transaction> transactions = sampleTransactions();

        List<Transaction> result =
                TransactionAnalytics.outgoingFrom(transactions, new UUID(0L, 999L));

        assertTrue(result.isEmpty());
    }

    @DisplayName("Filtering doesn't modify the source list")
    @Test
    void filteringDoesntModifySourceList() {
        List<Transaction> transactions = sampleTransactions();

        List<Transaction> result =
                TransactionAnalytics.outgoingFrom(transactions, A);

        assertEquals(3, result.size());
        assertEquals(
                List.of(
                        transactions.get(0),
                        transactions.get(1),
                        transactions.get(4)
                ),
                result
        );
        assertEquals(5, transactions.size());
    }

    @DisplayName("Correct outgoing counts for accounts A, B and C")
    @Test
    void correctOutgoingCounts() {
        List<Transaction> transactions = sampleTransactions();

        assertEquals(3, TransactionAnalytics.outgoingFrom(transactions, A).size());
        assertEquals(1, TransactionAnalytics.outgoingFrom(transactions, B).size());
        assertEquals(1, TransactionAnalytics.outgoingFrom(transactions, C).size());
    }

    @DisplayName("Empty list returns an empty map")
    @Test
    void emptyListReturnsEmptyMap() {
        List<Transaction> transactions = Collections.emptyList();

        Map<UUID, Long> result = TransactionAnalytics.outgoingCounts(transactions);

        assertTrue(result.isEmpty());
    }

    @DisplayName("GBP and EUR totals are calculated separately")
    @Test
    void gbpAndEurTotalsAreCalculatedSeparately() {
        List<Transaction> transactions = sampleTransactions();

        Map<Currency, BigDecimal> result = TransactionAnalytics.totalsByCurrency(transactions);

        assertEquals(new BigDecimal("170.75"), result.get(Currency.getInstance("GBP")));
        assertEquals(new BigDecimal("85.00"), result.get(Currency.getInstance("EUR")));
    }
    @DisplayName("Empty list returns an empty map")
    @Test
    void emptyListReturnsEmptyMapForTotalsByCurrency() {
        List<Transaction> transactions = Collections.emptyList();

        Map<Currency, BigDecimal> result = TransactionAnalytics.totalsByCurrency(transactions);

        assertTrue(result.isEmpty());
    }

    @DisplayName("Top two GBP transactions are in descending order")
    @Test
    void topTwoGbpTransactionsAreInDescendingOrder() {
        List<Transaction> transactions = sampleTransactions();

        List<Transaction> result = TransactionAnalytics.topTransfers(transactions, Currency.getInstance("GBP"), 2);

        assertEquals(2, result.size());
        assertEquals(transactions.get(0), result.get(0));
        assertEquals(transactions.get(2), result.get(1));
    }

    @DisplayName("Equal amounts use newest timestamp first")
    @Test
    void equalAmountsUseNewestTimestampFirst() {
        List<Transaction> transactions = List.of(
                transaction(201, A, C, "50.00", "GBP", "2026-10-01T10:00:00Z"),
                transaction(202, A, B, "50.00", "GBP", "2026-10-02T10:00:00Z"),
                transaction(203, B, A, "50.00", "GBP", "2026-10-03T10:00:00Z")
        );

        List<Transaction> result = TransactionAnalytics.topTransfers(transactions, Currency.getInstance("GBP"), 2);

        assertEquals(2, result.size());
        assertEquals(transactions.get(2), result.get(0));
        assertEquals(transactions.get(1), result.get(1));
    }

    @DisplayName("Zero limit returns an empty list")
    @Test
    void zeroLimitReturnsEmptyList() {
        List<Transaction> transactions = sampleTransactions();

        List<Transaction> result = TransactionAnalytics.topTransfers(transactions, Currency.getInstance("GBP"), 0);

        assertTrue(result.isEmpty());
    }

    @DisplayName("Negative limit throws IllegalArgumentException")
    @Test
    void negativeLimitThrowsIllegalArgumentException() {
        List<Transaction> transactions = sampleTransactions();

        assertThrows(IllegalArgumentException.class, () -> {
            TransactionAnalytics.topTransfers(transactions, Currency.getInstance("GBP"), -1);
        });
    }

    @DisplayName("Repeated UUIDs are detected even if other fields differ")
    @Test
    void repeatedUUIDsAreDetectedEvenIfOtherFieldsDiffer() {
        long repeatedId = 301L;
        List<Transaction> transactions = List.of(
                transaction(repeatedId, A, B, "10.00", "GBP", "2026-10-01T10:00:00Z"),
                transaction(repeatedId, A, C, "20.00", "GBP", "2026-10-02T10:00:00Z"),
                transaction(302L, B, A, "30.00", "GBP", "2026-10-03T10:00:00Z")
        );
        Set<UUID> result = TransactionAnalytics.duplicateTransactionIds(transactions);
        assertEquals(1, result.size());
        assertTrue(result.contains(new UUID(0L, repeatedId)));
    }

    @DisplayName("Unique transactions produce an empty set")
    @Test
    void uniqueTransactionsProduceEmptySet() {
        List<Transaction> transactions = List.of(
                transaction(301L, A, B, "10.00", "GBP", "2026-10-01T10:00:00Z"),
                transaction(302L, A, C, "20.00", "GBP", "2026-10-02T10:00:00Z"),
                transaction(303L, B, A, "30.00", "GBP", "2026-10-03T10:00:00Z")
        );
        Set<UUID> result = TransactionAnalytics.duplicateTransactionIds(transactions);
        assertTrue(result.isEmpty());
    }

    @DisplayName("Null required method arguments are rejected")
    @Test
    void nullRequiredMethodArgumentsAreRejected() {
        List<Transaction> transactions = sampleTransactions();
        assertThrows(NullPointerException.class, () -> {
            TransactionAnalytics.topTransfers(null, Currency.getInstance("GBP"), 2);
        });
        assertThrows(NullPointerException.class, () -> {
            TransactionAnalytics.topTransfers(transactions, null, 2);
        });
        assertThrows(NullPointerException.class, () -> {
            TransactionAnalytics.outgoingFrom(transactions, null);
        });
        assertThrows(NullPointerException.class, () -> {
            TransactionAnalytics.outgoingCounts(null);
        });
        assertThrows(NullPointerException.class, () -> {
            TransactionAnalytics.totalsByCurrency(null);
        });

    }
}
