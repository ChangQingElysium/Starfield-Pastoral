package com.stardew.craft.shop;

import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Data class representing a single building blueprint in Robin's carpenter menu.
 * Mirrors SDV CarpenterMenu.BlueprintEntry.
 */
public record CarpenterBlueprint(
    String id,
    String displayNameKey,
    String descriptionKey,
    int cost,
    List<MaterialEntry> materials,
    String resultItemId,
    boolean isUpgrade,
    int previewCanvasSize,
    boolean magicalConstruction
) {
    public Component displayName() {
        return Component.translatable(displayNameKey);
    }

    public Component description() {
        return Component.translatable(descriptionKey);
    }

    public record MaterialEntry(
        String itemId,
        int count
    ) {}
}
