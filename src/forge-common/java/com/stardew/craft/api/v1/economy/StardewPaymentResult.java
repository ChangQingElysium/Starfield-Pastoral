package com.stardew.craft.api.v1.economy;

import java.util.Objects;
import java.util.Optional;

/** Result of attempting one server-authoritative composite payment. */
public record StardewPaymentResult(
        boolean success,
        String failureReason,
        Optional<StardewPaymentReceipt> receipt
) {
    public StardewPaymentResult {
        Objects.requireNonNull(failureReason, "failureReason");
        receipt = Objects.requireNonNull(receipt, "receipt");
        if (success != receipt.isPresent()) {
            throw new IllegalArgumentException(
                    "successful payments require a receipt");
        }
    }

    public static StardewPaymentResult paid(
            StardewPaymentReceipt receipt
    ) {
        return new StardewPaymentResult(
                true, "", Optional.of(receipt));
    }

    public static StardewPaymentResult failed(String reason) {
        return new StardewPaymentResult(
                false, Objects.requireNonNull(reason, "reason"),
                Optional.empty());
    }
}
