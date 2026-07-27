package com.stardew.craft.api.v1.farm;

import com.stardew.craft.api.v1.internal.farm.StardewFarmLayoutRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/** Registration and lookup facade for built-in and addon farm layouts. */
public final class StardewFarmLayouts {
    private StardewFarmLayouts() {
    }

    public static void register(StardewFarmLayout layout) {
        StardewFarmLayoutRegistry.register(layout);
    }

    /**
     * Registers a versioned layout with server-defined creation options.
     *
     * <p>The original overload remains equivalent to version {@code 1} with
     * no options.
     */
    public static void register(
            StardewFarmLayout layout,
            int version,
            List<StardewFarmLayoutConfigField> configurationFields
    ) {
        StardewFarmLayoutRegistry.register(
                new StardewFarmLayoutRegistration(
                        layout, version, configurationFields));
    }

    public static void register(
            StardewFarmLayout layout,
            int version,
            List<StardewFarmLayoutConfigField> configurationFields,
            List<StardewFarmLayoutAttachment> attachments
    ) {
        StardewFarmLayoutRegistry.register(
                new StardewFarmLayoutRegistration(
                        layout, version, configurationFields, attachments));
    }

    public static Optional<StardewFarmLayout> find(ResourceLocation id) {
        return StardewFarmLayoutRegistry.find(id);
    }

    public static Optional<StardewFarmLayoutRegistration> findRegistration(
            ResourceLocation id
    ) {
        return StardewFarmLayoutRegistry.findRegistration(id);
    }

    public static List<StardewFarmLayout> all() {
        return StardewFarmLayoutRegistry.all();
    }

    public static List<StardewFarmLayoutRegistration> allRegistrations() {
        return StardewFarmLayoutRegistry.allRegistrations();
    }

    public static List<StardewFarmLayout> selectable() {
        return StardewFarmLayoutRegistry.selectable();
    }

    public static List<StardewFarmLayoutRegistration> selectableRegistrations() {
        return StardewFarmLayoutRegistry.selectableRegistrations();
    }
}
