
package io.github.eyupuk.transactions.domain;

import java.time.Instant;
import java.util.UUID;
import java.util.Objects;

public record Transaction(
        UUID id,
        UUID sourceAccountId,
        UUID destinationAccountId,
        Money amount,
        Instant createdAt
) {

    public Transaction {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(
                sourceAccountId, "sourceAccountId"
        );
        Objects.requireNonNull(
                destinationAccountId, "destinationAccountId"
        );
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(createdAt, "createdAt");

        // TODO 1: Prevent transfers to the same account.
        if (sourceAccountId.equals(destinationAccountId)) {
            throw new IllegalArgumentException("Cannot transfer to the same account: " + sourceAccountId);
        }

        // TODO 2: Reject zero or negative transaction amounts.
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("Invalid transaction amount: " + amount.amount());
        }
    }
}
