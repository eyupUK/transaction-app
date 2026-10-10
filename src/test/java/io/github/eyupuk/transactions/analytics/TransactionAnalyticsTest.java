
package io.github.eyupuk.transactions.analytics;

import io.github.eyupuk.transactions.domain.Money;
import io.github.eyupuk.transactions.domain.Transaction;
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
}
