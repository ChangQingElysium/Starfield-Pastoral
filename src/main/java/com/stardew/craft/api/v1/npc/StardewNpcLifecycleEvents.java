package com.stardew.craft.api.v1.npc;

import com.stardew.craft.api.v1.internal.npc.StardewNpcLifecycleRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.Objects;

/**
 * Ordered NPC lifecycle observers plus explicit reporting for addon-owned entities.
 *
 * <p>Reports are facts and cannot veto entity creation or removal.
 */
public final class StardewNpcLifecycleEvents {
    private StardewNpcLifecycleEvents() {
    }

    public static void register(
            ResourceLocation registrationId,
            int priority,
            StardewNpcLifecycleListener listener
    ) {
        StardewNpcLifecycleRegistry.register(
                registrationId, priority, listener);
    }

    public static void announceSpawned(
            ResourceLocation npcId,
            Entity entity,
            String reason
    ) {
        announce(StardewNpcLifecyclePhase.SPAWNED, npcId, entity, reason);
    }

    public static void announceRemoved(
            ResourceLocation npcId,
            Entity entity,
            String reason
    ) {
        announce(StardewNpcLifecyclePhase.REMOVED, npcId, entity, reason);
    }

    public static void announceSpawned(
            String rawNpcId,
            Entity entity,
            String reason
    ) {
        announceSpawned(requireNpcId(rawNpcId), entity, reason);
    }

    public static void announceRemoved(
            String rawNpcId,
            Entity entity,
            String reason
    ) {
        announceRemoved(requireNpcId(rawNpcId), entity, reason);
    }

    private static void announce(
            StardewNpcLifecyclePhase phase,
            ResourceLocation npcId,
            Entity entity,
            String reason
    ) {
        Objects.requireNonNull(entity, "entity");
        if (!(entity.level() instanceof ServerLevel level)) {
            throw new IllegalArgumentException(
                    "NPC lifecycle events require a server-side entity");
        }
        StardewNpcLifecycleRegistry.dispatch(new StardewNpcLifecycleEvent(
                phase, npcId, level, entity.getUUID(), reason));
    }

    private static ResourceLocation requireNpcId(String rawNpcId) {
        ResourceLocation npcId = StardewNpcInteractions.normalizeNpcId(rawNpcId);
        if (npcId == null) {
            throw new IllegalArgumentException("Invalid NPC ID: " + rawNpcId);
        }
        return npcId;
    }
}
