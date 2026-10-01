package com.stardew.craft.api.v1.pet;

import com.stardew.craft.api.v1.internal.extension.OrderedExtensionRegistry;
import com.stardew.craft.pet.PetDefaults;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * Experimental addon entry point. Register on BOTH physical sides during mod construction.
 * Registrations are immutable, reject duplicates, and use the shared extension freeze lifecycle.
 * New-farm setup, initial adoption, shops, care and the native renderer consume this catalog.
 */
public final class StardewPets {
    private static final OrderedExtensionRegistry<StardewPetSpeciesDefinition> SPECIES = new OrderedExtensionRegistry<>(new ResourceLocation("stardewcraft:pet/species"));
    private static final OrderedExtensionRegistry<StardewPetBreedDefinition> BREEDS = new OrderedExtensionRegistry<>(new ResourceLocation("stardewcraft:pet/breeds"));
    private static volatile Map<ResourceLocation, StardewPetSpeciesDefinition> species = Map.of();
    private static volatile Map<ResourceLocation, StardewPetBreedDefinition> breeds = Map.of();
    static { PetDefaults.register(); }
    private StardewPets() {}

    public static synchronized void registerSpecies(StardewPetSpeciesDefinition definition) {
        SPECIES.register(definition.id(), 0, definition);
        var next = new LinkedHashMap<>(species); next.put(definition.id(), definition); species = Map.copyOf(next);
    }
    public static synchronized void registerBreed(StardewPetBreedDefinition definition) {
        if (!species.containsKey(definition.species())) throw new IllegalArgumentException("Register pet species first: " + definition.species());
        BREEDS.register(definition.id(), 0, definition);
        var next = new LinkedHashMap<>(breeds); next.put(definition.id(), definition); breeds = Map.copyOf(next);
    }
    public static Optional<StardewPetSpeciesDefinition> species(ResourceLocation id) { return Optional.ofNullable(species.get(id)); }
    public static Optional<StardewPetBreedDefinition> breed(ResourceLocation id) { return Optional.ofNullable(breeds.get(id)); }
    public static List<StardewPetBreedDefinition> breeds() {
        // Keep the original ten starter pets first; addon IDs are ordered deterministically after them.
        return breeds.values().stream().sorted(java.util.Comparator.comparingInt((StardewPetBreedDefinition d) -> PetDefaults.order(d.id()))
                .thenComparing(d -> d.id().toString())).toList();
    }
    public static List<StardewPetSpeciesDefinition> species() { return species.values().stream().sorted(java.util.Comparator.comparing(d -> d.id().toString())).toList(); }
    public static void freeze() { SPECIES.freeze(); BREEDS.freeze(); }

    /** Configuration-phase comparison of all gameplay and asset declarations, independent of registration order. */
    public static String fingerprint() {
        var gson = new com.google.gson.GsonBuilder().registerTypeAdapter(ResourceLocation.class,
                (com.google.gson.JsonSerializer<ResourceLocation>) (id, type, context) -> new com.google.gson.JsonPrimitive(id.toString())).create();
        var tree = gson.toJsonTree(Map.of("species", species(), "breeds", breeds()));
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(canonical(tree).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException error) { throw new AssertionError(error); }
    }
    private static com.google.gson.JsonElement canonical(com.google.gson.JsonElement value) {
        if (value.isJsonObject()) {
            var result = new com.google.gson.JsonObject();
            value.getAsJsonObject().keySet().stream().sorted().forEach(key -> result.add(key, canonical(value.getAsJsonObject().get(key))));
            return result;
        }
        if (value.isJsonArray()) { var result = new com.google.gson.JsonArray(); value.getAsJsonArray().forEach(entry -> result.add(canonical(entry))); return result; }
        return value;
    }
}
