package com.stardew.craft.api.v1.extension;

import com.stardew.craft.api.v1.internal.extension.ExtensionPointCatalog;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/** Read-only diagnostics for extension points backed by the shared registry kernel. */
public final class StardewExtensions {
    private StardewExtensions() {
    }

    public static List<StardewExtensionPointSnapshot> snapshot() {
        return ExtensionPointCatalog.snapshots();
    }

    public static Optional<StardewExtensionPointSnapshot> find(
            ResourceLocation extensionPointId
    ) {
        return ExtensionPointCatalog.snapshot(extensionPointId);
    }
}
