package com.stardew.craft.api.v1.item;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** One immutable, display-safe way an item can be obtained. */
public record StardewAcquisitionSource(
        ResourceLocation itemId,
        Kind kind,
        ResourceLocation sourceId,
        int outputCount,
        Component display,
        boolean runtimeDependent
) {
    public StardewAcquisitionSource {
        itemId = Objects.requireNonNull(itemId, "itemId");
        kind = Objects.requireNonNull(kind, "kind");
        sourceId = Objects.requireNonNull(sourceId, "sourceId");
        if (outputCount < 1) {
            throw new IllegalArgumentException(
                    "acquisition output count must be positive");
        }
        display = Objects.requireNonNull(display, "display").copy();
    }

    @Override
    public Component display() {
        return display.copy();
    }

    public enum Kind {
        SHOP,
        CRAFTING,
        COOKING,
        MACHINE,
        FARMING,
        LOOT,
        QUEST,
        PROGRESS,
        FESTIVAL,
        OTHER
    }
}
