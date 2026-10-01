package com.stardew.craft.mixin;

import com.stardew.craft.block.terrain.TerrainWorldUpgrade;
import com.stardew.craft.core.ModDimensions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkSerializer.class)
public abstract class ChunkSerializerTerrainVariantsMixin {
    @Inject(method = "read", at = @At("HEAD"))
    private static void stardewcraft$upgradeTerrain(ServerLevel level, PoiManager poi, // PORT(1.20.1): no RegionStorageInfo
            ChunkPos pos, CompoundTag tag, CallbackInfoReturnable<ProtoChunk> callback) {
        if (level.dimension().equals(ModDimensions.STARDEW_VALLEY)) TerrainWorldUpgrade.upgrade(tag, level.getSeed());
    }

    @Inject(method = "write", at = @At("RETURN"))
    private static void stardewcraft$persistTerrainVersion(ServerLevel level, ChunkAccess chunk,
            CallbackInfoReturnable<CompoundTag> callback) {
        if (level.dimension().equals(ModDimensions.STARDEW_VALLEY))
            callback.getReturnValue().putInt(TerrainWorldUpgrade.VERSION_KEY, TerrainWorldUpgrade.VERSION);
    }
}
