package com.stardew.craft.api.v1.communitycenter;

import com.stardew.craft.api.v1.extension.StardewStateContainerSnapshot;
import com.stardew.craft.api.v1.extension.StardewStateMigration;
import com.stardew.craft.api.v1.extension.StardewStateMigrationPreview;
import com.stardew.craft.api.v1.extension.StardewStateMigrationResult;
import com.stardew.craft.api.v1.internal.state.NamespacedStateContainer;
import com.stardew.craft.api.v1.internal.state.NamespacedStateCodec;
import com.stardew.craft.api.v1.internal.state.NamespacedStateKeyRegistry;
import com.stardew.craft.api.v1.internal.state.NamespacedStateMaintenance;
import com.stardew.craft.api.v1.internal.state.NamespacedStateRemovalPreview;
import com.stardew.craft.api.v1.internal.state.NamespacedStateRemovalResult;
import com.stardew.craft.communitycenter.state.CommunityCenterSavedData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Versioned namespaced state attached to one player's Community Center progress. */
public final class StardewCommunityCenterPersistentData {
    private static final ResourceLocation SCOPE =
            ResourceLocation.fromNamespaceAndPath(
                    "stardewcraft", "state/community_center_player");

    private StardewCommunityCenterPersistentData() {
    }

    public static synchronized Key register(ResourceLocation id, int currentVersion) {
        return new Key(NamespacedStateKeyRegistry.register(
                SCOPE, id, currentVersion));
    }

    public static Optional<Value> read(ServerLevel level, UUID playerId, Key key) {
        requireRegistered(key);
        CompoundTag entry = CommunityCenterSavedData.get(level)
                .getAddonData(playerId, key.id().toString());
        return NamespacedStateCodec.decode(entry)
                .map(value -> new Value(
                        value.storedVersion(), value.payload()));
    }

    public static void write(
            ServerLevel level,
            UUID playerId,
            Key key,
            CompoundTag payload
    ) {
        requireRegistered(key);
        CommunityCenterSavedData.get(level)
                .putAddonData(
                        playerId,
                        key.id().toString(),
                        NamespacedStateCodec.encode(
                                key.handle,
                                Objects.requireNonNull(payload, "payload")));
    }

    public static boolean remove(ServerLevel level, UUID playerId, Key key) {
        requireRegistered(key);
        return CommunityCenterSavedData.get(level)
                .removeAddonData(playerId, key.id().toString());
    }

    public static Optional<StardewStateMigrationPreview> previewMigration(
            ServerLevel level,
            UUID playerId,
            Key key,
            StardewStateMigration migration
    ) throws Exception {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(playerId, "playerId");
        requireRegistered(key);
        NamespacedStateContainer container =
                NamespacedStateContainer.fromTag(
                        SCOPE,
                        CommunityCenterSavedData.get(level)
                                .getAddonData(playerId));
        return container.previewMigration(key.handle, migration);
    }

    public static StardewStateMigrationResult applyMigration(
            ServerLevel level,
            UUID playerId,
            Key key,
            StardewStateMigrationPreview preview
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(playerId, "playerId");
        requireRegistered(key);
        CommunityCenterSavedData data =
                CommunityCenterSavedData.get(level);
        NamespacedStateContainer container =
                NamespacedStateContainer.fromTag(
                        SCOPE, data.getAddonData(playerId));
        StardewStateMigrationResult result =
                container.applyMigration(key.handle, preview);
        if (result == StardewStateMigrationResult.APPLIED) {
            data.putAddonData(
                    playerId,
                    key.id().toString(),
                    container.toTag().getCompound(key.id().toString()));
        }
        return result;
    }

    public static StardewStateContainerSnapshot diagnostics(
            ServerLevel level,
            UUID playerId
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(playerId, "playerId");
        CompoundTag entries = CommunityCenterSavedData.get(level)
                .getAddonData(playerId);
        return NamespacedStateContainer.fromTag(SCOPE, entries)
                .snapshot();
    }

    public static Optional<NamespacedStateRemovalPreview>
    previewRemovalForAdministration(
            ServerLevel level,
            UUID playerId,
            NamespacedStateMaintenance.Authority authority,
            String entryName
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(playerId, "playerId");
        NamespacedStateContainer container =
                NamespacedStateContainer.fromTag(
                        SCOPE,
                        CommunityCenterSavedData.get(level)
                                .getAddonData(playerId));
        return container.previewRemoval(authority, entryName);
    }

    public static NamespacedStateRemovalResult
    applyRemovalForAdministration(
            ServerLevel level,
            UUID playerId,
            NamespacedStateMaintenance.Authority authority,
            NamespacedStateRemovalPreview preview
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(playerId, "playerId");
        CommunityCenterSavedData data =
                CommunityCenterSavedData.get(level);
        NamespacedStateContainer container =
                NamespacedStateContainer.fromTag(
                        SCOPE, data.getAddonData(playerId));
        NamespacedStateRemovalResult result =
                container.applyRemoval(authority, preview);
        if (result == NamespacedStateRemovalResult.APPLIED) {
            data.removeAddonData(playerId, preview.entryName());
        }
        return result;
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
