package io.github.eyupuk.transactions.domain;

/**
 * Decides whether a requested transfer is within a configured maximum.
 *
 * <p>Money is represented in whole pence for this introductory exercise.
 * A richer Money type will be introduced on Day 2.</p>
 *
 * <p>DAY 1 ASSIGNMENT: implement constructor validation and allows().</p>
 * Maximum	Requested transfer	Expected behaviour
 * 10,000p	1p	Allowed
 * 10,000p	9,999p	Allowed
 * 10,000p	10,000p	Allowed
 * 10,000p	10,001p	Not allowed
 * 10,000p	0p	Throw IllegalArgumentException
 * 10,000p	−1p	Throw IllegalArgumentException
 * 0p	Constructor call	Throw IllegalArgumentException
 * −1p	Constructor call	Throw IllegalArgumentException
 */
public final class TransferPolicy {
    private final long maximumAmountPence;

    public TransferPolicy(long maximumAmountPence) {
        if (maximumAmountPence <= 0) throw new IllegalArgumentException("Max amount should not be zero or negative!");
        this.maximumAmountPence = maximumAmountPence;
    }

    public boolean allows(long amountPence) {
        // TODO 2: Throw IllegalArgumentException for non-positive amounts.
        if (amountPence <= 0) throw new IllegalArgumentException("Amount should not be zero or negative!");
        // TODO 3: Return true when amount <= maximumAmountPence;
        //         return false when amount exceeds the maximum.
        return amountPence <= maximumAmountPence;
    }
}
