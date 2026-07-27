package com.stardew.craft.api.v1.festival;

import com.stardew.craft.festival.FestivalMapOverlayRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/** Registration and lookup facade for addon festival map overlays. */
public final class StardewFestivalMapOverlays {
    private StardewFestivalMapOverlays() {
    }

    public static void register(StardewFestivalMapOverlay overlay) {
        FestivalMapOverlayRegistry.registerAddon(overlay);
    }

    public static Optional<StardewFestivalMapOverlay> find(
            ResourceLocation id
    ) {
        return FestivalMapOverlayRegistry.findAddon(id);
    }

    public static List<StardewFestivalMapOverlay> allAddonOverlays() {
        return FestivalMapOverlayRegistry.addonOverlays();
    }
}
