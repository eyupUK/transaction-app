
package io.github.eyupuk.transactions.analytics;

import io.github.eyupuk.transactions.domain.Transaction;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

public final class TransactionAnalytics {

    private TransactionAnalytics() {
        // Utility class: prevent instantiation.
    }

    public static List<Transaction> outgoingFrom(
            List<Transaction> transactions,
            UUID accountId) {

        Objects.requireNonNull(transactions, "transactions");
        Objects.requireNonNull(accountId, "accountId");

        return transactions.stream()
                .filter(t -> t.sourceAccountId().equals(accountId))
                .toList();
    }

    public static Map<UUID, Long> outgoingCounts(
            List<Transaction> transactions) {

        // TODO 1
        return transactions.stream()
                .collect(Collectors.groupingBy(
                        Transaction::sourceAccountId,
                        Collectors.counting()));
    }

    public static Map<Currency, BigDecimal> totalsByCurrency(
            List<Transaction> transactions) {

        // TODO 2
        return transactions.stream()
                .collect(Collectors.groupingBy(
                        t -> t.amount().currency(),
                        Collectors.mapping(
                                t -> t.amount().amount(),
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add)
                        )
                ));
    }

    public static List<Transaction> topTransfers(
            List<Transaction> transactions,
            Currency currency,
            int limit) {

        // TODO 3
        return transactions.stream()
                .filter(t -> t.amount().currency().equals(currency))
                .sorted(Comparator.comparing((Transaction t) -> t.amount().amount()).reversed())
                .limit(limit)
                .toList();
    }

    public static Set<UUID> duplicateTransactionIds(
            List<Transaction> transactions) {

        // TODO 4
        return transactions.stream()
                .collect(Collectors.groupingBy(
                        Transaction::id,
                        Collectors.counting()))
                .entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }
}
