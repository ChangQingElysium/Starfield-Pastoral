package com.stardew.craft.api.v1.npc;

import com.stardew.craft.api.v1.internal.npc.StardewNpcProfileRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/**
 * Unified NPC identity catalog.
 *
 * <p>Definitions registered here are visible to the social overview and existing display
 * facade. More specialized behavior continues to compose through {@link StardewNpcEntities},
 * {@link StardewNpcGifts}, {@link StardewNpcInteractions} and {@link StardewNpcSocialRules}.
 */
public final class StardewNpcProfiles {
    private StardewNpcProfiles() {
    }

    public static void register(
            ResourceLocation registrationId,
            int priority,
            StardewNpcDefinition definition
    ) {
        StardewNpcProfileRegistry.register(registrationId, priority, definition);
    }

    public static Optional<StardewNpcDefinition> resolve(ResourceLocation npcId) {
        return Optional.ofNullable(StardewNpcProfileRegistry.resolve(npcId));
    }

    public static Optional<StardewNpcDefinition> resolve(String rawNpcId) {
        ResourceLocation npcId = StardewNpcInteractions.normalizeNpcId(rawNpcId);
        return npcId == null ? Optional.empty() : resolve(npcId);
    }

    /** Stable, sorted IDs from both data-defined core NPCs and registered addon profiles. */
    public static List<ResourceLocation> ids() {
        return StardewNpcProfileRegistry.ids();
    }
}
