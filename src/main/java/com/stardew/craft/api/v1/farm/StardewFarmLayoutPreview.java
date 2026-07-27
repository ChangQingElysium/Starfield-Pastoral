package com.stardew.craft.api.v1.farm;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

/**
 * Bounded, client-safe projection of a farm layout registration.
 *
 * <p>World geometry, schematic paths and migration handlers never cross the
 * selection protocol.
 */
public record StardewFarmLayoutPreview(
        ResourceLocation id,
        boolean selectable,
        Component displayName,
        Component description,
        ResourceLocation iconTexture,
        int version,
        List<StardewFarmLayoutConfigField> configurationFields
) {
    public StardewFarmLayoutPreview {
        id = Objects.requireNonNull(id, "id");
        displayName = Objects.requireNonNull(displayName, "displayName");
        description = Objects.requireNonNull(description, "description");
        iconTexture = Objects.requireNonNull(iconTexture, "iconTexture");
        if (version < 1) {
            throw new IllegalArgumentException("Farm layout preview version must be at least 1");
        }
        configurationFields = List.copyOf(
                Objects.requireNonNull(configurationFields, "configurationFields"));
        if (configurationFields.size() > 64) {
            throw new IllegalArgumentException("Farm layout preview has too many fields");
        }
    }

    public static StardewFarmLayoutPreview from(
            StardewFarmLayoutRegistration registration
    ) {
        StardewFarmLayout layout = registration.layout();
        return new StardewFarmLayoutPreview(
                layout.id(),
                layout.selectable(),
                layout.displayName(),
                layout.description(),
                layout.iconTexture(),
                registration.version(),
                registration.configurationFields());
    }
}
