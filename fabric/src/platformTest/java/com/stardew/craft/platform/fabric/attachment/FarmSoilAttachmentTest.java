package com.stardew.craft.platform.fabric.attachment;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.impl.attachment.AttachmentSerializingImpl;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FarmSoilAttachmentTest {
    private static final String KEY = FarmSoilMigrationAttachments.COMPLETED_ID.toString();
    private static final String BACKUP = FarmSoilMigrationAttachments.LEGACY_BACKUP_ID.toString();
    private static final String FABRIC = AttachmentTarget.NBT_ATTACHMENT_KEY;
    private static final String NEO = LegacyChunkAttachmentCompat.NEOFORGE_ROOT;
    private static final UUID A = UUID.fromString("12345678-1234-4234-8234-123456789001");
    private static final UUID B = UUID.fromString("12345678-1234-4234-8234-123456789002");
    private static final UUID C = UUID.fromString("12345678-1234-4234-8234-123456789003");

    @BeforeAll
    static void registerActualFabricTypes() { FarmSoilMigrationAttachments.initialize(); }

    @Test
    void officialRegistrationRetainsTheStableIdentifierCodecAndImmutableDefault() {
        AttachmentType<List<UUID>> type = FarmSoilMigrationAttachments.completedType();
        assertEquals("stardewcraft:farm_soil_migration_v1", type.identifier().toString());
        assertTrue(type.isPersistent());
        assertSame(FarmSoilMigrationAttachments.COMPLETED_CODEC, type.persistenceCodec());
        assertFalse(type.isSynced());
        assertEquals(List.of(), type.initializer().get());
        Fixture chunk = new Fixture(new CompoundTag());
        assertEquals(List.of(), FarmSoilMigrationAttachments.completed(chunk).orElseThrow());
        assertEquals(1, chunk.dirty);
        assertThrows(UnsupportedOperationException.class,
                () -> FarmSoilMigrationAttachments.completed(chunk).orElseThrow().add(A));
        assertEquals(1, chunk.dirty);
    }

    @Test
    void oldNeoForgeFlagIsImportedBeforeOfficialFabricDeserializationAndUnknownDataSurvives() {
        CompoundTag original = oldChunk(List.of(A));
        CompoundTag legacyBefore = original.getCompound(NEO).copy();
        CompoundTag originalBlocks = new CompoundTag();
        originalBlocks.putString("owner_edit", "keep");
        original.put("block_entities", originalBlocks);
        LegacyChunkAttachmentCompat.prepareChunkRead(original);
        assertEquals(legacyBefore, original.getCompound(NEO));
        assertEquals(originalBlocks, original.getCompound("block_entities"));
        Fixture loaded = new Fixture(original);
        assertEquals(List.of(A), FarmSoilMigrationAttachments.completed(loaded).orElseThrow());
        assertEquals(0, loaded.dirty);
        FarmSoilMigrationAttachments.setCompleted(loaded, List.of(A, B));
        assertEquals(1, loaded.dirty);
        CompoundTag saved = loaded.save(true);
        assertEquals(List.of(A, B), completed(saved.getCompound(NEO)));
        assertEquals(legacyBefore.get("other_mod:opaque"), saved.getCompound(NEO).get("other_mod:opaque"));
        LegacyChunkAttachmentCompat.prepareChunkRead(saved);
        assertEquals(List.of(A, B), FarmSoilMigrationAttachments.completed(new Fixture(saved)).orElseThrow());
    }

    @Test
    void theLegacyWriteDoesNotDependOnFabricReturnInjectionOrdering() {
        CompoundTag input = oldChunk(List.of(A));
        LegacyChunkAttachmentCompat.prepareChunkRead(input);
        Fixture chunk = new Fixture(input);
        FarmSoilMigrationAttachments.setCompleted(chunk, List.of(A, B));
        assertEquals(chunk.save(true), chunk.save(false));
    }

    @Test
    void modernAndLegacyListsUseAStableUnionAndPreparationIsIdempotent() {
        CompoundTag input = oldChunk(List.of(A, B));
        CompoundTag modern = new CompoundTag();
        modern.put(KEY, LegacyChunkAttachmentCompat.encodeCompletion(List.of(B, C)));
        input.put(FABRIC, modern);
        LegacyChunkAttachmentCompat.prepareChunkRead(input);
        assertEquals(List.of(B, C, A), completed(input.getCompound(FABRIC)));
        CompoundTag once = input.copy();
        LegacyChunkAttachmentCompat.prepareChunkRead(input);
        assertEquals(once, input);
        assertEquals(List.of(B, C, A), FarmSoilMigrationAttachments.completed(new Fixture(input)).orElseThrow());
    }

    @Test
    void aPureFabricChunkKeepsItsOfficialFormatWithoutInventingAnOldContainer() {
        CompoundTag input = new CompoundTag();
        CompoundTag modern = new CompoundTag();
        modern.put(KEY, LegacyChunkAttachmentCompat.encodeCompletion(List.of(A)));
        input.put(FABRIC, modern);
        LegacyChunkAttachmentCompat.prepareChunkRead(input);
        assertFalse(input.getCompound(FABRIC).contains(BACKUP));
        Fixture chunk = new Fixture(input);
        FarmSoilMigrationAttachments.setCompleted(chunk, List.of(A, B));
        CompoundTag saved = chunk.save(true);
        assertFalse(saved.contains(NEO));
        assertEquals(List.of(A, B), completed(saved.getCompound(FABRIC)));
    }

    @Test
    void anEmptyChunkIsNotModifiedByTheCompatibilityReader() {
        CompoundTag input = new CompoundTag();
        input.putInt("xPos", 25);
        CompoundTag before = input.copy();
        LegacyChunkAttachmentCompat.prepareChunkRead(input);
        assertEquals(before, input);
    }

    @Test
    void persistedOfficialBackupRecoversTheOldContainerIfAnEarlierFabricSaveOmittedIt() {
        CompoundTag input = oldChunk(List.of(A));
        CompoundTag expected = input.getCompound(NEO).copy();
        LegacyChunkAttachmentCompat.prepareChunkRead(input);
        Fixture first = new Fixture(input);
        CompoundTag withoutOldRoot = new CompoundTag();
        first.writeOfficial(withoutOldRoot);
        assertFalse(withoutOldRoot.contains(NEO));
        LegacyChunkAttachmentCompat.prepareChunkRead(withoutOldRoot);
        Fixture second = new Fixture(withoutOldRoot);
        assertEquals(List.of(A), FarmSoilMigrationAttachments.completed(second).orElseThrow());
        assertEquals(expected, second.save(true).getCompound(NEO));
    }

    @Test
    void corruptLegacyCompletionBlocksReconversionAndRetainsItsExactBytes() {
        CompoundTag input = oldChunk(List.of(A));
        Tag invalid = StringTag.valueOf("not-a-uuid-list");
        input.getCompound(NEO).put(KEY, invalid);
        LegacyChunkAttachmentCompat.prepareChunkRead(input);
        Fixture chunk = new Fixture(input);
        assertTrue(FarmSoilMigrationAttachments.completed(chunk).isEmpty());
        assertEquals(0, chunk.dirty);
        assertThrows(IllegalStateException.class, () -> FarmSoilMigrationAttachments.setCompleted(chunk, List.of()));
        CompoundTag saved = chunk.save(true);
        assertEquals(invalid, saved.getCompound(NEO).get(KEY));
        assertEquals(input.getCompound(NEO).get("other_mod:opaque"), saved.getCompound(NEO).get("other_mod:opaque"));
        LegacyChunkAttachmentCompat.prepareChunkRead(saved);
        assertTrue(FarmSoilMigrationAttachments.completed(new Fixture(saved)).isEmpty());
    }

    @Test
    void corruptModernCompletionIsPreservedInTheOfficialBackupAndStillBlocksAfterRestart() {
        CompoundTag input = new CompoundTag();
        CompoundTag modern = new CompoundTag();
        modern.put(KEY, StringTag.valueOf("unreadable"));
        input.put(FABRIC, modern);
        LegacyChunkAttachmentCompat.prepareChunkRead(input);
        Fixture chunk = new Fixture(input); // Fabric skips the invalid typed value, retaining the backup
        assertTrue(FarmSoilMigrationAttachments.completed(chunk).isEmpty());
        CompoundTag saved = chunk.save(true);
        assertTrue(saved.getCompound(FABRIC).contains(BACKUP));
        LegacyChunkAttachmentCompat.prepareChunkRead(saved);
        assertEquals(StringTag.valueOf("unreadable"), saved.getCompound(FABRIC).get(KEY));
        assertTrue(FarmSoilMigrationAttachments.completed(new Fixture(saved)).isEmpty());
    }

    @Test
    void malformedOldOuterContainerIsRetainedAndNeverBecomesAnEmptyCompletionSet() {
        CompoundTag input = new CompoundTag();
        input.put(NEO, IntTag.valueOf(55));
        LegacyChunkAttachmentCompat.prepareChunkRead(input);
        Fixture chunk = new Fixture(input);
        assertTrue(FarmSoilMigrationAttachments.completed(chunk).isEmpty());
        assertEquals(IntTag.valueOf(55), chunk.save(true).get(NEO));
    }

    @Test
    void unreadableFabricOuterContainerRemainsBlockedAfterAnOfficialSaveAndRestart() {
        CompoundTag input = new CompoundTag();
        input.put(FABRIC, StringTag.valueOf("unreadable attachment root"));
        LegacyChunkAttachmentCompat.prepareChunkRead(input);
        Fixture chunk = new Fixture(input);
        assertTrue(FarmSoilMigrationAttachments.completed(chunk).isEmpty());
        CompoundTag saved = chunk.save(true);
        LegacyChunkAttachmentCompat.prepareChunkRead(saved);
        assertTrue(FarmSoilMigrationAttachments.completed(new Fixture(saved)).isEmpty());
        assertTrue(saved.getCompound(FABRIC).getCompound(BACKUP).contains("unreadable_fabric_root"));
    }

    @Test
    void unreadableCompatibilityBackupCannotSilentlyResetCompletionState() {
        CompoundTag input = new CompoundTag();
        CompoundTag fabric = new CompoundTag();
        fabric.put(BACKUP, StringTag.valueOf("unreadable previous backup"));
        input.put(FABRIC, fabric);
        LegacyChunkAttachmentCompat.prepareChunkRead(input);
        Fixture chunk = new Fixture(input);
        assertTrue(FarmSoilMigrationAttachments.completed(chunk).isEmpty());
        CompoundTag saved = chunk.save(true);
        LegacyChunkAttachmentCompat.prepareChunkRead(saved);
        assertTrue(FarmSoilMigrationAttachments.completed(new Fixture(saved)).isEmpty());
    }

    @Test
    void newCompletionValuesAreCopiedSoCallerMutationCannotEraseSavedFlags() {
        Fixture chunk = new Fixture(new CompoundTag());
        List<UUID> values = new ArrayList<>(List.of(A));
        FarmSoilMigrationAttachments.setCompleted(chunk, values);
        values.clear();
        assertEquals(List.of(A), FarmSoilMigrationAttachments.completed(chunk).orElseThrow());
        assertEquals(List.of(A), completed(chunk.save(true).getCompound(FABRIC)));
    }

    private static List<UUID> completed(CompoundTag container) {
        return LegacyChunkAttachmentCompat.decodeCompletion(container.get(KEY));
    }

    private static CompoundTag oldChunk(List<UUID> flags) {
        CompoundTag legacy = new CompoundTag();
        legacy.put(KEY, LegacyChunkAttachmentCompat.encodeCompletion(flags));
        CompoundTag opaque = new CompoundTag();
        opaque.putString("unknown", "retained");
        opaque.putIntArray("data", new int[] {1, -8, Integer.MAX_VALUE});
        legacy.put("other_mod:opaque", opaque);
        CompoundTag root = new CompoundTag();
        root.put(NEO, legacy);
        return root;
    }

    /** Uses the real Fabric serializer/registry; only the live chunk target is replaced for a unit test. */
    private static final class Fixture implements AttachmentTarget {
        private final IdentityHashMap<AttachmentType<?>, Object> attachments;
        int dirty;

        Fixture(CompoundTag nbt) {
            var loaded = AttachmentSerializingImpl.deserializeAttachmentData(nbt, RegistryAccess.EMPTY);
            attachments = loaded == null ? new IdentityHashMap<>() : loaded;
        }

        @SuppressWarnings("unchecked")
        @Override public <A> A getAttached(AttachmentType<A> type) { return (A) attachments.get(type); }

        @SuppressWarnings("unchecked")
        @Override public <A> A setAttached(AttachmentType<A> type, A value) {
            dirty++;
            return (A) (value == null ? attachments.remove(type) : attachments.put(type, value));
        }

        @Override public boolean hasAttached(AttachmentType<?> type) { return attachments.containsKey(type); }

        void writeOfficial(CompoundTag output) {
            AttachmentSerializingImpl.serializeAttachmentData(output, RegistryAccess.EMPTY, attachments);
        }

        CompoundTag save(boolean officialFirst) {
            CompoundTag output = new CompoundTag();
            if (officialFirst) writeOfficial(output);
            FarmSoilMigrationAttachments.writeLegacyContainer(this, output);
            if (!officialFirst) writeOfficial(output);
            return output;
        }
    }
}
