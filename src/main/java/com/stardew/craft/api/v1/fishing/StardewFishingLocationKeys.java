package com.stardew.craft.api.v1.fishing;

import com.stardew.craft.api.v1.internal.fishing.StardewFishingLocationKeyRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

/** Ordered extensions to the fishing-pool lookup keys resolved for a world position. */
public final class StardewFishingLocationKeys {
    private StardewFishingLocationKeys() {
    }

    public static void register(ResourceLocation id, int priority, Provider provider) {
        StardewFishingLocationKeyRegistry.register(id, priority, provider);
    }

    @FunctionalInterface
    public interface Provider {
        /** Returns replacement keys, or {@code null} to keep the proposed list. */
        List<String> resolve(Context context);
    }

    public record Context(
            ServerLevel level,
            Holder<Biome> biome,
            @Nullable BlockPos position,
            List<String> proposedKeys
    ) {
        public Context {
            level = Objects.requireNonNull(level, "level");
            biome = Objects.requireNonNull(biome, "biome");
            position = position == null ? null : position.immutable();
            proposedKeys = List.copyOf(Objects.requireNonNull(
                    proposedKeys, "proposedKeys"));
        }
    }
}
