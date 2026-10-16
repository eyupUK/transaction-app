package io.github.eyupuk.transactions.application;

import io.github.eyupuk.transactions.domain.Account;
import io.github.eyupuk.transactions.domain.Money;
import io.github.eyupuk.transactions.infrastructure.InMemoryAccountRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

public class AccountServiceTest {


    @DisplayName("Reject a null repository")
    @Test
    void rejectsNullRepository() {
        assertThrows(NullPointerException.class, () -> new AccountService(null));
    }

    @DisplayName("Return the correct balance for an existing account")
    @Test
    void returnsBalanceForExistingAccount() {
        Account opening = new Account(
                UUID.randomUUID(),
                Money.of("100.00", "GBP")
        );

        AccountRepository repository =
                new InMemoryAccountRepository(List.of(opening));

        AccountService service = new AccountService(repository);

        Money balance = service.getBalance(opening.id());

        assertEquals(Money.of("100.00", "GBP"), balance);
    }

    @DisplayName("Throw for an unknown UUID")
    @Test
    void throwsForUnknownUUID() {
        AccountRepository repository =
                new InMemoryAccountRepository(List.of());

        AccountService service = new AccountService(repository);

        assertThrows(NoSuchElementException.class, () -> service.getBalance(UUID.randomUUID()));
    }

    @DisplayName("Correctly update and return the account after deposit")
    @Test
    void correctlyUpdateAndReturnAccount() {
        Account opening = new Account(
                UUID.randomUUID(),
                Money.of("100.00", "GBP")
        );

        AccountRepository repository =
                new InMemoryAccountRepository(List.of(opening));

        AccountService service = new AccountService(repository);

        Money deposit = Money.of("150.00", "GBP");
        Account updatedAccount = service.deposit(opening.id(), deposit);
        assertEquals(new Account(opening.id(), Money.of("250.00", "GBP")), updatedAccount);
    }

    @DisplayName("Correctly update and return the account after withdrawal")
    @Test
    void correctlyUpdateAndReturnAccountAfterWithdrawal() {
        Account opening = new Account(
                UUID.randomUUID(),
                Money.of("200.00", "GBP")
        );

        AccountRepository repository =
                new InMemoryAccountRepository(List.of(opening));

        AccountService service = new AccountService(repository);

        Money withdrawal = Money.of("50.00", "GBP");
        Account updatedAccount = service.withdraw(opening.id(), withdrawal);
        assertEquals(new Account(opening.id(), Money.of("150.00", "GBP")), updatedAccount);
    }

    @DisplayName("Reject the withdrawal and preserve the balance when insufficient funds")
    @Test
    void rejectWithdrawalWhenInsufficientFunds() {
        Account opening = new Account(
                UUID.randomUUID(),
                Money.of("100.00", "GBP")
        );

        AccountRepository repository =
                new InMemoryAccountRepository(List.of(opening));

        AccountService service = new AccountService(repository);

        Money withdrawal = Money.of("150.00", "GBP");
        assertThrows(IllegalStateException.class, () -> service.withdraw(opening.id(), withdrawal));

        // Ensure the balance remains unchanged
        Money balanceAfterFailedWithdrawal = service.getBalance(opening.id());
        assertEquals(Money.of("100.00", "GBP"), balanceAfterFailedWithdrawal);
    }

    @DisplayName("Reject a mismatched currency without changing state")
    @Test
    void rejectMismatchedCurrency() {
        Account opening = new Account(
                UUID.randomUUID(),
                Money.of("100.00", "GBP")
        );

        AccountRepository repository =
                new InMemoryAccountRepository(List.of(opening));

        AccountService service = new AccountService(repository);


        assertThrows(IllegalArgumentException.class, () -> {
            Money withdrawal = Money.of("150.00", "USD");
            service.withdraw(opening.id(), withdrawal);
        });

        // Ensure the balance remains unchanged
        Money balanceAfterFailedWithdrawal = service.getBalance(opening.id());
        assertEquals(Money.of("100.00", "GBP"), balanceAfterFailedWithdrawal);
    }

}
