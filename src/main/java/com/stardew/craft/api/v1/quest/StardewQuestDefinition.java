package com.stardew.craft.api.v1.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.stardew.craft.api.v1.action.StardewAction;
import com.stardew.craft.api.v1.action.StardewActions;
import com.stardew.craft.api.v1.condition.StardewCondition;
import com.stardew.craft.api.v1.condition.StardewConditions;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/** Immutable server-authoritative definition; per-player progress is stored separately. */
public record StardewQuestDefinition(
        QuestText title,
        QuestText description,
        QuestText objectiveText,
        StardewQuestObjective objective,
        List<StardewCondition> availableWhen,
        List<StardewAction> onAccept,
        List<StardewAction> onComplete,
        List<ResourceLocation> nextQuests,
        int moneyReward,
        Optional<QuestText> rewardDescription,
        boolean canCancel,
        int days
) {
    public static final Codec<StardewQuestDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            QuestText.CODEC.fieldOf("title").forGetter(StardewQuestDefinition::title),
            QuestText.CODEC.fieldOf("description").forGetter(StardewQuestDefinition::description),
            QuestText.CODEC.optionalFieldOf("objective_text", QuestText.empty()).forGetter(StardewQuestDefinition::objectiveText),
            StardewQuestObjectives.CODEC.fieldOf("objective").forGetter(StardewQuestDefinition::objective),
            StardewConditions.CODEC.listOf().optionalFieldOf("available_when", List.of()).forGetter(StardewQuestDefinition::availableWhen),
            StardewActions.CODEC.listOf().optionalFieldOf("on_accept", List.of()).forGetter(StardewQuestDefinition::onAccept),
            StardewActions.CODEC.listOf().optionalFieldOf("on_complete", List.of()).forGetter(StardewQuestDefinition::onComplete),
            ResourceLocation.CODEC.listOf().optionalFieldOf("next_quests", List.of()).forGetter(StardewQuestDefinition::nextQuests),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("money_reward", 0).forGetter(StardewQuestDefinition::moneyReward),
            QuestText.CODEC.optionalFieldOf("reward_description").forGetter(StardewQuestDefinition::rewardDescription),
            Codec.BOOL.optionalFieldOf("can_cancel", true).forGetter(StardewQuestDefinition::canCancel),
            Codec.INT.optionalFieldOf("days", -1).forGetter(StardewQuestDefinition::days)
    ).apply(instance, StardewQuestDefinition::new));

    public StardewQuestDefinition {
        availableWhen = List.copyOf(availableWhen);
        onAccept = List.copyOf(onAccept);
        onComplete = List.copyOf(onComplete);
        nextQuests = List.copyOf(nextQuests);
    }
}
