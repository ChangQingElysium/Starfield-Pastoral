package com.stardew.craft.api.v1.agriculture;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.animal.model.FarmAnimalDefinitions;
import com.stardew.craft.entity.animal.BaseCoopAnimalEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Registry for addon managed-animal types.
 *
 * <p>Registration is safe during addon construction: the entity type supplier is resolved only
 * when StardewCraft needs to spawn a persisted animal. Existing unnamespaced record IDs can be
 * retained for save compatibility, while new addons should prefer an owner-qualified
 * {@code animalTypeId}.
 */
public final class StardewAnimalTypes {
    private static final Map<String, Registered> TYPES = new HashMap<>();
    private static final Map<ResourceLocation, String> REGISTRATION_IDS = new HashMap<>();
    private static volatile Map<String, Registered> snapshot = Map.of();

    private StardewAnimalTypes() {
    }

    /**
     * Registers an addon animal type.
     *
     * @param definition stable record metadata
     * @param entityType deferred supplier for an entity type extending the managed-animal base
     */
    public static synchronized void register(
            StardewAnimalTypeDefinition definition,
            Supplier<? extends EntityType<? extends BaseCoopAnimalEntity>> entityType
    ) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(entityType, "entityType");
        if (FarmAnimalDefinitions.find(definition.animalTypeId()) != null) {
            throw new IllegalArgumentException(
                    "Cannot replace built-in Stardew animal type: " + definition.animalTypeId());
        }
        if (TYPES.containsKey(definition.animalTypeId())) {
            throw new IllegalStateException(
                    "Stardew animal type already registered: " + definition.animalTypeId());
        }
        if (REGISTRATION_IDS.containsKey(definition.registrationId())) {
            throw new IllegalStateException(
                    "Stardew animal registration ID already registered: " + definition.registrationId());
        }

        TYPES.put(definition.animalTypeId(), new Registered(definition, entityType));
        REGISTRATION_IDS.put(definition.registrationId(), definition.animalTypeId());
        snapshot = Map.copyOf(TYPES);
    }

    /** Convenience overload for addons that do not need to retain the definition instance. */
    public static void register(
            ResourceLocation registrationId,
            String animalTypeId,
            String family,
            int daysToMature,
            Supplier<? extends EntityType<? extends BaseCoopAnimalEntity>> entityType
    ) {
        register(
                new StardewAnimalTypeDefinition(
                        registrationId, animalTypeId, family, daysToMature),
                entityType
        );
    }

    /** Returns registered pre-entity metadata, or {@code null} for built-in and unknown types. */
    @Nullable
    public static StardewAnimalTypeDefinition definition(String animalTypeId) {
        Registered registered = snapshot.get(normalizeLookup(animalTypeId));
        return registered == null ? null : registered.definition();
    }

    /** Returns the immutable set of addon animal record IDs registered so far. */
    public static Set<String> registeredTypeIds() {
        return snapshot.keySet();
    }

    /** Returns whether the ID is a built-in or registered addon managed-animal type. */
    public static boolean isKnown(String animalTypeId) {
        String normalized = normalizeLookup(animalTypeId);
        return FarmAnimalDefinitions.find(normalized) != null || snapshot.containsKey(normalized);
    }

    /**
     * Resolves the deferred entity type.
     *
     * <p>A failing addon supplier is isolated and logged so animal reconciliation can leave the
     * authoritative record intact for a later retry.
     */
    @Nullable
    public static EntityType<? extends BaseCoopAnimalEntity> entityType(String animalTypeId) {
        Registered registered = snapshot.get(normalizeLookup(animalTypeId));
        if (registered == null) {
            return null;
        }
        try {
            EntityType<? extends BaseCoopAnimalEntity> resolved = registered.entityType().get();
            if (resolved == null) {
                StardewCraft.LOGGER.error(
                        "Stardew animal type supplier {} returned null for {}",
                        registered.definition().registrationId(),
                        registered.definition().animalTypeId()
                );
            }
            return resolved;
        } catch (RuntimeException exception) {
            StardewCraft.LOGGER.error(
                    "Stardew animal type supplier {} failed for {}",
                    registered.definition().registrationId(),
                    registered.definition().animalTypeId(),
                    exception
            );
            return null;
        }
    }

    private static String normalizeLookup(String animalTypeId) {
        if (animalTypeId == null) {
            return "";
        }
        return animalTypeId.trim().toLowerCase(Locale.ROOT);
    }

    private record Registered(
            StardewAnimalTypeDefinition definition,
            Supplier<? extends EntityType<? extends BaseCoopAnimalEntity>> entityType
    ) {
    }
}
