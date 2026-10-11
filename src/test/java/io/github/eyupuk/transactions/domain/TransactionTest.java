package io.github.eyupuk.transactions.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class TransactionTest {
    @DisplayName("Creates a valid transaction")
    @Test
    void testCreateValidTransaction() {
        UUID id = UUID.randomUUID();
        UUID sourceAccountId = UUID.randomUUID();
        UUID destinationAccountId = UUID.randomUUID();
        Money amount = Money.of("100.00", "GBP");
        Instant createdAt = Instant.now();
        Transaction transaction = new Transaction(id, sourceAccountId, destinationAccountId, amount, createdAt);
        assertEquals(sourceAccountId, transaction.sourceAccountId());
        assertEquals(destinationAccountId, transaction.destinationAccountId());
        assertEquals(amount, transaction.amount());
    }

    @DisplayName("Rejects equal source and destination IDs")
    @Test
    void testRejectsEqualSourceAndDestinationIds() {
        UUID accountId = UUID.randomUUID();

        IllegalArgumentException exc = assertThrows(IllegalArgumentException.class, () -> {
            new Transaction(UUID.randomUUID(), accountId, accountId, Money.of("100.00", "GBP"), Instant.now());
        });

        assertEquals("Cannot transfer to the same account: " + accountId, exc.getMessage());
    }

    @DisplayName("Rejects zero or negative transaction amounts")
    @Test
    void testRejectsZeroOrNegativeTransactionAmounts() {
        IllegalArgumentException exc1 = assertThrows(IllegalArgumentException.class, () -> {
            new Transaction(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Money.of("0.00", "GBP"), Instant.now());
        });
        assertEquals("Invalid transaction amount: 0.00", exc1.getMessage());

        IllegalArgumentException exc2 = assertThrows(IllegalArgumentException.class, () -> {
            new Transaction(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Money.of("-10.00", "GBP"), Instant.now());
        });
        assertEquals("Invalid transaction amount: -10.00", exc2.getMessage());
    }

    @DisplayName("Rejects null values for required fields")
    @ParameterizedTest
    @ValueSource(strings = {"id", "sourceAccountId", "destinationAccountId", "amount", "createdAt"})
    void testRejectsNullValuesForRequiredFields(String fieldName) {
        UUID id = UUID.randomUUID();
        UUID sourceAccountId = UUID.randomUUID();
        UUID destinationAccountId = UUID.randomUUID();
        Money amount = Money.of("100.00", "GBP");
        Instant createdAt = Instant.now();

        switch (fieldName) {
            case "id" -> id = null;
            case "sourceAccountId" -> sourceAccountId = null;
            case "destinationAccountId" -> destinationAccountId = null;
            case "amount" -> amount = null;
            case "createdAt" -> createdAt = null;
        }

        UUID finalId = id;
        UUID finalSourceAccountId = sourceAccountId;
        UUID finalDestinationAccountId = destinationAccountId;
        Money finalAmount = amount;
        Instant finalCreatedAt = createdAt;
        assertThrows(NullPointerException.class, () -> {
            new Transaction(finalId, finalSourceAccountId, finalDestinationAccountId, finalAmount, finalCreatedAt);
        });
    }
}
