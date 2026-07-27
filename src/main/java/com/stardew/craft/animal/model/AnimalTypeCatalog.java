package com.stardew.craft.animal.model;

import com.stardew.craft.api.v1.agriculture.StardewAnimalTypeDefinition;
import com.stardew.craft.api.v1.agriculture.StardewAnimalTypes;

import java.util.LinkedHashSet;
import java.util.Set;

import javax.annotation.Nullable;

public final class AnimalTypeCatalog {
    private AnimalTypeCatalog() {
    }

    public static AnimalTypeSpec resolve(String animalTypeId) {
        return require(animalTypeId);
    }

    @Nullable
    public static AnimalTypeSpec find(String animalTypeId) {
        String key = normalize(animalTypeId);
        if (key.isEmpty()) {
            return null;
        }
        FarmAnimalDefinition builtIn = FarmAnimalDefinitions.find(key);
        if (builtIn != null) {
            return new AnimalTypeSpec(
                    builtIn.id(), builtIn.family(), builtIn.daysToMature());
        }
        StardewAnimalTypeDefinition addon = StardewAnimalTypes.definition(key);
        if (addon != null) {
            return new AnimalTypeSpec(
                    addon.animalTypeId(), addon.family(), addon.daysToMature());
        }
        return null;
    }

    public static AnimalTypeSpec require(String animalTypeId) {
        AnimalTypeSpec definition = find(animalTypeId);
        if (definition == null) {
            throw new IllegalArgumentException(
                    "Unknown animal type: " + animalTypeId);
        }
        return definition;
    }

    public static Set<String> knownTypeIds() {
        LinkedHashSet<String> known = new LinkedHashSet<>(FarmAnimalDefinitions.ids());
        known.addAll(StardewAnimalTypes.registeredTypeIds());
        return Set.copyOf(known);
    }

    public record AnimalTypeSpec(String id, String family, int daysToMature) {
    }

    private static String normalize(String animalTypeId) {
        return animalTypeId == null
                ? ""
                : animalTypeId.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
