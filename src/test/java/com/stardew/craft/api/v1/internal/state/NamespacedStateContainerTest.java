package com.stardew.craft.api.v1.internal.state;

import com.stardew.craft.api.v1.extension.StardewStateMigrationResult;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NamespacedStateContainerTest {
    @Test
    void preservesUnknownEntriesAndDefensivelyCopiesPayloads() {
        ResourceLocation scope = id("scope_" + System.nanoTime());
        CompoundTag source = new CompoundTag();
        CompoundTag unknown = new CompoundTag();
        unknown.putString("opaque", "keep");
        source.put("missing_addon:data", unknown);
        NamespacedStateContainer container =
                NamespacedStateContainer.fromTag(scope, source);
        ResourceLocation keyId = id("known_" + System.nanoTime());
        var key = NamespacedStateKeyRegistry.register(
                scope, keyId, 3);
        CompoundTag payload = new CompoundTag();
        payload.putString("value", "before");

        container.write(key, payload);
        payload.putString("value", "after");

        assertEquals("before",
                container.read(key).orElseThrow().payload().getString("value"));
        CompoundTag read = container.read(key).orElseThrow().payload();
        read.putString("value", "mutated");
        assertEquals("before",
                container.read(key).orElseThrow().payload().getString("value"));
        assertEquals("keep", container.toTag()
                .getCompound("missing_addon:data")
                .getString("opaque"));
        assertTrue(container.storedIds().contains(
                ResourceLocation.parse("missing_addon:data")));
        assertTrue(NamespacedStateKeyRegistry.snapshots().stream()
                .anyMatch(snapshot -> snapshot.scope().equals(scope)
                        && snapshot.id().equals(keyId)
                        && snapshot.currentVersion() == 3));
    }

    @Test
    void rejectsKeysRegisteredForAnotherScope() {
        ResourceLocation firstScope = id("first_" + System.nanoTime());
        ResourceLocation secondScope = id("second_" + System.nanoTime());
        NamespacedStateContainer container =
                NamespacedStateContainer.empty(firstScope);
        var foreign = NamespacedStateKeyRegistry.register(
                secondScope, id("foreign_" + System.nanoTime()), 1);

        assertThrows(IllegalArgumentException.class,
                () -> container.write(foreign, new CompoundTag()));
        assertThrows(IllegalArgumentException.class,
                () -> container.remove(foreign));
    }

    @Test
    void diagnosesOrphansAndVersionsWithoutMutatingStoredNbt() {
        ResourceLocation scope = id("diagnostics_" + System.nanoTime());
        ResourceLocation currentId = id("current_" + System.nanoTime());
        ResourceLocation legacyId = id("legacy_" + System.nanoTime());
        ResourceLocation futureId = id("future_" + System.nanoTime());
        ResourceLocation malformedId =
                id("malformed_" + System.nanoTime());
        ResourceLocation orphanId = ResourceLocation.fromNamespaceAndPath(
                "absent_addon", "orphan_" + System.nanoTime());
        NamespacedStateKeyRegistry.register(scope, currentId, 3);
        NamespacedStateKeyRegistry.register(scope, legacyId, 3);
        NamespacedStateKeyRegistry.register(scope, futureId, 3);
        NamespacedStateKeyRegistry.register(scope, malformedId, 3);

        CompoundTag source = new CompoundTag();
        source.put(currentId.toString(), entry(3));
        source.put(legacyId.toString(), entry(1));
        source.put(futureId.toString(), entry(5));
        source.put(orphanId.toString(), entry(7));
        source.put(malformedId.toString(), StringTag.valueOf("keep"));
        source.put("not a resource location", entry(2));
        NamespacedStateContainer container =
                NamespacedStateContainer.fromTag(scope, source);

        var snapshot = container.snapshot();

        assertEquals(scope, snapshot.scope());
        assertEquals(Set.of(
                        currentId, legacyId, futureId,
                        malformedId, orphanId),
                snapshot.storedIds());
        assertEquals(Set.of(
                        currentId, legacyId, futureId, malformedId),
                snapshot.registeredIds());
        assertEquals(Set.of(orphanId), snapshot.orphanedIds());
        assertEquals(Set.of(malformedId), snapshot.malformedIds());
        assertEquals(Set.of(legacyId), snapshot.legacyVersionIds());
        assertEquals(Set.of(futureId), snapshot.futureVersionIds());
        assertEquals(List.of("not a resource location"),
                snapshot.invalidEntryNames());
        assertFalse(snapshot.healthy());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.storedIds().clear());
        assertEquals(source, container.toTag());
    }

    @Test
    void emptyRegisteredContainerIsHealthy() {
        ResourceLocation scope = id("healthy_" + System.nanoTime());
        NamespacedStateKeyRegistry.register(
                scope, id("registered_" + System.nanoTime()), 1);

        var snapshot = NamespacedStateContainer.empty(scope).snapshot();

        assertTrue(snapshot.healthy());
        assertTrue(snapshot.storedIds().isEmpty());
        assertEquals(1, snapshot.registeredIds().size());
    }

    @Test
    void previewsThenExplicitlyAppliesOwnedMigration() throws Exception {
        ResourceLocation scope = id("migration_" + System.nanoTime());
        ResourceLocation keyId = id("owned_" + System.nanoTime());
        var key = NamespacedStateKeyRegistry.register(scope, keyId, 3);
        CompoundTag sourcePayload = new CompoundTag();
        sourcePayload.putString("name", "before");
        CompoundTag source = new CompoundTag();
        source.put(keyId.toString(), entry(1, sourcePayload));
        NamespacedStateContainer container =
                NamespacedStateContainer.fromTag(scope, source);

        var preview = container.previewMigration(
                key,
                (storedVersion, targetVersion, payload) -> {
                    assertEquals(1, storedVersion);
                    assertEquals(3, targetVersion);
                    payload.putString("name", "after");
                    return payload;
                }).orElseThrow();

        assertEquals("before", container.read(key).orElseThrow()
                .payload().getString("name"));
        assertEquals("before",
                preview.sourcePayload().getString("name"));
        assertEquals("after",
                preview.migratedPayload().getString("name"));
        CompoundTag exposed = preview.migratedPayload();
        exposed.putString("name", "tampered");

        assertEquals(
                StardewStateMigrationResult.APPLIED,
                container.applyMigration(key, preview));
        NamespacedStateCodec.StoredValue migrated =
                container.read(key).orElseThrow();
        assertEquals(3, migrated.storedVersion());
        assertEquals("after", migrated.payload().getString("name"));
        assertEquals(
                StardewStateMigrationResult.STALE,
                container.applyMigration(key, preview));
    }

    @Test
    void staleOrMissingMigrationPreviewNeverOverwritesState()
            throws Exception {
        ResourceLocation scope =
                id("stale_migration_" + System.nanoTime());
        ResourceLocation keyId = id("owned_" + System.nanoTime());
        var key = NamespacedStateKeyRegistry.register(scope, keyId, 2);
        CompoundTag source = new CompoundTag();
        source.put(keyId.toString(), entry(1));
        NamespacedStateContainer container =
                NamespacedStateContainer.fromTag(scope, source);
        var preview = container.previewMigration(
                key,
                (storedVersion, targetVersion, payload) -> {
                    payload.putString("candidate", "migration");
                    return payload;
                }).orElseThrow();

        CompoundTag concurrent = new CompoundTag();
        concurrent.putString("owner", "newer");
        container.write(key, concurrent);

        assertEquals(
                StardewStateMigrationResult.STALE,
                container.applyMigration(key, preview));
        assertEquals("newer", container.read(key).orElseThrow()
                .payload().getString("owner"));

        container.remove(key);
        assertEquals(
                StardewStateMigrationResult.MISSING,
                container.applyMigration(key, preview));
    }

    @Test
    void migrationPreviewRejectsWrongKeyAndLeavesFailuresUntouched() {
        ResourceLocation scope =
                id("migration_failure_" + System.nanoTime());
        ResourceLocation firstId = id("first_" + System.nanoTime());
        ResourceLocation secondId = id("second_" + System.nanoTime());
        var first = NamespacedStateKeyRegistry.register(scope, firstId, 2);
        var second = NamespacedStateKeyRegistry.register(scope, secondId, 2);
        CompoundTag source = new CompoundTag();
        source.put(firstId.toString(), entry(1));
        NamespacedStateContainer container =
                NamespacedStateContainer.fromTag(scope, source);

        assertThrows(
                IllegalStateException.class,
                () -> container.previewMigration(
                        first,
                        (storedVersion, targetVersion, payload) -> {
                            throw new IllegalStateException("expected");
                        }));
        assertEquals(source, container.toTag());

        var preview = org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> container.previewMigration(
                        first,
                        (storedVersion, targetVersion, payload) -> payload)
                        .orElseThrow());
        assertThrows(
                IllegalArgumentException.class,
                () -> container.applyMigration(second, preview));
        assertEquals(source, container.toTag());
    }

    @Test
    void onlyLegacyWellFormedEntriesProduceMigrationPreviews()
            throws Exception {
        ResourceLocation scope =
                id("migration_eligibility_" + System.nanoTime());
        ResourceLocation currentId = id("current_" + System.nanoTime());
        ResourceLocation futureId = id("future_" + System.nanoTime());
        ResourceLocation malformedId =
                id("malformed_" + System.nanoTime());
        var current =
                NamespacedStateKeyRegistry.register(scope, currentId, 2);
        var future =
                NamespacedStateKeyRegistry.register(scope, futureId, 2);
        var malformed =
                NamespacedStateKeyRegistry.register(scope, malformedId, 2);
        CompoundTag source = new CompoundTag();
        source.put(currentId.toString(), entry(2));
        source.put(futureId.toString(), entry(4));
        source.put(malformedId.toString(), StringTag.valueOf("broken"));
        NamespacedStateContainer container =
                NamespacedStateContainer.fromTag(scope, source);

        assertTrue(container.previewMigration(
                current, (from, to, payload) -> payload).isEmpty());
        assertTrue(container.previewMigration(
                future, (from, to, payload) -> payload).isEmpty());
        assertTrue(container.previewMigration(
                malformed, (from, to, payload) -> payload).isEmpty());
        assertEquals(source, container.toTag());
    }

    @Test
    void removalRequiresIssuePreviewAndExplicitMatchingApply() {
        ResourceLocation scope =
                id("removal_" + System.nanoTime());
        ResourceLocation healthyId = id("healthy_" + System.nanoTime());
        ResourceLocation legacyId = id("legacy_" + System.nanoTime());
        ResourceLocation malformedId =
                id("malformed_" + System.nanoTime());
        ResourceLocation orphanId = ResourceLocation
                .fromNamespaceAndPath(
                        "missing_addon", "orphan_" + System.nanoTime());
        var healthy =
                NamespacedStateKeyRegistry.register(scope, healthyId, 2);
        NamespacedStateKeyRegistry.register(scope, legacyId, 2);
        NamespacedStateKeyRegistry.register(scope, malformedId, 2);
        CompoundTag source = new CompoundTag();
        source.put(healthyId.toString(), entry(2));
        source.put(legacyId.toString(), entry(1));
        source.put(malformedId.toString(), StringTag.valueOf("broken"));
        source.put(orphanId.toString(), entry(7));
        source.put("invalid entry", StringTag.valueOf("opaque"));
        NamespacedStateContainer container =
                NamespacedStateContainer.fromTag(scope, source);
        var authority = new NamespacedStateMaintenance.Authority();

        assertTrue(container.previewRemoval(
                authority, healthyId.toString()).isEmpty());
        assertTrue(container.previewRemoval(
                authority, legacyId.toString()).isEmpty());

        NamespacedStateRemovalPreview orphanPreview =
                container.previewRemoval(
                        authority, orphanId.toString()).orElseThrow();
        assertEquals(
                Set.of(NamespacedStateRemovalPreview.Issue.ORPHANED),
                orphanPreview.issues());
        assertFalse(orphanPreview.confirmationToken().isBlank());
        assertEquals(
                orphanPreview.confirmationToken(),
                container.previewRemoval(
                                authority, orphanId.toString())
                        .orElseThrow()
                        .confirmationToken());

        assertEquals(
                NamespacedStateRemovalResult.APPLIED,
                container.applyRemoval(authority, orphanPreview));
        assertFalse(container.toTag().contains(orphanId.toString()));
        assertTrue(container.toTag().contains(healthyId.toString()));
        assertTrue(container.toTag().contains(legacyId.toString()));
        assertEquals(2, container.read(healthy).orElseThrow()
                .storedVersion());

        NamespacedStateRemovalPreview invalidPreview =
                container.previewRemoval(
                        authority, "invalid entry").orElseThrow();
        assertEquals(
                Set.of(
                        NamespacedStateRemovalPreview.Issue.INVALID_NAME,
                        NamespacedStateRemovalPreview.Issue.MALFORMED),
                invalidPreview.issues());
        assertEquals(
                NamespacedStateRemovalResult.APPLIED,
                container.applyRemoval(authority, invalidPreview));
    }

    @Test
    void staleRemovalPreviewCannotDeleteRepairedState() {
        ResourceLocation scope =
                id("stale_removal_" + System.nanoTime());
        ResourceLocation keyId = id("entry_" + System.nanoTime());
        var key = NamespacedStateKeyRegistry.register(scope, keyId, 2);
        CompoundTag source = new CompoundTag();
        source.put(keyId.toString(), StringTag.valueOf("broken"));
        NamespacedStateContainer container =
                NamespacedStateContainer.fromTag(scope, source);
        var authority = new NamespacedStateMaintenance.Authority();
        NamespacedStateRemovalPreview preview =
                container.previewRemoval(
                        authority, keyId.toString()).orElseThrow();

        CompoundTag repaired = new CompoundTag();
        repaired.putString("owner", "keep");
        container.write(key, repaired);

        assertEquals(
                NamespacedStateRemovalResult.STALE,
                container.applyRemoval(authority, preview));
        assertEquals("keep", container.read(key).orElseThrow()
                .payload().getString("owner"));
        assertTrue(container.previewRemoval(
                authority, keyId.toString()).isEmpty());
    }

    private static CompoundTag entry(int version) {
        return entry(version, new CompoundTag());
    }

    private static CompoundTag entry(
            int version,
            CompoundTag payload
    ) {
        CompoundTag entry = new CompoundTag();
        entry.putInt(NamespacedStateCodec.VERSION, version);
        entry.put(NamespacedStateCodec.PAYLOAD, payload.copy());
        return entry;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                "stardewcraft_test", path);
    }
}
