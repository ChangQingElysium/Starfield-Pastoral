package com.stardew.craft.api.v1.festival;

import com.stardew.craft.api.v1.internal.festival.StardewFestivalMechanicRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Registration and discovery facade for composable festival mechanics. */
public final class StardewFestivalMechanics {
    private StardewFestivalMechanics() {
    }

    public static void register(
            ResourceLocation registrationId,
            int priority,
            ResourceLocation mechanicId,
            Set<StardewFestivalMechanicCapability> capabilities,
            StardewFestivalMechanicHandler handler
    ) {
        StardewFestivalMechanicRegistry.register(
                registrationId,
                priority,
                mechanicId,
                capabilities,
                handler);
    }

    public static List<StardewFestivalMechanicRegistration> registrations(
            ResourceLocation mechanicId
    ) {
        return StardewFestivalMechanicRegistry.registrations(mechanicId);
    }

    public static Set<StardewFestivalMechanicCapability> capabilities(
            ResourceLocation mechanicId
    ) {
        return StardewFestivalMechanicRegistry.capabilities(mechanicId);
    }

    public static Optional<StardewFestivalMechanicSnapshot> inspect(
            ResourceLocation festivalId
    ) {
        return StardewFestivalMechanicRegistry.inspect(festivalId);
    }
}
