package com.stardew.craft.api.v1.equipment;

import com.stardew.craft.data.StardewDataMaps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Stack-aware equipment metadata lookup. Providers run before the static Data Map. */
public final class StardewEquipmentDataApi {
    private static final List<Registered> PROVIDERS = new ArrayList<>();

    private StardewEquipmentDataApi() {
    }

    public static synchronized void registerProvider(ResourceLocation id, int priority,
                                                     StardewEquipmentDataProvider provider) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(provider, "provider");
        if (PROVIDERS.stream().anyMatch(entry -> entry.id().equals(id))) {
            throw new IllegalStateException("Equipment data provider already registered: " + id);
        }
        PROVIDERS.add(new Registered(id, priority, provider));
        PROVIDERS.sort(Comparator.comparingInt(Registered::priority).reversed()
                .thenComparing(entry -> entry.id().toString()));
    }

    @Nullable
    public static synchronized StardewEquipmentData get(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        for (Registered registered : PROVIDERS) {
            StardewEquipmentData data = registered.provider().resolve(stack);
            if (data != null) return data;
        }
        return stack.getItem().builtInRegistryHolder().getData(StardewDataMaps.EQUIPMENT_DATA);
    }

    private record Registered(ResourceLocation id, int priority, StardewEquipmentDataProvider provider) {
    }
}
