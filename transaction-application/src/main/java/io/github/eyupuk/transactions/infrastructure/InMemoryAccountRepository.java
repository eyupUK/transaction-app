
package io.github.eyupuk.transactions.infrastructure;

import io.github.eyupuk.transactions.application.AccountRepository;
import io.github.eyupuk.transactions.domain.Account;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

public final class InMemoryAccountRepository
        implements AccountRepository {

    private final ConcurrentHashMap<UUID, Account> accounts =
            new ConcurrentHashMap<>();

    public InMemoryAccountRepository(
            Collection<Account> initialAccounts) {

        Objects.requireNonNull(
                initialAccounts, "initialAccounts"
        );

        for (Account account : initialAccounts) {
            Objects.requireNonNull(account, "account");

            Account previous =
                    accounts.putIfAbsent(account.id(), account);

            if (previous != null) {
                throw new IllegalArgumentException(
                        "Duplicate account ID: " + account.id()
                );
            }
        }
    }

    @Override
    public Optional<Account> findById(UUID accountId) {
        // 1: Validate ID.
        Objects.requireNonNull(accountId, "accountId");

        // 2: Return Optional containing an account
        //         when the key exists.
        return Optional.ofNullable(accounts.get(accountId));
    }

    @Override
    public Account update(
            UUID accountId,
            UnaryOperator<Account> change) {

        // 3: Validate arguments.
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(change, "change");

        // 4: Apply the update atomically.
        return accounts.compute(accountId, (id, account) -> {
            // 5: Reject an unknown account ID.
            if (account == null) {
                throw new NoSuchElementException("Unknown account ID: " + accountId);
            }

            // 6: Apply the change.
            Account updated = change.apply(account);

            // 7: Reject a null updated account.
            if (updated == null) {
                throw new IllegalStateException("Updated account cannot be null");
            }

            // 8: Preserve the original account ID.
            if (!updated.id().equals(accountId)) {
                throw new IllegalStateException("Updated account ID does not match original ID");
            }

            // 9: Return the updated account.
            return updated;
        });
    }
}
