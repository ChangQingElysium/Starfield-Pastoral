package com.stardew.craft.platform.mixin.event.mixin;


import com.stardew.craft.platform.fabric.event.ChunkLoadState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Disk-loaded FULL chunks use another constructor; conversion from ProtoChunk is newly generated. */
@Mixin(LevelChunk.class)
public abstract class LevelChunkGenerationMixin implements ChunkLoadState {
    @Unique
    private boolean stardewcraft$newlyGenerated;

    @Inject(method = "<init>(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ProtoChunk;Lnet/minecraft/world/level/chunk/LevelChunk$PostLoadProcessor;)V", at = @At("RETURN"))
    private void stardewcraft$markGenerated(ServerLevel level, ProtoChunk protoChunk,
            LevelChunk.PostLoadProcessor processor, CallbackInfo callback) {
        stardewcraft$newlyGenerated = !(protoChunk instanceof ImposterProtoChunk);
    }

    @Override
    public boolean stardewcraft$consumeNewlyGenerated() {
        boolean generated = stardewcraft$newlyGenerated;
        stardewcraft$newlyGenerated = false;
        return generated;
    }
}
