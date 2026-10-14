
package io.github.eyupuk.transactions.performance;

import io.github.eyupuk.transactions.domain.Transaction;

import java.util.*;

public final class BoundedTransactionHistory {

    private final int capacity;
    private final Deque<Transaction> history =
            new ArrayDeque<>();

    public BoundedTransactionHistory(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException(
                    "Capacity must be positive"
            );
        }

        this.capacity = capacity;
    }

    public synchronized void record(Transaction transaction) {
        Objects.requireNonNull(transaction, "transaction");

        if (history.size() == capacity) {
            history.removeFirst();
        }
        history.addLast(transaction);
    }

    public synchronized List<Transaction> snapshot() {
        // TODO 3: Return an independent,
        return List.copyOf(history);
    }
}
