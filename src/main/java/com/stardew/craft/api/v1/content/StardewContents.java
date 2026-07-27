package com.stardew.craft.api.v1.content;

import com.stardew.craft.api.v1.internal.content.StardewContentRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/** Registration and read-only lookup facade for the unified content catalog. */
public final class StardewContents {
    private StardewContents() {
    }

    public static void register(
            ResourceLocation registrationId,
            int priority,
            StardewContentProvider provider
    ) {
        StardewContentRegistry.register(
                registrationId, priority, provider);
    }

    public static StardewContentCatalogSnapshot snapshot() {
        return StardewContentRegistry.snapshot();
    }

    public static void registerAliases(
            ResourceLocation registrationId,
            int priority,
            StardewContentAliasProvider provider
    ) {
        StardewContentRegistry.registerAliases(
                registrationId, priority, provider);
    }

    public static List<StardewContentAliasSnapshot> aliases() {
        return StardewContentRegistry.aliases();
    }

    public static Optional<StardewContentKey> resolve(
            StardewContentKey key
    ) {
        return StardewContentRegistry.resolveKey(key);
    }

    public static Optional<StardewContentNodeSnapshot> find(
            StardewContentKey key
    ) {
        return StardewContentRegistry.find(key);
    }
}
