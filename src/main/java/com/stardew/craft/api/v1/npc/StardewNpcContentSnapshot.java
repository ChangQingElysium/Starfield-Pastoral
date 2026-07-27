package com.stardew.craft.api.v1.npc;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

/** Immutable cross-system view of content currently attached to one NPC identity. */
public record StardewNpcContentSnapshot(
        ResourceLocation npcId,
        boolean hasProfile,
        boolean hasDialogue,
        boolean hasSchedule,
        boolean hasGiftTastes,
        List<ResourceLocation> shopBindings,
        List<ResourceLocation> shops,
        List<String> issues
) {
    public StardewNpcContentSnapshot {
        npcId = Objects.requireNonNull(npcId, "npcId");
        shopBindings = List.copyOf(shopBindings);
        shops = List.copyOf(shops);
        issues = List.copyOf(issues);
    }

    public boolean hasContent() {
        return hasProfile || hasDialogue || hasSchedule || hasGiftTastes
                || !shopBindings.isEmpty();
    }

    public boolean valid() {
        return issues.isEmpty();
    }
}
