package com.stardew.craft.platform.mixin.light.mixin;


import com.stardew.craft.platform.fabric.light.AuxiliaryLights;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkSerializer.class)
abstract class AuxiliaryLightPersistenceMixin {
    @Inject(method = "read", at = @At("RETURN"), require = 1)
    private static void stardewcraft$readBeforeLighting(ServerLevel level, PoiManager poi, RegionStorageInfo storage,
            ChunkPos position, CompoundTag input, CallbackInfoReturnable<ProtoChunk> callback) {
        if (callback.getReturnValue() instanceof ImposterProtoChunk imposter) {
            AuxiliaryLights.owned(imposter.getWrapped()).loadChunkTag(input);
        }
    }

    @Inject(method = "write", at = @At("RETURN"), require = 1)
    private static void stardewcraft$writeChunkCache(ServerLevel level, ChunkAccess chunk,
            CallbackInfoReturnable<CompoundTag> callback) {
        LevelChunk owner = chunk instanceof LevelChunk loaded ? loaded : chunk instanceof ImposterProtoChunk imposter ? imposter.getWrapped() : null;
        if (owner != null) {
            AuxiliaryLights.owned(owner).saveInto(callback.getReturnValue());
        }
    }
}
