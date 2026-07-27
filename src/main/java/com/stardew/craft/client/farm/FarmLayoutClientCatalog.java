package com.stardew.craft.client.farm;

import com.stardew.craft.api.v1.farm.StardewFarmLayoutPreview;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

/** Last server-authored farm selection catalog, retained while visiting join UI. */
@OnlyIn(Dist.CLIENT)
public final class FarmLayoutClientCatalog {
    private static List<StardewFarmLayoutPreview> layouts = List.of();

    private FarmLayoutClientCatalog() {
    }

    public static List<StardewFarmLayoutPreview> layouts() {
        return layouts;
    }

    public static void replace(List<StardewFarmLayoutPreview> serverLayouts) {
        layouts = List.copyOf(serverLayouts);
    }
}
