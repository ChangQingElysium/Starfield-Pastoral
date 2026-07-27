package com.stardew.craft.api.v1.internal.state;

import com.stardew.craft.api.v1.extension.StardewStateContainerSnapshot;
import com.stardew.craft.api.v1.extension.StardewStateMigration;
import com.stardew.craft.api.v1.extension.StardewStateMigrationPreview;
import com.stardew.craft.api.v1.extension.StardewStateMigrationResult;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Mutable internal container which preserves unknown addon entries as opaque NBT.
 */
public final class NamespacedStateContainer {
    private final ResourceLocation scope;
    private final CompoundTag entries;

    private NamespacedStateContainer(
            ResourceLocation scope,
            CompoundTag entries
    ) {
        this.scope = scope;
        this.entries = entries == null ? new CompoundTag() : entries.copy();
    }

    public static NamespacedStateContainer empty(ResourceLocation scope) {
        return new NamespacedStateContainer(scope, new CompoundTag());
    }

    public static NamespacedStateContainer fromTag(
            ResourceLocation scope,
            CompoundTag entries
    ) {
        return new NamespacedStateContainer(scope, entries);
    }

    public Optional<NamespacedStateCodec.StoredValue> read(
            NamespacedStateKeyRegistry.Handle key
    ) {
        require(key);
        return NamespacedStateCodec.decode(
                entries.getCompound(key.id().toString()));
    }

    public void write(
            NamespacedStateKeyRegistry.Handle key,
            CompoundTag payload
    ) {
        require(key);
        entries.put(
                key.id().toString(),
                NamespacedStateCodec.encode(key, payload));
    }

    public boolean remove(NamespacedStateKeyRegistry.Handle key) {
        require(key);
        String rawId = key.id().toString();
        if (!entries.contains(rawId)) {
            return false;
        }
        entries.remove(rawId);
        return true;
    }

    public Optional<StardewStateMigrationPreview> previewMigration(
            NamespacedStateKeyRegistry.Handle key,
            StardewStateMigration migration
    ) throws Exception {
        require(key);
        java.util.Objects.requireNonNull(migration, "migration");
        Optional<NamespacedStateCodec.StoredValue> stored = read(key);
        if (stored.isEmpty()
                || stored.orElseThrow().storedVersion()
                >= key.currentVersion()) {
            return Optional.empty();
        }
        NamespacedStateCodec.StoredValue value = stored.orElseThrow();
        CompoundTag sourcePayload = value.payload();
        CompoundTag migratedPayload = migration.migrate(
                value.storedVersion(),
                key.currentVersion(),
                sourcePayload.copy());
        return Optional.of(new StardewStateMigrationPreview(
                scope,
                key.id(),
                value.storedVersion(),
                key.currentVersion(),
                sourcePayload,
                java.util.Objects.requireNonNull(
                        migratedPayload, "migration result")));
    }

    public StardewStateMigrationResult applyMigration(
            NamespacedStateKeyRegistry.Handle key,
            StardewStateMigrationPreview preview
    ) {
        require(key);
        java.util.Objects.requireNonNull(preview, "preview");
        if (!scope.equals(preview.scope())
                || !key.id().equals(preview.id())
                || key.currentVersion() != preview.targetVersion()) {
            throw new IllegalArgumentException(
                    "Migration preview does not belong to state key "
                            + key.id() + " in scope " + scope);
        }
        String rawId = key.id().toString();
        if (!entries.contains(rawId)) {
            return StardewStateMigrationResult.MISSING;
        }
        Optional<NamespacedStateCodec.StoredValue> current =
                NamespacedStateCodec.decode(entries.getCompound(rawId));
        if (current.isEmpty()) {
            return StardewStateMigrationResult.STALE;
        }
        NamespacedStateCodec.StoredValue currentValue =
                current.orElseThrow();
        if (currentValue.storedVersion() != preview.storedVersion()
                || !currentValue.payload().equals(
                        preview.sourcePayload())) {
            return StardewStateMigrationResult.STALE;
        }
        entries.put(
                rawId,
                NamespacedStateCodec.encode(
                        key, preview.migratedPayload()));
        return StardewStateMigrationResult.APPLIED;
    }

