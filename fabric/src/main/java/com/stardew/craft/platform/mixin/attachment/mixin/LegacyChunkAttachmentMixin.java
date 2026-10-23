package com.stardew.craft.platform.mixin.attachment.mixin;


import com.stardew.craft.platform.fabric.attachment.FarmSoilMigrationAttachments;
import com.stardew.craft.platform.fabric.attachment.LegacyChunkAttachmentCompat;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Preserves old chunk data while the actual attachment values use Fabric's persistence API. */
@Mixin(ChunkSerializer.class)
public abstract class LegacyChunkAttachmentMixin {
    @Inject(method = "read", at = @At("HEAD"))
    private static void stardewcraft$readNeoForgeAttachments(ServerLevel level, PoiManager poi,
            RegionStorageInfo storage, ChunkPos position, CompoundTag input,
            CallbackInfoReturnable<ProtoChunk> callback) {
        LegacyChunkAttachmentCompat.prepareChunkRead(input);
    }

    @Inject(method = "write", at = @At("RETURN"))
    private static void stardewcraft$retainNeoForgeAttachments(ServerLevel level, ChunkAccess chunk,
            CallbackInfoReturnable<CompoundTag> callback) {
        // Read the official attachment target directly. This does not depend on the order
        // of our RETURN callback relative to Fabric's own attachment serialization callback.
        FarmSoilMigrationAttachments.writeLegacyContainer((AttachmentTarget) chunk, callback.getReturnValue());
    }
}
