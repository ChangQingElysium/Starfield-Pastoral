package com.stardew.craft.api.v1.farm;

import com.stardew.craft.api.v1.internal.farm.StardewFarmDebrisPlacementRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Objects;

/** Ordered replacement providers for blocks chosen by the core farm-debris daily pass. */
public final class StardewFarmDebrisPlacements {
    private StardewFarmDebrisPlacements() {
    }

    public static void register(
            ResourceLocation id,
            int priority,
            Provider provider
    ) {
        StardewFarmDebrisPlacementRegistry.register(id, priority, provider);
    }

    public enum Stage {
        YOUNG_TREE,
        DEBRIS
    }

    @FunctionalInterface
    public interface Provider {
        BlockState resolve(Context context);
    }

    public record Context(
            ServerLevel level,
            StardewFarmSnapshot farm,
            BlockPos position,
            BlockState proposedState,
            Stage stage,
            RandomSource random
    ) {
        public Context {
            level = Objects.requireNonNull(level, "level");
            farm = Objects.requireNonNull(farm, "farm");
            position = Objects.requireNonNull(position, "position").immutable();
            proposedState = Objects.requireNonNull(proposedState, "proposedState");
            stage = Objects.requireNonNull(stage, "stage");
            random = Objects.requireNonNull(random, "random");
        }
    }
}
