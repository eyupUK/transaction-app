
package io.github.eyupuk.transactions.concurrency;

import io.github.eyupuk.transactions.domain.Account;
import java.util.Objects;

public final class UnsafeAccountStore {

    private volatile Account current;

    public UnsafeAccountStore(Account openingAccount) {
        this.current = Objects.requireNonNull(
                openingAccount, "openingAccount"
        );
    }

    public Account snapshot() {
        return current;
    }

    public void replace(Account updated) {
        current = Objects.requireNonNull(updated);
    }
}
