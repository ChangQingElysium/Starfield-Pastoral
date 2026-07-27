package com.stardew.craft.api.v1.internal.fishing;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.internal.extension.OrderedExtensionRegistry;
import com.stardew.craft.api.v1.fishing.StardewFishingRule;
import com.stardew.craft.api.v1.fishing.StardewFishingRuleConditions;
import com.stardew.craft.fishing.data.SpawnFishRule;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;

import java.util.List;

/** Internal fishing rule-condition dispatch and DTO conversion. */
public final class StardewFishingRuleConditionRegistry {
    private static final OrderedExtensionRegistry<
            StardewFishingRuleConditions.Provider> PROVIDERS =
            new OrderedExtensionRegistry<>(
                    ResourceLocation.fromNamespaceAndPath(
                            StardewCraft.MODID, "fishing/rule_condition"));

    private StardewFishingRuleConditionRegistry() {
    }

    public static void register(
            ResourceLocation id,
            int priority,
            StardewFishingRuleConditions.Provider provider
    ) {
        PROVIDERS.register(id, priority, provider);
    }

    public static boolean evaluate(
            ServerPlayer player,
            ServerLevel level,
            BlockPos position,
            Holder<Biome> biome,
            SpawnFishRule rule,
            boolean usingMagicBait,
            boolean proposed
    ) {
        StardewFishingRule publicRule = toPublic(rule);
        StardewFishingRuleConditions.Context context =
                new StardewFishingRuleConditions.Context(
                        player,
                        level,
                        position,
                        biome,
                        publicRule,
                        usingMagicBait);
        for (var registered : PROVIDERS.entries()) {
            try {
                StardewFishingRuleConditions.Decision decision =
                        PROVIDERS.invoke(
                                registered,
                                provider -> provider.decide(
                                        context, proposed));
                if (decision == StardewFishingRuleConditions.Decision.ALLOW) {
                    return true;
                }
                if (decision == StardewFishingRuleConditions.Decision.DENY) {
                    return false;
                }
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error(
                        "Fishing rule-condition provider {} failed for rule {}",
                        registered.id(), rule.id(), exception);
            }
        }
        return proposed;
    }

    private static StardewFishingRule toPublic(SpawnFishRule rule) {
        List<StardewFishingRule.TimeRange> timeRanges =
                rule.timeRanges() == null ? List.of() : rule.timeRanges().stream()
                        .filter(range -> range != null && range.length >= 2)
                        .map(range -> new StardewFishingRule.TimeRange(
                                range[0], range[1]))
                        .toList();
        return new StardewFishingRule(
                rule.id(),
                rule.itemId(),
                safe(rule.biomes()),
                safe(rule.biomeTags()),
                safe(rule.seasons()),
                rule.weather() == null ? "" : rule.weather(),
                timeRanges,
                Math.max(0, rule.minFishingLevel()),
                Math.max(0, rule.minDistanceFromShore()),
                rule.maxDistanceFromShore(),
                rule.requireMagicBait(),
                Math.max(-1, rule.catchLimit()),
                rule.condition() == null ? "" : rule.condition());
    }

    private static List<String> safe(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
