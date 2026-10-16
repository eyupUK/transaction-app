
package io.github.eyupuk.transactions.application;

import io.github.eyupuk.transactions.domain.Account;
import io.github.eyupuk.transactions.domain.Money;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

public final class AccountService {

    private final AccountRepository repository;

    public AccountService(AccountRepository repository) {
        this.repository = Objects.requireNonNull(
                repository, "repository"
        );
    }

    public Money getBalance(UUID accountId) {
        Objects.requireNonNull(accountId, "accountId");

        return repository.findById(accountId)
                .orElseThrow(
                        () -> new NoSuchElementException(
                                "Account not found: " + accountId
                        )
                )
                .balance();
    }

    public Account deposit(UUID accountId, Money amount) {
        // 1: Validate required arguments.
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(amount, "amount");

        // 2: Use repository.update().
        // 3: Delegate deposit rules to Account.
        return repository.update(accountId, account -> account.deposit(amount));
    }

    public Account withdraw(UUID accountId, Money amount) {
        // 4: Validate required arguments.
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(amount, "amount");

        // 5: Use repository.update().
        // 6: Delegate withdrawal rules to Account.
        return repository.update(accountId, account -> account.withdraw(amount));
    }
}
