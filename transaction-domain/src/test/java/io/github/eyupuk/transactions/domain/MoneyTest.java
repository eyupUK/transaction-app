package io.github.eyupuk.transactions.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

public class MoneyTest {


    @Test
    void normalizesMonetaryAmounts() {
        Money first = Money.of("10.0", "GBP");
        Money second = Money.of("10.00", "GBP");

        assertEquals(first, second);
        assertEquals(
                new BigDecimal("10.00"),
                first.amount()
        );
    }

    @Test
    void addTwoGbpValues() {
        Money first = Money.of("5.05", "GBP");
        Money second = Money.of("10.15", "GBP");

        Money result = first.add(second);
        assertEquals(Money.of("15.20", "GBP"), result);
    }

    @Test
    void subtractTwoGbpValues() {
        Money first = Money.of("15.20", "GBP");
        Money second = Money.of("10.15", "GBP");

        Money result = first.subtract(second);
        assertEquals(Money.of("5.05", "GBP"), result);
    }

    @Test
    void rejectAmountRequiringRounding(){
        String errMsg = assertThrows(IllegalArgumentException.class, () -> {
            Money.of("10.123", "GBP");
        }).getMessage();

        assertTrue(errMsg.contains("Amount requires rounding"));
    }

    @Test
    void rejectsUnsupportedCurrencies() {
        assertTrue(assertThrows(IllegalArgumentException.class, () -> {
            Money.of("10.00", "JPY");
        }).getMessage().contains("Unsupported currency"));
    }

    // Rejects addition of different currencies
    @Test
    void rejectsAdditionOfDifferentCurrencies() {
        Money first = Money.of("10.00", "GBP");
        Money second = Money.of("10.00", "EUR");
        assertTrue(assertThrows(IllegalArgumentException.class, () -> {
            first.add(second);
        }).getMessage().contains("Currency mismatch"));
    }

    // Identifies positive and negative amounts
    @Test
    void identifiesPositiveAndNegativeAmounts() {
        Money positive = Money.of("10.00", "GBP");
        Money negative = Money.of("-10.00", "GBP");

        assertTrue(positive.isPositive());
        assertFalse(positive.isNegative());
        assertTrue(negative.isNegative());
        assertFalse(negative.isPositive());
    }
}
