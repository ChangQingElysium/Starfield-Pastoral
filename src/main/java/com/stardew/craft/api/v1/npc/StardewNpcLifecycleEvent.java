package com.stardew.craft.api.v1.npc;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.Objects;
import java.util.UUID;

/** Immutable server-side NPC entity lifecycle fact. */
public record StardewNpcLifecycleEvent(
        StardewNpcLifecyclePhase phase,
        ResourceLocation npcId,
        ServerLevel level,
        UUID entityUuid,
        String reason
) {
    public StardewNpcLifecycleEvent {
        phase = Objects.requireNonNull(phase, "phase");
        npcId = Objects.requireNonNull(npcId, "npcId");
        level = Objects.requireNonNull(level, "level");
        entityUuid = Objects.requireNonNull(entityUuid, "entityUuid");
        reason = Objects.requireNonNull(reason, "reason").trim();
        if (reason.isEmpty()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
    }
}
