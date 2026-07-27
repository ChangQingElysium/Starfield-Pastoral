package com.stardew.craft.api.v1.progress;

import com.stardew.craft.api.v1.internal.progress.StardewProgressRequirementRegistry;
import com.stardew.craft.api.v1.requirement.StardewRequirementReport;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

/**
 * Read-only, server-authoritative explanations for common progress
 * operations. The report never performs the operation or reserves state.
 */
public final class StardewProgressRequirements {
    private StardewProgressRequirements() {
    }

    public static StardewRequirementReport requirements(
            ServerPlayer player,
            StardewProgressKey key,
            StardewProgressOperation operation
    ) {
        return StardewProgressRequirementRegistry.requirements(
                Objects.requireNonNull(player, "player"),
                Objects.requireNonNull(key, "key"),
                Objects.requireNonNull(operation, "operation"));
    }
}
