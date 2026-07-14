package com.stardew.craft.api.v1.shop;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Public registry for add-on supplied dynamic shop inventories. */
public final class StardewShopInventoryProviders {
    private static final Map<ResourceLocation, StardewShopInventoryProvider> PROVIDERS = new LinkedHashMap<>();

    private StardewShopInventoryProviders() {
    }

    public static synchronized void register(ResourceLocation id, StardewShopInventoryProvider provider) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(provider, "provider");
        if (PROVIDERS.putIfAbsent(id, provider) != null) {
            throw new IllegalStateException("Shop inventory provider already registered: " + id);
        }
    }

    @Nullable
    public static synchronized StardewShopInventoryProvider get(ResourceLocation id) {
        return PROVIDERS.get(id);
    }

    public static synchronized Set<ResourceLocation> registeredIds() {
        return Set.copyOf(PROVIDERS.keySet());
    }
}
