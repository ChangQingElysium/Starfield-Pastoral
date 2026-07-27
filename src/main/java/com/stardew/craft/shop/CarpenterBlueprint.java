package com.stardew.craft.shop;

import com.stardew.craft.api.v1.building.StardewBuildingBlueprint;
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

    public static CarpenterBlueprint from(
            StardewBuildingBlueprint blueprint
    ) {
        var definition = blueprint.definition();
        return new CarpenterBlueprint(
                blueprint.id().toString(),
                definition.displayNameKey(),
                definition.descriptionKey(),
                definition.money(),
                definition.materials().stream()
                        .map(material -> new MaterialEntry(
                                material.item().toString(),
                                material.count()))
                        .toList(),
                definition.resultItem().toString(),
                definition.upgrade(),
                definition.previewCanvasSize(),
                definition.magicalConstruction());
    }

    public record MaterialEntry(
        String itemId,
        int count
    ) {}
}
