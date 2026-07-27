package com.stardew.craft.api.v1.farm;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Versioned registration metadata around a farm layout.
 *
 * <p>The geometry record remains unchanged for source compatibility. Version
 * and selection options live here because they describe registration and
 * migration policy rather than the immutable world snapshot.
 */
public record StardewFarmLayoutRegistration(
        StardewFarmLayout layout,
        int version,
        List<StardewFarmLayoutConfigField> configurationFields,
        List<StardewFarmLayoutAttachment> attachments
) {
    public StardewFarmLayoutRegistration {
        layout = Objects.requireNonNull(layout, "layout");
        if (version < 1) {
            throw new IllegalArgumentException("Farm layout version must be at least 1");
        }
        configurationFields = List.copyOf(
                Objects.requireNonNull(configurationFields, "configurationFields"));
        attachments = List.copyOf(
                Objects.requireNonNull(attachments, "attachments"));
        if (configurationFields.size() > 64) {
            throw new IllegalArgumentException("Farm layout has too many configuration fields");
        }
        HashSet<Object> ids = new HashSet<>();
        for (StardewFarmLayoutConfigField field : configurationFields) {
            if (!ids.add(field.id())) {
                throw new IllegalArgumentException(
                        "Duplicate farm layout configuration field: " + field.id());
            }
        }
        if (attachments.size() > 128) {
            throw new IllegalArgumentException(
                    "Farm layout has too many attachments");
        }
        ids.clear();
        for (StardewFarmLayoutAttachment attachment : attachments) {
            if (!ids.add(attachment.id())) {
                throw new IllegalArgumentException(
                        "Duplicate farm layout attachment: "
                                + attachment.id());
            }
        }
    }

    public StardewFarmLayoutRegistration(
            StardewFarmLayout layout,
            int version,
            List<StardewFarmLayoutConfigField> configurationFields
    ) {
        this(layout, version, configurationFields, List.of());
    }

    public StardewFarmLayoutConfiguration defaultConfiguration() {
        return StardewFarmLayoutConfiguration.validate(
                configurationFields, java.util.Map.of());
    }

    public java.util.Optional<StardewFarmLayoutAttachment> findAttachment(
            net.minecraft.resources.ResourceLocation id
    ) {
        return attachments.stream()
                .filter(attachment -> attachment.id().equals(id))
                .findFirst();
    }

    public List<StardewFarmLayoutAttachment> attachmentsWithTag(
            net.minecraft.resources.ResourceLocation tag
    ) {
        return attachments.stream()
                .filter(attachment -> attachment.tags().contains(tag))
                .toList();
    }
}
