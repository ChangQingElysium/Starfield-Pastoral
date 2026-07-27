package com.stardew.craft.api.v1.internal.npc;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.internal.extension.OrderedExtensionRegistry;
import com.stardew.craft.api.v1.npc.StardewNpcEntities;
import com.stardew.craft.api.v1.npc.StardewNpcInteractions;
import com.stardew.craft.entity.npc.StardewNpcEntity;
import com.stardew.craft.npc.runtime.NpcSpawnManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.Objects;

/** Internal NPC entity resolver dispatch. */
public final class StardewNpcEntityRegistry {
    private static final OrderedExtensionRegistry<
            StardewNpcEntities.Resolver> RESOLVERS =
            new OrderedExtensionRegistry<>(
                    ResourceLocation.fromNamespaceAndPath(
                            StardewCraft.MODID, "npc/entity"));

    private StardewNpcEntityRegistry() {
    }

    public static void register(
            ResourceLocation id,
            int priority,
            StardewNpcEntities.Resolver resolver
    ) {
        RESOLVERS.register(id, priority, resolver);
    }

    public static Entity resolve(ServerLevel level, ResourceLocation npcId) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(npcId, "npcId");
        StardewNpcEntities.Context context = new StardewNpcEntities.Context(level, npcId);
        for (var registered : RESOLVERS.entries()) {
            try {
                Entity candidate = RESOLVERS.invoke(
                        registered,
                        resolver -> resolver.resolve(context));
                if (isValid(candidate, level, npcId)) {
                    return candidate;
                }
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error(
                        "NPC entity resolver {} failed for {}",
                        registered.id(), npcId, exception);
            }
        }

        String trackedId = StardewCraft.MODID.equals(npcId.getNamespace())
                ? npcId.getPath()
                : npcId.toString();
        Entity tracked = NpcSpawnManager.getTrackedNpc(level, trackedId);
        return isValid(tracked, level, npcId) ? tracked : null;
    }

    private static boolean isValid(
            Entity entity,
            ServerLevel level,
            ResourceLocation requestedId
    ) {
        if (entity == null || entity.level() != level || entity.isRemoved() || !entity.isAlive()) {
            return false;
        }
        if (entity instanceof StardewNpcEntity npc) {
            ResourceLocation actualId = StardewNpcInteractions.normalizeNpcId(npc.getNpcId());
            return requestedId.equals(actualId);
        }
        return true;
    }
}
