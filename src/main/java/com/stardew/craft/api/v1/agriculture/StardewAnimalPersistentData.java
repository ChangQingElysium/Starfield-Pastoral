package com.stardew.craft.api.v1.agriculture;

import com.stardew.craft.animal.data.AnimalWorldData;
import com.stardew.craft.animal.model.FarmAnimalRecord;
import com.stardew.craft.api.v1.extension.StardewStateContainerSnapshot;
import com.stardew.craft.api.v1.extension.StardewStateMigration;
import com.stardew.craft.api.v1.extension.StardewStateMigrationPreview;
import com.stardew.craft.api.v1.extension.StardewStateMigrationResult;
import com.stardew.craft.api.v1.internal.state.NamespacedStateContainer;
import com.stardew.craft.api.v1.internal.state.NamespacedStateKeyRegistry;
import com.stardew.craft.api.v1.internal.state.NamespacedStateMaintenance;
import com.stardew.craft.api.v1.internal.state.NamespacedStateRemovalPreview;
import com.stardew.craft.api.v1.internal.state.NamespacedStateRemovalResult;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Versioned, owner-keyed persistent data attached to a managed animal record.
 *
 * <p>Unknown entries are retained as raw NBT across save/load, allowing a world to be opened while
 * an addon is temporarily absent. Mutation requires the exact {@link Key} returned by registration
 * and goes through the authoritative world data so the save is marked dirty.
 */
public final class StardewAnimalPersistentData {
    private static final ResourceLocation SCOPE =
            ResourceLocation.fromNamespaceAndPath(
                    "stardewcraft", "state/animal");

    private final NamespacedStateContainer container;

    private StardewAnimalPersistentData(CompoundTag entries) {
        this.container = NamespacedStateContainer.fromTag(SCOPE, entries);
    }

    public static StardewAnimalPersistentData empty() {
        return new StardewAnimalPersistentData(new CompoundTag());
    }

    public static StardewAnimalPersistentData fromTag(CompoundTag entries) {
        return new StardewAnimalPersistentData(
                entries == null ? new CompoundTag() : entries);
    }

    public static synchronized Key register(ResourceLocation id, int currentVersion) {
        return new Key(NamespacedStateKeyRegistry.register(
                SCOPE, id, currentVersion));
    }

    public static Optional<Value> read(FarmAnimalRecord animal, Key key) {
        Objects.requireNonNull(animal, "animal");
        requireRegistered(key);
        return animal.persistentData().readEntry(key);
    }

    public static Optional<Value> read(ServerLevel level, long animalId, Key key) {
        Objects.requireNonNull(level, "level");
        requireRegistered(key);
        return AnimalWorldData.get(level).getAnimal(animalId)
                .flatMap(animal -> animal.persistentData().readEntry(key));
    }

    public static boolean write(
            ServerLevel level,
            long animalId,
            Key key,
            CompoundTag payload
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(payload, "payload");
        requireRegistered(key);
        AnimalWorldData worldData = AnimalWorldData.get(level);
        FarmAnimalRecord animal = worldData.getAnimal(animalId).orElse(null);
        if (animal == null) {
            return false;
        }
        animal.persistentData().writeEntry(key, payload);
        worldData.markChanged();
        return true;
    }

    public static boolean remove(ServerLevel level, long animalId, Key key) {
        Objects.requireNonNull(level, "level");
        requireRegistered(key);
        AnimalWorldData worldData = AnimalWorldData.get(level);
        FarmAnimalRecord animal = worldData.getAnimal(animalId).orElse(null);
        if (animal == null || !animal.persistentData().removeEntry(key)) {
            return false;
        }
        worldData.markChanged();
        return true;
    }

    public static Optional<StardewStateMigrationPreview> previewMigration(
            ServerLevel level,
            long animalId,
            Key key,
            StardewStateMigration migration
    ) throws Exception {
        Objects.requireNonNull(level, "level");
        requireRegistered(key);
        FarmAnimalRecord animal = AnimalWorldData.get(level)
                .getAnimal(animalId)
                .orElse(null);
        return animal == null
                ? Optional.empty()
                : animal.persistentData().previewEntry(key, migration);
    }

    public static StardewStateMigrationResult applyMigration(
            ServerLevel level,
            long animalId,
            Key key,
            StardewStateMigrationPreview preview
    ) {
        Objects.requireNonNull(level, "level");
        requireRegistered(key);
        AnimalWorldData worldData = AnimalWorldData.get(level);
        FarmAnimalRecord animal = worldData.getAnimal(animalId)
                .orElse(null);
        if (animal == null) {
            return StardewStateMigrationResult.MISSING;
        }
        StardewStateMigrationResult result =
                animal.persistentData().applyEntry(key, preview);
        if (result == StardewStateMigrationResult.APPLIED) {
            worldData.markChanged();
        }
        return result;
    }

    public static Optional<StardewStateContainerSnapshot> diagnostics(
            ServerLevel level,
            long animalId
    ) {
        Objects.requireNonNull(level, "level");
        return AnimalWorldData.get(level).getAnimal(animalId)
                .map(animal -> animal.persistentData().diagnostics());
    }

    /** IDs present in the save, including entries whose addon is currently absent. */
    public Set<ResourceLocation> storedIds() {
        return container.storedIds();
    }

    public StardewStateContainerSnapshot diagnostics() {
        return container.snapshot();
    }

    public Optional<NamespacedStateRemovalPreview>
    previewRemovalForAdministration(
            NamespacedStateMaintenance.Authority authority,
            String entryName
    ) {
        return container.previewRemoval(authority, entryName);
    }

    public NamespacedStateRemovalResult applyRemovalForAdministration(
            NamespacedStateMaintenance.Authority authority,
            NamespacedStateRemovalPreview preview
    ) {
        return container.applyRemoval(authority, preview);
    }

    /** Returns a defensive copy used by the animal-record serializer. */
    public CompoundTag toTag() {
        return container.toTag();
    }

    private Optional<Value> readEntry(Key key) {
        requireRegistered(key);
        return container.read(key.handle)
                .map(value -> new Value(
                        value.storedVersion(), value.payload()));
    }

    private void writeEntry(Key key, CompoundTag payload) {
        requireRegistered(key);
        container.write(key.handle, payload);
    }

    private boolean removeEntry(Key key) {
        requireRegistered(key);
        return container.remove(key.handle);
    }

    private Optional<StardewStateMigrationPreview> previewEntry(
            Key key,
            StardewStateMigration migration
    ) throws Exception {
        requireRegistered(key);
        return container.previewMigration(key.handle, migration);
    }

    private StardewStateMigrationResult applyEntry(
            Key key,
            StardewStateMigrationPreview preview
    ) {
        requireRegistered(key);
        return container.applyMigration(key.handle, preview);
    }

    private static synchronized void requireRegistered(Key key) {
        Objects.requireNonNull(key, "key");
        NamespacedStateKeyRegistry.require(SCOPE, key.handle);
    }

    public static final class Key {
        private final NamespacedStateKeyRegistry.Handle handle;

        private Key(NamespacedStateKeyRegistry.Handle handle) {
            this.handle = handle;
        }

        public ResourceLocation id() {
            return handle.id();
        }

        public int currentVersion() {
            return handle.currentVersion();
        }
    }

    public record Value(int storedVersion, CompoundTag payload) {
        public Value {
            if (storedVersion < 0) {
                throw new IllegalArgumentException("storedVersion must be non-negative");
            }
            payload = Objects.requireNonNull(payload, "payload").copy();
        }

        @Override
        public CompoundTag payload() {
            return payload.copy();
        }
    }
}
