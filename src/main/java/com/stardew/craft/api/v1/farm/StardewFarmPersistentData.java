package com.stardew.craft.api.v1.farm;

import com.stardew.craft.api.v1.extension.StardewStateContainerSnapshot;
import com.stardew.craft.api.v1.extension.StardewStateMigration;
import com.stardew.craft.api.v1.extension.StardewStateMigrationPreview;
import com.stardew.craft.api.v1.extension.StardewStateMigrationResult;
import com.stardew.craft.api.v1.internal.state.NamespacedStateContainer;
import com.stardew.craft.api.v1.internal.state.NamespacedStateKeyRegistry;
import com.stardew.craft.api.v1.internal.state.NamespacedStateMaintenance;
import com.stardew.craft.api.v1.internal.state.NamespacedStateRemovalPreview;
import com.stardew.craft.api.v1.internal.state.NamespacedStateRemovalResult;
import com.stardew.craft.farm.FarmInstance;
import com.stardew.craft.farm.FarmInstanceRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Versioned, namespaced persistent state attached to a farm instance.
 *
 * <p>Unknown entries survive save/load and farm ownership transfer. Mutation requires the exact
 * {@link Key} returned by registration.
 */
public final class StardewFarmPersistentData {
    private static final ResourceLocation SCOPE =
            ResourceLocation.fromNamespaceAndPath(
                    "stardewcraft", "state/farm");

    private final NamespacedStateContainer container;

    private StardewFarmPersistentData(CompoundTag entries) {
        this.container = NamespacedStateContainer.fromTag(SCOPE, entries);
    }

    public static StardewFarmPersistentData empty() {
        return new StardewFarmPersistentData(new CompoundTag());
    }

    public static StardewFarmPersistentData fromTag(CompoundTag entries) {
        return new StardewFarmPersistentData(entries == null ? new CompoundTag() : entries);
    }

    public static synchronized Key register(ResourceLocation id, int currentVersion) {
        return new Key(NamespacedStateKeyRegistry.register(
                SCOPE, id, currentVersion));
    }

    public static Optional<Value> read(
            MinecraftServer server,
            UUID ownerUuid,
            Key key
    ) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        requireRegistered(key);
        FarmInstance farm = FarmInstanceRegistry.get(server).getFarm(ownerUuid);
        return farm == null ? Optional.empty() : farm.persistentData().readEntry(key);
    }

    public static boolean write(
            MinecraftServer server,
            UUID ownerUuid,
            Key key,
            CompoundTag payload
    ) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(payload, "payload");
        requireRegistered(key);
        FarmInstanceRegistry registry = FarmInstanceRegistry.get(server);
        FarmInstance farm = registry.getFarm(ownerUuid);
        if (farm == null) {
            return false;
        }
        farm.persistentData().writeEntry(key, payload);
        registry.setDirty();
        return true;
    }

    public static boolean remove(
            MinecraftServer server,
            UUID ownerUuid,
            Key key
    ) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        requireRegistered(key);
        FarmInstanceRegistry registry = FarmInstanceRegistry.get(server);
        FarmInstance farm = registry.getFarm(ownerUuid);
        if (farm == null || !farm.persistentData().removeEntry(key)) {
            return false;
        }
        registry.setDirty();
        return true;
    }

    public static Optional<StardewStateMigrationPreview> previewMigration(
            MinecraftServer server,
            UUID ownerUuid,
            Key key,
            StardewStateMigration migration
    ) throws Exception {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        requireRegistered(key);
        FarmInstance farm = FarmInstanceRegistry.get(server)
                .getFarm(ownerUuid);
        return farm == null
                ? Optional.empty()
                : farm.persistentData().previewEntry(key, migration);
    }

    public static StardewStateMigrationResult applyMigration(
            MinecraftServer server,
            UUID ownerUuid,
            Key key,
            StardewStateMigrationPreview preview
    ) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        requireRegistered(key);
        FarmInstanceRegistry registry = FarmInstanceRegistry.get(server);
        FarmInstance farm = registry.getFarm(ownerUuid);
        if (farm == null) {
            return StardewStateMigrationResult.MISSING;
        }
        StardewStateMigrationResult result =
                farm.persistentData().applyEntry(key, preview);
        if (result == StardewStateMigrationResult.APPLIED) {
            registry.setDirty();
        }
        return result;
    }

    public static Optional<StardewStateContainerSnapshot> diagnostics(
            MinecraftServer server,
            UUID ownerUuid
    ) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        FarmInstance farm = FarmInstanceRegistry.get(server)
                .getFarm(ownerUuid);
        return farm == null
                ? Optional.empty()
                : Optional.of(farm.persistentData().diagnostics());
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

    public CompoundTag toTag() {
        return container.toTag();
    }

    public boolean isEmpty() {
        return container.isEmpty();
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
