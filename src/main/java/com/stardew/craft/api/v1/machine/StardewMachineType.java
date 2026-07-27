package com.stardew.craft.api.v1.machine;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

/** Stable runtime and JEI descriptor for an addon artisan machine. */
public record StardewMachineType(
        ResourceLocation id,
        ResourceLocation itemId,
        String translationKey,
        Layout layout,
        boolean producesItem,
        List<AuxiliaryInput> auxiliaryInputs
) {
    public StardewMachineType {
        id = Objects.requireNonNull(id, "id");
        itemId = Objects.requireNonNull(itemId, "itemId");
        translationKey = Objects.requireNonNull(translationKey, "translationKey");
        if (translationKey.isBlank()) {
            throw new IllegalArgumentException("Machine translation key cannot be blank");
        }
        layout = Objects.requireNonNull(layout, "layout");
        auxiliaryInputs = List.copyOf(auxiliaryInputs);
        if (auxiliaryInputs.size() > 1) {
            throw new IllegalArgumentException(
                    "The current stable machine layout supports at most one auxiliary input");
        }
        if (!auxiliaryInputs.isEmpty() && layout != Layout.AUXILIARY_INPUT) {
            throw new IllegalArgumentException(
                    "Auxiliary inputs require the AUXILIARY_INPUT layout");
        }
    }

    public enum Layout {
        STANDARD,
        AUXILIARY_INPUT,
        RANDOM_OUTPUT
    }

    public record AuxiliaryInput(ResourceLocation itemId, int count) {
        public AuxiliaryInput {
            itemId = Objects.requireNonNull(itemId, "itemId");
            if (count <= 0) {
                throw new IllegalArgumentException("Auxiliary input count must be positive");
            }
        }
    }
}
