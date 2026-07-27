package com.stardew.craft.api.v1.machine;

import com.stardew.craft.api.v1.internal.machine.StardewMachineRecipeDisplayRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Registration facade for addon-provided machine recipe display DTOs. */
public final class StardewMachineRecipeDisplays {
    private StardewMachineRecipeDisplays() {
    }

    public static void register(ResourceLocation id, int priority, Provider provider) {
        StardewMachineRecipeDisplayRegistry.register(id, priority, provider);
    }

    @FunctionalInterface
    public interface Provider {
        List<StardewMachineRecipeDisplay> displays(ResourceLocation machineId);
    }
}
