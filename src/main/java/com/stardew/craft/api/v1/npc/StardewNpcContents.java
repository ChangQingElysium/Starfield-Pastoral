package com.stardew.craft.api.v1.npc;

import com.stardew.craft.api.v1.internal.npc.StardewNpcContentInspector;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

/** Cross-system NPC content and runtime diagnostics. */
public final class StardewNpcContents {
    private StardewNpcContents() {
    }

    public static StardewNpcContentSnapshot inspect(ResourceLocation npcId) {
        return StardewNpcContentInspector.inspect(npcId);
    }

    public static StardewNpcContentSnapshot inspect(String rawNpcId) {
        ResourceLocation npcId = StardewNpcInteractions.normalizeNpcId(rawNpcId);
        if (npcId == null) {
            throw new IllegalArgumentException("Invalid NPC ID: " + rawNpcId);
        }
        return inspect(npcId);
    }

    public static StardewNpcRuntimeSnapshot inspect(
            ServerLevel level,
            ResourceLocation npcId
    ) {
        return StardewNpcContentInspector.inspect(level, npcId);
    }

    public static List<ResourceLocation> ids() {
        return StardewNpcContentInspector.ids();
    }
}