    public Optional<NamespacedStateRemovalPreview> previewRemoval(
            NamespacedStateMaintenance.Authority authority,
            String entryName
    ) {
        NamespacedStateMaintenance.require(authority);
        if (entryName == null || entryName.isBlank()) {
            throw new IllegalArgumentException(
                    "entryName must not be blank");
        }
        Tag entry = entries.get(entryName);
        if (entry == null) {
            return Optional.empty();
        }
        EnumSet<NamespacedStateRemovalPreview.Issue> issues =
                EnumSet.noneOf(
                        NamespacedStateRemovalPreview.Issue.class);
        ResourceLocation id = ResourceLocation.tryParse(entryName);
        if (id == null) {
            issues.add(
                    NamespacedStateRemovalPreview.Issue.INVALID_NAME);
        } else if (!NamespacedStateKeyRegistry.handles(scope)
                .containsKey(id)) {
            issues.add(NamespacedStateRemovalPreview.Issue.ORPHANED);
        }
        if (!(entry instanceof CompoundTag compound)
                || NamespacedStateCodec.decode(compound).isEmpty()) {
            issues.add(NamespacedStateRemovalPreview.Issue.MALFORMED);
        }
        if (issues.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new NamespacedStateRemovalPreview(
                scope,
                entryName,
                issues,
                removalToken(entryName, entry, issues),
                entry));
    }

    public NamespacedStateRemovalResult applyRemoval(
            NamespacedStateMaintenance.Authority authority,
            NamespacedStateRemovalPreview preview
    ) {
        NamespacedStateMaintenance.require(authority);
        java.util.Objects.requireNonNull(preview, "preview");
        if (!scope.equals(preview.scope())) {
            throw new IllegalArgumentException(
                    "Removal preview belongs to scope "
                            + preview.scope() + ", not " + scope);
        }
        Tag current = entries.get(preview.entryName());
        if (current == null) {
            return NamespacedStateRemovalResult.MISSING;
        }
        if (!current.equals(preview.sourceEntry())) {
            return NamespacedStateRemovalResult.STALE;
        }
        Optional<NamespacedStateRemovalPreview> currentPreview =
                previewRemoval(authority, preview.entryName());
        if (currentPreview.isEmpty()) {
            return NamespacedStateRemovalResult.NO_LONGER_ELIGIBLE;
        }
        if (!currentPreview.orElseThrow().confirmationToken()
                .equals(preview.confirmationToken())) {
            return NamespacedStateRemovalResult.STALE;
        }
        entries.remove(preview.entryName());
        return NamespacedStateRemovalResult.APPLIED;
    }

    public Set<ResourceLocation> storedIds() {
        return snapshot().storedIds();
    }

    public StardewStateContainerSnapshot snapshot() {
        Map<ResourceLocation, NamespacedStateKeyRegistry.Handle> registered =
                NamespacedStateKeyRegistry.handles(scope);
        LinkedHashSet<ResourceLocation> stored = new LinkedHashSet<>();
        LinkedHashSet<ResourceLocation> orphaned = new LinkedHashSet<>();
        LinkedHashSet<ResourceLocation> malformed = new LinkedHashSet<>();
        LinkedHashSet<ResourceLocation> legacy = new LinkedHashSet<>();
        LinkedHashSet<ResourceLocation> future = new LinkedHashSet<>();
        List<String> invalidNames = new ArrayList<>();
        List<String> rawIds = entries.getAllKeys().stream()
                .sorted()
                .toList();
        for (String rawId : rawIds) {
            ResourceLocation id = ResourceLocation.tryParse(rawId);
            if (id == null) {
                invalidNames.add(rawId);
                continue;
            }
            stored.add(id);
            NamespacedStateKeyRegistry.Handle handle = registered.get(id);
            if (handle == null) {
                orphaned.add(id);
            }
            Optional<NamespacedStateCodec.StoredValue> value =
                    NamespacedStateCodec.decode(
                            entries.getCompound(rawId));
            if (value.isEmpty()) {
                malformed.add(id);
                continue;
            }
            if (handle == null) {
                continue;
            }
            int storedVersion = value.orElseThrow().storedVersion();
            if (storedVersion < handle.currentVersion()) {
                legacy.add(id);
            } else if (storedVersion > handle.currentVersion()) {
                future.add(id);
            }
        }
        return new StardewStateContainerSnapshot(
                scope,
                stored,
                new LinkedHashSet<>(registered.keySet()),
                orphaned,
                malformed,
                legacy,
                future,
                invalidNames);
    }

    public CompoundTag toTag() {
        return entries.copy();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    private void require(NamespacedStateKeyRegistry.Handle key) {
        NamespacedStateKeyRegistry.require(scope, key);
    }

    private String removalToken(
            String entryName,
            Tag entry,
            Set<NamespacedStateRemovalPreview.Issue> issues
    ) {
        String material = scope + "\n"
                + entryName + "\n"
                + issues.stream()
                        .map(Enum::name)
                        .sorted()
                        .toList()
                + "\n" + entry;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable", exception);
        }
    }
}
