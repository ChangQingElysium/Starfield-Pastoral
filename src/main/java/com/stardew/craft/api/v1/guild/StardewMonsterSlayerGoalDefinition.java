package com.stardew.craft.api.v1.guild;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.stardew.craft.api.v1.action.StardewAction;
import com.stardew.craft.api.v1.action.StardewActions;

import java.util.List;

/** Reloadable Adventurer's Guild monster-eradication goal. */
public record StardewMonsterSlayerGoalDefinition(
        String translationKey,
        int requiredKills,
        List<String> monsterTags,
        List<StardewAction> rewards
) {
    public static final Codec<StardewMonsterSlayerGoalDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("translation_key").forGetter(StardewMonsterSlayerGoalDefinition::translationKey),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf("required_kills")
                    .forGetter(StardewMonsterSlayerGoalDefinition::requiredKills),
            Codec.STRING.listOf().fieldOf("monster_tags").forGetter(StardewMonsterSlayerGoalDefinition::monsterTags),
            StardewActions.CODEC.listOf().optionalFieldOf("rewards", List.of())
                    .forGetter(StardewMonsterSlayerGoalDefinition::rewards)
    ).apply(instance, StardewMonsterSlayerGoalDefinition::new));

    public StardewMonsterSlayerGoalDefinition {
        monsterTags = List.copyOf(monsterTags == null ? List.of() : monsterTags);
        rewards = List.copyOf(rewards == null ? List.of() : rewards);
        if (monsterTags.isEmpty()) throw new IllegalArgumentException("monster slayer goal needs monster_tags");
    }
}
