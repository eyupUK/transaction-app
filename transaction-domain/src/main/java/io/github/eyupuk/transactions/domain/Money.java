
package io.github.eyupuk.transactions.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

public record Money(BigDecimal amount, Currency currency) {

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");

        // 1: Support GBP and EUR only.
        if (!currency.getCurrencyCode().equals("GBP") && !currency.getCurrencyCode().equals("EUR")) {
            throw new IllegalArgumentException("Unsupported currency: " + currency);
        }


        // 2: Normalize the amount to scale 2.
        try {
            amount = amount.setScale(
                    2, RoundingMode.UNNECESSARY
            );
        }
        // 3: Reject amounts requiring rounding.
        catch (ArithmeticException e) {
            throw new IllegalArgumentException(
                    "Amount requires rounding: " + amount, e
            );
        }
    }

    public static Money of(
            String amount,
            String currencyCode) {

        return new Money(
                new BigDecimal(amount),
                Currency.getInstance(currencyCode)
        );
    }

    public Money add(Money other) {
        // 4: Validate matching currencies.
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException("Currency mismatch: " + this.currency + " vs " + other.currency);
        }
        // 5: Return a new Money object.
        return new Money(this.amount.add(other.amount), this.currency);
    }

    public Money subtract(Money other) {
        // 6: Validate matching currencies.
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException("Currency mismatch: " + this.currency + " vs " + other.currency);
        }

        // 7: Return a new Money object.
        return new Money(this.amount.subtract(other.amount), this.currency);
    }

    public boolean isPositive() {
        // 8: Return true when amount > 0.
        return this.amount.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isNegative() {
        // 9: Return true when amount < 0.
    return this.amount.compareTo(BigDecimal.ZERO) < 0;
    }

    public static Money zero(String currencyCode) {
        // Return zero monetary value for the supplied currency.
        // Reuse existing validation rather than duplicating it.
        return new Money(BigDecimal.ZERO.setScale(2), Currency.getInstance(currencyCode));
    }
}
