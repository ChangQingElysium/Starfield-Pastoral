package com.stardew.craft.api.v1.economy;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/**
 * A successful payment that can be refunded at most once.
 *
 * <p>Callers should validate the reward before paying where possible. The receipt exists for
 * failures that can only be discovered after resources have been reserved.
 */
public final class StardewPaymentReceipt {
    private final BooleanSupplier refund;
    private final AtomicBoolean refundAttempted =
            new AtomicBoolean();
    private volatile boolean refunded;

    public StardewPaymentReceipt(BooleanSupplier refund) {
        this.refund = Objects.requireNonNull(refund, "refund");
    }

    public boolean refund() {
        if (!refundAttempted.compareAndSet(false, true)) {
            return false;
        }
        refunded = refund.getAsBoolean();
        return refunded;
    }

    public boolean isRefunded() {
        return refunded;
    }

    public boolean isSettled() {
        return refundAttempted.get();
    }
}
