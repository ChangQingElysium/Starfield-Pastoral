package com.stardew.craft.api.v1.fishing;

import com.stardew.craft.api.v1.internal.fishing.StardewFishingRuleConditionRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;

import java.util.Objects;

/** Ordered final eligibility overrides for data-driven fishing rules. */
public final class StardewFishingRuleConditions {
    private StardewFishingRuleConditions() {
    }

    public static void register(ResourceLocation id, int priority, Provider provider) {
        StardewFishingRuleConditionRegistry.register(id, priority, provider);
    }

    public enum Decision {
        PASS,
        ALLOW,
        DENY
    }

    @FunctionalInterface
    public interface Provider {
        Decision decide(Context context, boolean proposedResult);
    }

    public record Context(
            ServerPlayer player,
            ServerLevel level,
            BlockPos position,
            Holder<Biome> biome,
            StardewFishingRule rule,
            boolean usingMagicBait
    ) {
        public Context {
            player = Objects.requireNonNull(player, "player");
            level = Objects.requireNonNull(level, "level");
            position = Objects.requireNonNull(position, "position").immutable();
            biome = Objects.requireNonNull(biome, "biome");
            rule = Objects.requireNonNull(rule, "rule");
        }
    }
}
