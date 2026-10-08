package io.github.eyupuk.transactions.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class TransferPolicyTest {

    /*
    Maximum	Requested transfer	Expected behaviour
    +10,000p	1p	true
    +10,000p	9,999p	true
    +10,000p	10,000p	true
    +10,000p	10,001p	false
    +10,000p	0p	Throw IllegalArgumentException
    +10,000p	−1p	Throw IllegalArgumentException
    +0p	Constructor call	Throw IllegalArgumentException
    +−1p	Constructor call	Throw IllegalArgumentException
     */

    @ParameterizedTest
    @ValueSource(longs = {1L, 9999L, 10000L})
    void allowsTransferAtPositiveCasesAmount(long amount) {
        TransferPolicy policy = new TransferPolicy(10000L);

        boolean result = policy.allows(amount);

        assertTrue(result);
    }

    @ParameterizedTest
    @ValueSource(longs = {-1L, 0L})
    void allowsTransferAtNegativeCasesAmount(long amount) {
        TransferPolicy policy = new TransferPolicy(10000L);

        String errMsg = assertThrows( IllegalArgumentException.class, () -> policy.allows(amount)).getMessage();
        assertEquals("Amount should not be zero or negative!", errMsg);
    }
    @ParameterizedTest
    @ValueSource(longs = {10001L})
    void rejectsTransferExceedingConfiguredMaximum(long amount) {
        TransferPolicy policy = new TransferPolicy(10000L);

        boolean result = policy.allows(amount);

        assertFalse(result);
    }

    @ParameterizedTest
    @ValueSource(longs = {-1L, 0L})
    void allowsTransferAtNegativeCasesMaxAmount(long maxAmount) {
        String errMsg = assertThrows( IllegalArgumentException.class, () -> new TransferPolicy(maxAmount)).getMessage();
        assertEquals("Max amount should not be zero or negative!", errMsg);
    }


}
