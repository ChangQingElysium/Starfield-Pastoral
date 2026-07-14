package com.stardew.craft.api.v1.quest;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Mutable per-player state created by a registered quest objective type. */
public interface QuestObjectiveRuntime {
    default void onAccepted(QuestObjectiveContext context) {
    }

    default QuestObjectiveResult onProgress(QuestObjectiveContext context, QuestProgressEvent event) {
        return QuestObjectiveResult.NONE;
    }

    default void onCompleted(QuestObjectiveContext context) {
    }

    default CompoundTag saveState() {
        return new CompoundTag();
    }

    default void loadState(CompoundTag state) {
    }

    default List<Component> objectiveComponents(Component fallback) {
        return fallback.getString().isEmpty() ? List.of() : List.of(fallback);
    }

    default int currentCount() {
        return -1;
    }

    default int targetCount() {
        return -1;
    }

    default boolean matchesItemDelivery(String npcId, String itemId) {
        return false;
    }

    default String deliveryTargetMessage() {
        return "";
    }
}
