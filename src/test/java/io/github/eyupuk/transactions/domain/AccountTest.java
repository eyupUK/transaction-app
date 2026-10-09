package io.github.eyupuk.transactions.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class AccountTest {

    @DisplayName("Deposit creates an updated account without changing the original")
    @Test
    void testDepositCreatesUpdatedAccount() {
        // Arrange
        Account originalAccount = new Account(UUID.randomUUID(), Money.of("100.00", "GBP"));
        Money depositAmount = Money.of("50.00", "GBP");
        // Act
        Account updatedAccount = originalAccount.deposit(depositAmount);

        // Assert
        assertNotEquals(originalAccount.balance(), updatedAccount.balance());
        assertEquals(Money.of("150.00", "GBP"), updatedAccount.balance());
        assertEquals(Money.of("100.00", "GBP"), originalAccount.balance());
    }

    @DisplayName("Withdrawal creates an updated account")
    @Test
    void testWithdrawalCreatesUpdatedAccount() {
        // Arrange
        Account originalAccount = new Account(UUID.randomUUID(), Money.of("100.00", "GBP"));
        Money withdrawalAmount = Money.of("30.00", "GBP");
        // Act
        Account updatedAccount = originalAccount.withdraw(withdrawalAmount);

        // Assert
        assertNotEquals(originalAccount.balance(), updatedAccount.balance());
        assertEquals(Money.of("70.00", "GBP"), updatedAccount.balance());
        assertEquals(Money.of("100.00", "GBP"), originalAccount.balance());
    }

    @DisplayName("Allows withdrawal of the exact available balance")
    @Test
    void testWithdrawalOfExactBalance() {
        // Arrange
        Account originalAccount = new Account(UUID.randomUUID(), Money.of("100.00", "GBP"));
        Money withdrawalAmount = Money.of("100.00", "GBP");
        // Act
        Account updatedAccount = originalAccount.withdraw(withdrawalAmount);

        // Assert
        assertEquals(Money.of("0.00", "GBP"), updatedAccount.balance());
    }

    @DisplayName("Rejects withdrawals exceeding the balance")
    @Test
    void testWithdrawalExceedingBalance() {
        // Arrange
        Account originalAccount = new Account(UUID.randomUUID(), Money.of("100.00", "GBP"));
        Money withdrawalAmount = Money.of("150.00", "GBP");
        // Act & Assert
        try {
            originalAccount.withdraw(withdrawalAmount);
        } catch (IllegalArgumentException e) {
            assertEquals("Insufficient balance: 100.00 GBP vs 150.00 GBP", e.getMessage());
        }
    }

    @DisplayName("Rejects zero and negative deposits")
    @Test
    void testZeroAndNegativeDeposits() {
        // Arrange
        Account originalAccount = new Account(UUID.randomUUID(), Money.of("100.00", "GBP"));
        Money zeroDeposit = Money.of("0.00", "GBP");
        Money negativeDeposit = Money.of("-50.00", "GBP");
        // Act & Assert
        try {
            originalAccount.deposit(zeroDeposit);
        } catch (IllegalArgumentException e) {
            assertEquals("Invalid deposit amount: 0.00 GBP", e.getMessage());
        }
        try {
            originalAccount.deposit(negativeDeposit);
        } catch (IllegalArgumentException e) {
            assertEquals("Invalid deposit amount: -50.00 GBP", e.getMessage());
        }
    }

    @DisplayName("Rejects currency mismatches and negative starting balances")
    @Test
    void testCurrencyMismatchAndNegativeStartingBalance() {
        // Arrange
        Account originalAccount = new Account(UUID.randomUUID(), Money.of("100.00", "GBP"));
        Money depositAmount = Money.of("50.00", "EUR");
        Money withdrawalAmount = Money.of("30.00", "EUR");
        // Act & Assert
        try {
            originalAccount.deposit(depositAmount);
        } catch (IllegalArgumentException e) {
            assertEquals("Currency mismatch: GBP vs EUR", e.getMessage());
        }
        try {
            originalAccount.withdraw(withdrawalAmount);
        } catch (IllegalArgumentException e) {
            assertEquals("Currency mismatch: GBP vs EUR", e.getMessage());
        }
        try {
            new Account(UUID.randomUUID(), Money.of("-100.00", "GBP"));
        } catch (IllegalArgumentException e) {
            assertEquals("Negative starting balance: -100.00 GBP", e.getMessage());
        }
    }
}
