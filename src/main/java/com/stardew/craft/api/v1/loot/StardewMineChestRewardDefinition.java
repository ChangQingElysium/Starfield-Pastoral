package com.stardew.craft.api.v1.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.stardew.craft.api.v1.condition.StardewCondition;
import com.stardew.craft.api.v1.condition.StardewConditions;
import com.stardew.craft.api.v1.query.StardewItemQueries;
import com.stardew.craft.api.v1.query.StardewItemQuery;

import java.util.List;

/** A normal-mine floor chest reward selected through an Item Query. */
public record StardewMineChestRewardDefinition(
        int floor,
        int priority,
        List<StardewCondition> availableWhen,
        StardewItemQuery reward
) {
    public static final Codec<StardewMineChestRewardDefinition> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.intRange(1, Integer.MAX_VALUE).fieldOf("floor")
                            .forGetter(StardewMineChestRewardDefinition::floor),
                    Codec.INT.optionalFieldOf("priority", 0)
                            .forGetter(StardewMineChestRewardDefinition::priority),
                    StardewConditions.CODEC.listOf().optionalFieldOf("available_when", List.of())
                            .forGetter(StardewMineChestRewardDefinition::availableWhen),
                    StardewItemQueries.CODEC.fieldOf("reward")
                            .forGetter(StardewMineChestRewardDefinition::reward)
            ).apply(instance, StardewMineChestRewardDefinition::new));

    public StardewMineChestRewardDefinition {
        availableWhen = List.copyOf(availableWhen == null ? List.of() : availableWhen);
    }
}
