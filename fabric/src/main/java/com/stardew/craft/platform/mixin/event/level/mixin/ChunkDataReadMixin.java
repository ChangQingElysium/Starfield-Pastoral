package com.stardew.craft.platform.mixin.event.level.mixin;


import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.stardew.craft.platform.fabric.event.FabricEvents;
import com.stardew.craft.platform.fabric.event.level.ChunkDataEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkType;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ChunkSerializer.class)
public abstract class ChunkDataReadMixin {
    @ModifyReturnValue(method = "read", at = @At("RETURN"))
    private static ProtoChunk stardewcraft$loadedData(ProtoChunk result, ServerLevel level, PoiManager poi,
            RegionStorageInfo storage, ChunkPos pos, CompoundTag data) {
        var chunk = result instanceof ImposterProtoChunk imposter ? imposter.getWrapped() : result;
        var type = result instanceof ImposterProtoChunk ? ChunkType.LEVELCHUNK : ChunkType.PROTOCHUNK;
        // Neo posts after palette decoding and data application, including for full chunks.
        FabricEvents.GAME.post(new ChunkDataEvent.Load(chunk, level, data, type));
        return result;
    }
}
