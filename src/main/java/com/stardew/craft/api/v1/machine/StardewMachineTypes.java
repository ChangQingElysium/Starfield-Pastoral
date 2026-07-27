package com.stardew.craft.api.v1.machine;

import com.stardew.craft.api.v1.internal.machine.StardewMachineTypeRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Registration and query facade for addon artisan machine descriptors.
 *
 * <p>Registration must happen during mod construction, before JEI requests its categories.
 */
public final class StardewMachineTypes {
    private StardewMachineTypes() {
    }

    public static void register(ResourceLocation registrationId, StardewMachineType machine) {
        StardewMachineTypeRegistry.register(registrationId, machine);
    }

    public static StardewMachineType definition(ResourceLocation machineId) {
        return StardewMachineTypeRegistry.definition(machineId);
    }

    public static List<StardewMachineType> addonDefinitions() {
        return StardewMachineTypeRegistry.definitions();
    }
}
