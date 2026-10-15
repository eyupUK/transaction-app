
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
        Account updated = current.deposit(amount);
        current = updated;
        return updated;
    }

    public synchronized Account withdraw(Money amount) {
        Account updated = current.withdraw(amount);
        current = updated;
        return updated;
    }


}
