package com.stardew.craft.api.v1.agriculture;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Registry for addon animal-query behavior that must be revalidated on the server. */
public final class StardewAnimalQueryDefinitions {
    private static final Map<String, StardewAnimalQueryDefinition> DEFINITIONS = new HashMap<>();
    private static final Map<ResourceLocation, String> REGISTRATION_IDS = new HashMap<>();
    private static volatile Map<String, StardewAnimalQueryDefinition> snapshot = Map.of();

    private StardewAnimalQueryDefinitions() {
    }

    public static synchronized void register(StardewAnimalQueryDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        if (DEFINITIONS.containsKey(definition.animalTypeId())) {
            throw new IllegalStateException(
                    "Stardew animal query definition already registered: "
                            + definition.animalTypeId());
        }
        if (REGISTRATION_IDS.containsKey(definition.registrationId())) {
            throw new IllegalStateException(
                    "Stardew animal query registration ID already registered: "
                            + definition.registrationId());
        }
        DEFINITIONS.put(definition.animalTypeId(), definition);
        REGISTRATION_IDS.put(definition.registrationId(), definition.animalTypeId());
        snapshot = Map.copyOf(DEFINITIONS);
    }

    @Nullable
    public static StardewAnimalQueryDefinition definition(String animalTypeId) {
        if (animalTypeId == null) {
            return null;
        }
        return snapshot.get(animalTypeId.trim().toLowerCase(Locale.ROOT));
    }
}
