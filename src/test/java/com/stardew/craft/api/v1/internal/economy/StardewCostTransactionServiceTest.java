package com.stardew.craft.api.v1.internal.economy;

import com.stardew.craft.api.v1.economy.StardewPaymentReceipt;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StardewCostTransactionServiceTest {
    @Test
    void failedPreflightDoesNotMutateAnyAccount() {
        FakeOperation first =
                new FakeOperation("first", true, true);
        FakeOperation second =
                new FakeOperation("second", false, true);

        var result = StardewCostTransactionService
                .executeOperations(List.of(first, second));

        assertFalse(result.success());
        assertEquals("insufficient_second", result.failureReason());
        assertEquals(0, first.withdrawals);
        assertEquals(0, first.refunds);
    }

    @Test
    void laterWithdrawalFailureRollsBackEarlierComponents() {
        ArrayList<String> order = new ArrayList<>();
        FakeOperation first =
                new FakeOperation("first", true, true, order);
        FakeOperation second =
                new FakeOperation("second", true, false, order);

        var result = StardewCostTransactionService
                .executeOperations(List.of(first, second));

        assertFalse(result.success());
        assertEquals(List.of(
                "withdraw:first",
                "withdraw:second",
                "refund:first"), order);
        assertEquals(1, first.refunds);
    }

    @Test
    void successfulReceiptRefundsOnceInReverseOrder() {
        ArrayList<String> order = new ArrayList<>();
        FakeOperation first =
                new FakeOperation("first", true, true, order);
        FakeOperation second =
                new FakeOperation("second", true, true, order);
        var result = StardewCostTransactionService
                .executeOperations(List.of(first, second));
        StardewPaymentReceipt receipt =
                new StardewPaymentReceipt(result.refund());

        assertTrue(result.success());
        assertTrue(receipt.refund());
        assertFalse(receipt.refund());
        assertTrue(receipt.isSettled());
        assertTrue(receipt.isRefunded());
        assertEquals(List.of(
                "withdraw:first",
                "withdraw:second",
                "refund:second",
                "refund:first"), order);
    }

    private static final class FakeOperation
            implements StardewCostTransactionService.PaymentOperation {
        private final String id;
        private final boolean affordable;
        private final boolean withdrawalSucceeds;
        private final List<String> order;
        private int withdrawals;
        private int refunds;

        private FakeOperation(
                String id,
                boolean affordable,
                boolean withdrawalSucceeds
        ) {
            this(id, affordable, withdrawalSucceeds,
                    new ArrayList<>());
        }

        private FakeOperation(
                String id,
                boolean affordable,
                boolean withdrawalSucceeds,
                List<String> order
        ) {
            this.id = id;
            this.affordable = affordable;
            this.withdrawalSucceeds = withdrawalSucceeds;
            this.order = order;
        }

        @Override
        public boolean canPay() {
            return affordable;
        }

        @Override
        public boolean withdraw() {
            withdrawals++;
            order.add("withdraw:" + id);
            return withdrawalSucceeds;
        }

        @Override
        public boolean refund() {
            refunds++;
            order.add("refund:" + id);
            return true;
        }

        @Override
        public String failureReason() {
            return id;
        }
    }
}
