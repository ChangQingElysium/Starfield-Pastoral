package com.stardew.craft.api.v1.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.stardew.craft.api.v1.condition.StardewCondition;
import com.stardew.craft.api.v1.condition.StardewConditions;
import com.stardew.craft.api.v1.query.StardewItemQueries;
import com.stardew.craft.api.v1.query.StardewItemQuery;

import java.util.List;

/** A fixed or repeating prize-ticket reward selected through an Item Query. */
public record StardewPrizeTicketRewardDefinition(
        int level,
        int repeatEvery,
        int priority,
        List<StardewCondition> availableWhen,
        StardewItemQuery reward
) {
    public static final Codec<StardewPrizeTicketRewardDefinition> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.intRange(0, Integer.MAX_VALUE).fieldOf("level")
                            .forGetter(StardewPrizeTicketRewardDefinition::level),
                    Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("repeat_every", 0)
                            .forGetter(StardewPrizeTicketRewardDefinition::repeatEvery),
                    Codec.INT.optionalFieldOf("priority", 0)
                            .forGetter(StardewPrizeTicketRewardDefinition::priority),
                    StardewConditions.CODEC.listOf().optionalFieldOf("available_when", List.of())
                            .forGetter(StardewPrizeTicketRewardDefinition::availableWhen),
                    StardewItemQueries.CODEC.fieldOf("reward")
                            .forGetter(StardewPrizeTicketRewardDefinition::reward)
            ).apply(instance, StardewPrizeTicketRewardDefinition::new));

    public StardewPrizeTicketRewardDefinition {
        availableWhen = List.copyOf(availableWhen == null ? List.of() : availableWhen);
    }

    public boolean matches(int prizeLevel) {
        if (prizeLevel < level) return false;
        return repeatEvery == 0 ? prizeLevel == level : (prizeLevel - level) % repeatEvery == 0;
    }
}
