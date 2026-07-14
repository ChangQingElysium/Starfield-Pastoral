package com.stardew.craft.api.v1.quest;

@FunctionalInterface
public interface StardewQuestObjectiveFactory<T> {
    QuestObjectiveRuntime create(T definition);
}
