package com.stardew.craft.api.v1.npc;

import com.stardew.craft.api.v1.internal.npc.StardewNpcDisplayRegistry;
import net.minecraft.resources.ResourceLocation;

/** Ordered, client-safe NPC display metadata providers. */
public final class StardewNpcDisplays {
    private StardewNpcDisplays() {
    }

    public static void register(ResourceLocation id, int priority, Provider provider) {
        StardewNpcDisplayRegistry.register(id, priority, provider);
    }

    public static StardewNpcDisplay resolve(ResourceLocation npcId) {
        return StardewNpcDisplayRegistry.resolve(npcId);
    }

    public static StardewNpcDisplay resolve(String rawNpcId) {
        ResourceLocation npcId = StardewNpcInteractions.normalizeNpcId(rawNpcId);
        return npcId == null ? null : resolve(npcId);
    }

    @FunctionalInterface
    public interface Provider {
        /** Returns display metadata for this NPC, or {@code null} to pass. */
        StardewNpcDisplay resolve(ResourceLocation npcId);
    }
}
