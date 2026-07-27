package com.stardew.craft.api.v1.agriculture;

import com.stardew.craft.animal.model.FarmAnimalDefinition;
import com.stardew.craft.animal.model.FarmAnimalDefinitions;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Addon purchase listings appended after StardewCraft's built-in Marnie inventory. */
public final class StardewAnimalShopEntries {
    private static final Map<String, StardewAnimalShopEntry> ENTRIES = new HashMap<>();
    private static final Map<ResourceLocation, String> REGISTRATION_IDS = new HashMap<>();
    private static volatile Catalog catalog =
            new Catalog(List.of(), Map.of());

    private StardewAnimalShopEntries() {
    }

    public static synchronized void register(StardewAnimalShopEntry entry) {
        Objects.requireNonNull(entry, "entry");
        FarmAnimalDefinition dataDefinition =
                FarmAnimalDefinitions.find(entry.animalTypeId());
        if (dataDefinition != null
                && dataDefinition.soldByAnimalShop()) {
            throw new IllegalArgumentException(
                    "Cannot replace data-defined Stardew animal shop entry: "
                            + entry.animalTypeId());
        }
        if (ENTRIES.containsKey(entry.animalTypeId())) {
            throw new IllegalStateException(
                    "Stardew animal shop type already registered: " + entry.animalTypeId());
        }
        if (REGISTRATION_IDS.containsKey(entry.registrationId())) {
            throw new IllegalStateException(
                    "Stardew animal shop registration ID already registered: "
                            + entry.registrationId());
        }

        ENTRIES.put(entry.animalTypeId(), entry);
        REGISTRATION_IDS.put(entry.registrationId(), entry.animalTypeId());
        ArrayList<StardewAnimalShopEntry> ordered = new ArrayList<>(ENTRIES.values());
        ordered.sort(Comparator.comparingInt(StardewAnimalShopEntry::sortOrder)
                .thenComparing(value -> value.registrationId().toString()));
        catalog = new Catalog(
                List.copyOf(ordered),
                Map.copyOf(ENTRIES));
    }

    /** Returns an immutable, deterministic snapshot of addon listings. */
    public static List<StardewAnimalShopEntry> entries() {
        return catalog.entries();
    }

    @Nullable
    public static StardewAnimalShopEntry entry(String animalTypeId) {
        if (animalTypeId == null) {
            return null;
        }
        return catalog.byType().get(
                animalTypeId.trim().toLowerCase(Locale.ROOT));
    }

    private record Catalog(
            List<StardewAnimalShopEntry> entries,
            Map<String, StardewAnimalShopEntry> byType
    ) {
    }
}
