package com.stardew.craft.platform.fabric.attachment;

import com.mojang.serialization.Codec;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/** The single real attachment consumer in the current upstream mod, registered with Fabric API. */
public final class FarmSoilMigrationAttachments {
    public static final ResourceLocation COMPLETED_ID = ResourceLocation.parse("stardewcraft:farm_soil_migration_v1");
    public static final ResourceLocation LEGACY_BACKUP_ID =
            ResourceLocation.parse("stardewcraft:legacy_neoforge_chunk_attachments");
    static final Codec<List<UUID>> COMPLETED_CODEC = UUIDUtil.CODEC.listOf();

    private static final class Types {
        static final AttachmentType<List<UUID>> COMPLETED = AttachmentRegistry.<List<UUID>>create(COMPLETED_ID,
                builder -> builder.initializer(List::of).persistent(COMPLETED_CODEC));
        static final AttachmentType<CompoundTag> LEGACY_BACKUP = AttachmentRegistry.<CompoundTag>create(
                LEGACY_BACKUP_ID, builder -> builder.persistent(CompoundTag.CODEC));
    }

    /** Call during common initialization, before the first chunk is deserialized. */
    public static void initialize() {
        Types.COMPLETED.identifier();
        Types.LEGACY_BACKUP.identifier();
    }

    /** Unreadable completion metadata must never be mistaken for an unconverted chunk. */
    public static Optional<List<UUID>> completed(AttachmentTarget chunk) {
        CompoundTag backup = chunk.getAttached(Types.LEGACY_BACKUP);
        if (backup != null && backup.getBoolean(LegacyChunkAttachmentCompat.BLOCKED)) return Optional.empty();
        return Optional.of(List.copyOf(chunk.getAttachedOrCreate(Types.COMPLETED)));
    }

    public static void setCompleted(AttachmentTarget chunk, List<UUID> completed) {
        CompoundTag backup = chunk.getAttached(Types.LEGACY_BACKUP);
        if (backup != null && backup.getBoolean(LegacyChunkAttachmentCompat.BLOCKED)) {
            throw new IllegalStateException("Unreadable chunk completion metadata blocks soil migration");
        }
        // Fabric's setter marks ChunkAccess unsaved; the original migration also marks it
        // explicitly after its block writes, retaining the shared chunk-save boundary.
        chunk.setAttached(Types.COMPLETED, List.copyOf(completed));
    }

    public static void writeLegacyContainer(AttachmentTarget chunk, CompoundTag output) {
        LegacyChunkAttachmentCompat.writeLegacyContainer(output, chunk.getAttached(Types.LEGACY_BACKUP),
                chunk.getAttached(Types.COMPLETED));
    }

    static AttachmentType<List<UUID>> completedType() { return Types.COMPLETED; }
    static AttachmentType<CompoundTag> backupType() { return Types.LEGACY_BACKUP; }

    private FarmSoilMigrationAttachments() {}
}
