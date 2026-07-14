package com.stardew.craft.api.v1.museum;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.stardew.craft.api.v1.action.StardewAction;
import com.stardew.craft.api.v1.action.StardewActions;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Reloadable museum milestone reward. */
public record StardewMuseumRewardDefinition(
        String condition,
        int threshold,
        List<ResourceLocation> requiredItems,
        List<StardewAction> rewards
) {
    private static final Codec<String> CONDITION_CODEC = Codec.STRING.validate(value -> switch (value) {
        case "total_count", "mineral_count", "artifact_count", "specific_items" -> DataResult.success(value);
        default -> DataResult.error(() -> "unknown museum condition " + value);
    });

    public static final Codec<StardewMuseumRewardDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            CONDITION_CODEC.fieldOf("condition").forGetter(StardewMuseumRewardDefinition::condition),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("threshold", 0)
                    .forGetter(StardewMuseumRewardDefinition::threshold),
            ResourceLocation.CODEC.listOf().optionalFieldOf("required_items", List.of())
                    .forGetter(StardewMuseumRewardDefinition::requiredItems),
            StardewActions.CODEC.listOf().optionalFieldOf("rewards", List.of())
                    .forGetter(StardewMuseumRewardDefinition::rewards)
    ).apply(instance, StardewMuseumRewardDefinition::new));

    public StardewMuseumRewardDefinition {
        requiredItems = List.copyOf(requiredItems == null ? List.of() : requiredItems);
        rewards = List.copyOf(rewards == null ? List.of() : rewards);
        if ("specific_items".equals(condition) && requiredItems.isEmpty()) {
            throw new IllegalArgumentException("specific_items reward needs required_items");
        }
    }
}
