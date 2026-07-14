package com.stardew.craft.api.v1.quest;

import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

/** Server state available to a quest objective runtime. */
public record QuestObjectiveContext(ServerPlayer player) {
    public QuestObjectiveContext {
        Objects.requireNonNull(player, "player");
    }
}
