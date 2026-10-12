
package io.github.eyupuk.transactions.concurrency;

import io.github.eyupuk.transactions.domain.Account;
import io.github.eyupuk.transactions.domain.Money;

import java.util.Objects;

public final class SynchronizedAccountProcessor {

    private Account current;

    public SynchronizedAccountProcessor(Account openingAccount) {
        this.current = Objects.requireNonNull(
                openingAccount, "openingAccount"
        );
    }

    public synchronized Account snapshot() {
        return current;
    }

    public synchronized Account deposit(Money amount) {
        // TODO 1: Derive a new Account from current.
        Account updated = current.deposit(amount);

        // TODO 2: Replace current only after success.
        if (updated.balance().amount().compareTo(current.balance().amount()) > 0) {
            current = updated;
        }

        // TODO 3: Return the updated account.
        return current;
    }

    public synchronized Account withdraw(Money amount) {
        // TODO 4: Use the latest current Account.
        Account updated = current.withdraw(amount);

        // TODO 5: Apply the existing domain rules.

        // TODO 6: Store the updated Account.
        if (updated.balance().amount().compareTo(current.balance().amount()) < 0) {
            current = updated;
        } else {
            throw new IllegalArgumentException("Withdrawal amount exceeds current balance.");
        }

        // TODO 7: Return the updated account.
        return current;
    }

}
