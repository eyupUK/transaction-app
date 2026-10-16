
package io.github.eyupuk.transactions.application;

import io.github.eyupuk.transactions.domain.Account;

import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

public interface AccountRepository {

    Optional<Account> findById(UUID accountId);

    Account update(
            UUID accountId,
            UnaryOperator<Account> change
    );
}
