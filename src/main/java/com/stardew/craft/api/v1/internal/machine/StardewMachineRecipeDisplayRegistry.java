package com.stardew.craft.api.v1.internal.machine;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.internal.extension.OrderedExtensionRegistry;
import com.stardew.craft.api.v1.machine.StardewMachineRecipeDisplay;
import com.stardew.craft.api.v1.machine.StardewMachineRecipeDisplays;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Core machine-display dispatch bridge. Not part of the public compatibility surface. */
public final class StardewMachineRecipeDisplayRegistry {
    private static final OrderedExtensionRegistry<
            StardewMachineRecipeDisplays.Provider> PROVIDERS =
            new OrderedExtensionRegistry<>(
                    ResourceLocation.fromNamespaceAndPath(
                            StardewCraft.MODID, "machine/recipe_display"));

    private StardewMachineRecipeDisplayRegistry() {
    }

    public static void register(
            ResourceLocation id,
            int priority,
            StardewMachineRecipeDisplays.Provider provider
    ) {
        PROVIDERS.register(id, priority, provider);
    }

    public static List<StardewMachineRecipeDisplay> displays(ResourceLocation machineId) {
        LinkedHashMap<ResourceLocation, StardewMachineRecipeDisplay> result =
                new LinkedHashMap<>();
        for (var registered : PROVIDERS.entries()) {
            try {
                List<StardewMachineRecipeDisplay> provided =
                        PROVIDERS.invoke(
                                registered,
                                provider -> provider.displays(machineId));
                if (provided == null) {
                    continue;
                }
                for (StardewMachineRecipeDisplay display : provided) {
                    if (display == null || !machineId.equals(display.machineId())) {
                        continue;
                    }
                    StardewMachineRecipeDisplay duplicate =
                            result.putIfAbsent(display.id(), display);
                    if (duplicate != null) {
                        StardewCraft.LOGGER.error(
                                "Duplicate machine display {} from provider {}; keeping first",
                                display.id(), registered.id());
                    }
                }
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error(
                        "Stardew machine display provider {} failed for {}",
                        registered.id(), machineId, exception);
            }
        }
        return List.copyOf(result.values());
    }
}
