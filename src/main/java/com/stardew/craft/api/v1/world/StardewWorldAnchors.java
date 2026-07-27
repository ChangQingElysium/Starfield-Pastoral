package com.stardew.craft.api.v1.world;

import com.stardew.craft.world.WorldAnchorRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/** Read-only facade for reloadable named world anchors. */
public final class StardewWorldAnchors {
    private StardewWorldAnchors() {
    }

    public static Optional<StardewWorldAnchor> get(ResourceLocation id) {
        return WorldAnchorRegistry.get(id);
    }

    /**
     * Resolves a full ID, or treats a legacy unqualified point as a
     * {@code stardewcraft} anchor.
     */
    public static Optional<StardewWorldAnchor> resolve(String id) {
        return WorldAnchorRegistry.resolve(id);
    }

    public static List<StardewWorldAnchor> all() {
        return WorldAnchorRegistry.all();
    }

    public static List<StardewWorldAnchor> withRole(ResourceLocation role) {
        return WorldAnchorRegistry.withRole(role);
    }
}
