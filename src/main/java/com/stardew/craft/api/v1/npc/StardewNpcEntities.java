package com.stardew.craft.api.v1.npc;

import com.stardew.craft.api.v1.internal.npc.StardewNpcEntityRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.Objects;
import java.util.Optional;

/** Ordered NPC entity resolution for tracked, event-owned and addon-owned NPCs. */
public final class StardewNpcEntities {
    private StardewNpcEntities() {
    }

    public static void register(ResourceLocation id, int priority, Resolver resolver) {
        StardewNpcEntityRegistry.register(id, priority, resolver);
    }

    public static Optional<Entity> resolve(ServerLevel level, ResourceLocation npcId) {
        return Optional.ofNullable(StardewNpcEntityRegistry.resolve(level, npcId));
    }

    @FunctionalInterface
    public interface Resolver {
        /** Returns a live entity for the requested NPC, or {@code null} to pass. */
        Entity resolve(Context context);
    }

    public record Context(ServerLevel level, ResourceLocation npcId) {
        public Context {
            level = Objects.requireNonNull(level, "level");
            npcId = Objects.requireNonNull(npcId, "npcId");
        }
    }
}
