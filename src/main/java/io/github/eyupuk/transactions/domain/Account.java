
package io.github.eyupuk.transactions.domain;

import java.util.UUID;
import java.util.Objects;

public record Account(UUID id, Money balance) {

    public Account {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(balance, "balance");

        // TODO 1: Reject negative starting balances.
        if (balance.isNegative()) {
            throw new IllegalArgumentException("Negative starting balance: " + balance.amount() + " " + balance.currency());
        }
    }

    public Account deposit(Money amount) {
        // TODO 2: Reject zero or negative deposits.
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("Invalid deposit amount: " + amount.amount() + " " + amount.currency());
        }

        // TODO 3: Validate matching currencies.
        if (!this.balance.currency().equals(amount.currency())) {
            throw new IllegalArgumentException("Currency mismatch: " + this.balance.currency() + " vs " + amount.currency());
        }

        // TODO 4: Return a NEW Account.
        return new Account(this.id, this.balance.add(amount));
    }

    public Account withdraw(Money amount) {
        // TODO 5: Reject zero or negative withdrawals.
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("Invalid withdrawal amount: " + amount.amount() + " " + amount.currency());
        }

        // TODO 6: Validate matching currencies.
        if (!this.balance.currency().equals(amount.currency())) {
            throw new IllegalArgumentException("Currency mismatch: " + this.balance.currency() + " vs " + amount.currency());
        }

        // TODO 7: Reject insufficient funds.
        if (this.balance.subtract(amount).isNegative()) {
            throw new IllegalArgumentException("Insufficient balance: " + this.balance.amount() + " " + this.balance.currency() + " vs " + amount.amount() + " " + amount.currency());
        }

        // TODO 8: Return a NEW Account.
        return new Account(this.id, this.balance.subtract(amount));
    }
}
