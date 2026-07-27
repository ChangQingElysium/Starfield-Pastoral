package com.stardew.craft.api.v1.festival;

import com.stardew.craft.api.v1.extension.StardewStateContainerSnapshot;
import com.stardew.craft.api.v1.extension.StardewStateMigration;
import com.stardew.craft.api.v1.extension.StardewStateMigrationPreview;
import com.stardew.craft.api.v1.extension.StardewStateMigrationResult;
import com.stardew.craft.api.v1.internal.state.NamespacedStateContainer;
import com.stardew.craft.api.v1.internal.state.NamespacedStateKeyRegistry;
import com.stardew.craft.api.v1.internal.state.NamespacedStateMaintenance;
import com.stardew.craft.api.v1.internal.state.NamespacedStateRemovalPreview;
import com.stardew.craft.api.v1.internal.state.NamespacedStateRemovalResult;
import com.stardew.craft.festival.FestivalDefinition;
import com.stardew.craft.festival.FestivalRegistry;
import com.stardew.craft.festival.FestivalSessionState;
import com.stardew.craft.festival.FestivalWorldData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Versioned namespaced state attached to a festival session.
 *
 * <p>Unknown entries are preserved when their addon is absent.
 */
public final class StardewFestivalSessionPersistentData {
    private static final ResourceLocation SCOPE =
            ResourceLocation.fromNamespaceAndPath(
                    "stardewcraft", "state/festival_session");

    private final NamespacedStateContainer container;

    private StardewFestivalSessionPersistentData(CompoundTag entries) {
        container = NamespacedStateContainer.fromTag(SCOPE, entries);
    }

    public static StardewFestivalSessionPersistentData empty() {
        return new StardewFestivalSessionPersistentData(new CompoundTag());
    }

    public static StardewFestivalSessionPersistentData fromTag(
            CompoundTag entries
    ) {
        return new StardewFestivalSessionPersistentData(
                entries == null ? new CompoundTag() : entries);
    }

    public static synchronized Key register(
            ResourceLocation id,
            int currentVersion
    ) {
        return new Key(NamespacedStateKeyRegistry.register(
                SCOPE, id, currentVersion));
    }

    public static Optional<Value> read(
            ServerLevel level,
            ResourceLocation festivalId,
            Key key
    ) {
        requireRegistered(key);
        return findSession(level, festivalId)
                .flatMap(session -> session.persistentData()
                        .readEntry(key));
    }

    public static boolean write(
            ServerLevel level,
            ResourceLocation festivalId,
            Key key,
            CompoundTag payload
    ) {
        requireRegistered(key);
        FestivalWorldData data = FestivalWorldData.get(
                Objects.requireNonNull(level, "level"));
        FestivalSessionState session =
                findSession(level, festivalId).orElse(null);
        if (session == null) {
            return false;
        }
        session.persistentData().writeEntry(
                key, Objects.requireNonNull(payload, "payload"));
        data.setDirty();
        return true;
    }

    public static boolean remove(
            ServerLevel level,
            ResourceLocation festivalId,
            Key key
    ) {
        requireRegistered(key);
        FestivalWorldData data = FestivalWorldData.get(
                Objects.requireNonNull(level, "level"));
        FestivalSessionState session =
                findSession(level, festivalId).orElse(null);
        if (session == null
                || !session.persistentData().removeEntry(key)) {
            return false;
        }
        data.setDirty();
        return true;
    }

    public static Optional<StardewStateMigrationPreview> previewMigration(
            ServerLevel level,
            ResourceLocation festivalId,
            Key key,
            StardewStateMigration migration
    ) throws Exception {
        requireRegistered(key);
        FestivalSessionState session =
                findSession(level, festivalId).orElse(null);
        return session == null
                ? Optional.empty()
                : session.persistentData()
                        .previewEntry(key, migration);
    }

    public static StardewStateMigrationResult applyMigration(
            ServerLevel level,
            ResourceLocation festivalId,
            Key key,
            StardewStateMigrationPreview preview
    ) {
        requireRegistered(key);
        FestivalWorldData data = FestivalWorldData.get(
                Objects.requireNonNull(level, "level"));
        FestivalSessionState session =
                findSession(level, festivalId).orElse(null);
        if (session == null) {
            return StardewStateMigrationResult.MISSING;
        }
        StardewStateMigrationResult result =
                session.persistentData().applyEntry(key, preview);
        if (result == StardewStateMigrationResult.APPLIED) {
            data.setDirty();
        }
        return result;
    }

    public static Optional<StardewStateContainerSnapshot> diagnostics(
            ServerLevel level,
            ResourceLocation festivalId
    ) {
        return findSession(level, festivalId)
                .map(session -> session.persistentData().diagnostics());
    }

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

    public Optional<Value> readEntry(Key key) {
        requireRegistered(key);
        return container.read(key.handle)
                .map(value -> new Value(
                        value.storedVersion(), value.payload()));
    }

    public void writeEntry(Key key, CompoundTag payload) {
        requireRegistered(key);
        container.write(key.handle, payload);
    }

    public boolean removeEntry(Key key) {
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

    private static Optional<FestivalSessionState> findSession(
            ServerLevel level,
            ResourceLocation festivalId
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(festivalId, "festivalId");
        String runtimeId = FestivalRegistry.get(festivalId)
                .map(FestivalDefinition::id)
                .orElse(festivalId.toString());
        return FestivalWorldData.get(level).getSession(runtimeId);
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
                throw new IllegalArgumentException(
                        "storedVersion must be non-negative");
            }
            payload = Objects.requireNonNull(payload, "payload").copy();
        }

        @Override
        public CompoundTag payload() {
            return payload.copy();
        }
    }
}
