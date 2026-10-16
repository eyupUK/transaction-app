
package io.github.eyupuk.transactions;

import io.github.eyupuk.transactions.application.AccountRepository;
import io.github.eyupuk.transactions.application.AccountService;
import io.github.eyupuk.transactions.infrastructure.InMemoryAccountRepository;
import io.github.eyupuk.transactions.domain.Account;
import io.github.eyupuk.transactions.domain.Money;

import java.util.List;
import java.util.UUID;

public final class BackendDemo {

    public static void main(String[] args) {

        Account opening = new Account(
                UUID.randomUUID(),
                Money.of("100.00", "GBP")
        );

        AccountRepository repository =
                new InMemoryAccountRepository(List.of(opening));

        AccountService service =
                new AccountService(repository);

        // Deposit £25 using AccountService.
        service.deposit(opening.id(), Money.of("25.00", "GBP"));

        // Print the updated balance.
        Money updatedBalance = service.getBalance(opening.id());
        System.out.println("Updated balance: " + updatedBalance.amount() + " " + updatedBalance.currency());

        // Withdraw £40.
        service.withdraw(opening.id(), Money.of("40.00", "GBP"));

        // Print the final balance.
        Money finalBalance = service.getBalance(opening.id());
        System.out.println("Final balance: " + finalBalance.amount() + " " + finalBalance.currency());
    }
}
