package com.stardew.craft.platform.fabric.attachment;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Pure NBT compatibility at chunk I/O; unknown NeoForge attachment values are preserved verbatim. */
public final class LegacyChunkAttachmentCompat {
    public static final String NEOFORGE_ROOT = "neoforge:attachments";
    private static final String FABRIC_ROOT = AttachmentTarget.NBT_ATTACHMENT_KEY;
    static final String LEGACY = "neoforge";
    static final String BLOCKED = "soil_migration_blocked";
    private static final String INVALID_FABRIC = "unreadable_fabric_completion";
    private static final String INVALID_FABRIC_ROOT = "unreadable_fabric_root";
    private static final String INVALID_BACKUP = "unreadable_compatibility_backup";
    private static final String COMPLETED = FarmSoilMigrationAttachments.COMPLETED_ID.toString();
    private static final String BACKUP = FarmSoilMigrationAttachments.LEGACY_BACKUP_ID.toString();
    private static final Logger LOGGER = LoggerFactory.getLogger("stardewcraft/fabric-attachments");

    /** Runs before Fabric's own chunk constructor/deserializer consumes fabric:attachments. */
    public static void prepareChunkRead(CompoundTag input) {
        FarmSoilMigrationAttachments.initialize();
        Tag fabricRoot = input.get(FABRIC_ROOT);
        CompoundTag fabric = fabricRoot instanceof CompoundTag compound ? compound.copy() : new CompoundTag();
        Tag rawBackup = fabric.get(BACKUP);
        CompoundTag backup = fabric.getCompound(BACKUP).copy();
        if (fabricRoot != null && !(fabricRoot instanceof CompoundTag)) {
            backup.put(INVALID_FABRIC_ROOT, fabricRoot.copy());
        }
        if (rawBackup != null && !(rawBackup instanceof CompoundTag)) {
            backup.put(INVALID_BACKUP, rawBackup.copy());
        }
        boolean invalidContainer = backup.contains(INVALID_FABRIC_ROOT) || backup.contains(INVALID_BACKUP);

        // Prefer the current on-disk root when it exists. Retain backup-only attachment ids
        // after a prior Fabric save has omitted the original NeoForge root.
        Tag legacy = mergeLegacyRoots(input.get(NEOFORGE_ROOT), backup.get(LEGACY));
        if (legacy != null) backup.put(LEGACY, legacy.copy());

        Tag fabricCompletion = fabric.get(COMPLETED);
        if (fabricCompletion == null && backup.contains(INVALID_FABRIC)) {
            fabricCompletion = backup.get(INVALID_FABRIC).copy();
            fabric.put(COMPLETED, fabricCompletion);
        }
        Tag legacyCompletion = legacy instanceof CompoundTag compound ? compound.get(COMPLETED) : null;
        List<UUID> modern = decodeCompletion(fabricCompletion);
        List<UUID> old = decodeCompletion(legacyCompletion);
        boolean blocked = invalidContainer || (legacy != null && !(legacy instanceof CompoundTag))
                || modern == null || old == null;
        if (blocked) {
            backup.putBoolean(BLOCKED, true);
            if (fabricCompletion != null && modern == null) backup.put(INVALID_FABRIC, fabricCompletion.copy());
            LOGGER.error("Unreadable chunk attachment completion metadata; soil migration is blocked. "
                    + "The original data is retained in {}", BACKUP);
        } else {
            backup.remove(BLOCKED);
            backup.remove(INVALID_FABRIC);
            if (fabricCompletion != null || legacyCompletion != null) {
                LinkedHashSet<UUID> union = new LinkedHashSet<>(modern);
                union.addAll(old);
                fabric.put(COMPLETED, encodeCompletion(List.copyOf(union)));
            }
        }
        if (!backup.isEmpty()) fabric.put(BACKUP, backup);
        // Leave a clean chunk with no attachment data untouched.
        if (fabricRoot != null || !fabric.isEmpty()) input.put(FABRIC_ROOT, fabric);
    }

    static void writeLegacyContainer(CompoundTag output, CompoundTag backup, List<UUID> completed) {
        if (backup == null || !backup.contains(LEGACY)) return;
        Tag saved = backup.get(LEGACY).copy();
        if (saved instanceof CompoundTag legacy && !backup.getBoolean(BLOCKED) && completed != null) {
            legacy.put(COMPLETED, encodeCompletion(completed));
        }
        output.put(NEOFORGE_ROOT, saved);
    }

    private static Tag mergeLegacyRoots(Tag current, Tag retained) {
        if (current == null) return retained == null ? null : retained.copy();
        if (!(current instanceof CompoundTag present) || !(retained instanceof CompoundTag previous)) {
            return current.copy();
        }
        CompoundTag result = present.copy();
        for (String key : previous.getAllKeys()) {
            if (!result.contains(key)) result.put(key, previous.get(key).copy());
        }
        return result;
    }

    /** A null return means unreadable data; an absent value is a valid empty completion set. */
    static List<UUID> decodeCompletion(Tag value) {
        if (value == null) return List.of();
        return FarmSoilMigrationAttachments.COMPLETED_CODEC.parse(NbtOps.INSTANCE, value)
                .result().map(List::copyOf).orElse(null);
    }

    static Tag encodeCompletion(List<UUID> values) {
        return FarmSoilMigrationAttachments.COMPLETED_CODEC.encodeStart(NbtOps.INSTANCE, values).getOrThrow();
    }

    private LegacyChunkAttachmentCompat() {}
}
