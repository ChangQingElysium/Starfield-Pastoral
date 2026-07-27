package com.stardew.craft.api.v1.economy;

import com.stardew.craft.api.v1.internal.economy.StardewCostTransactionService;
import com.stardew.craft.api.v1.requirement.StardewRequirementReport;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

/** Server-authoritative composite cost and refund facade. */
public final class StardewCosts {
    private StardewCosts() {
    }

    public static StardewPaymentResult pay(
            ServerPlayer player,
            StardewCost cost
    ) {
        return StardewCostTransactionService.pay(player, cost);
    }

    /**
     * Evaluates every normalized currency and item component without
     * withdrawing anything.
     *
     * <p>This report is a read-only affordability snapshot. The actual
     * operation must still call {@link #pay(ServerPlayer, StardewCost)}.
     */
    public static StardewRequirementReport requirements(
            ServerPlayer player,
            StardewCost cost
    ) {
        return StardewCostTransactionService.requirements(
                Objects.requireNonNull(player, "player"),
                Objects.requireNonNull(cost, "cost"));
    }
}
