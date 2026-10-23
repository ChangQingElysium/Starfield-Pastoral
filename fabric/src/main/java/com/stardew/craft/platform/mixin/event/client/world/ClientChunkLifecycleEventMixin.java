package com.stardew.craft.platform.mixin.event.client.world;

import com.llamalad7.mixinextras.sugar.Local;
import com.stardew.craft.platform.fabric.event.client.FabricClientWorldEvents;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientChunkCache.class)
public abstract class ClientChunkLifecycleEventMixin {
    @Shadow @Final private ClientLevel level;

    @Inject(method = "replaceWithPacketData", at = @At("RETURN"), require = 1)
    private void stardewcraft$clientChunkLoaded(CallbackInfoReturnable<LevelChunk> callback) {
        // Valid full packets (including updates of an existing chunk) run onChunkLoaded before returning.
        FabricClientWorldEvents.chunkLoaded(level, callback.getReturnValue());
    }

    @Inject(method = "drop", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/multiplayer/ClientChunkCache$Storage;replace(ILnet/minecraft/world/level/chunk/LevelChunk;Lnet/minecraft/world/level/chunk/LevelChunk;)Lnet/minecraft/world/level/chunk/LevelChunk;"), require = 1)
    private void stardewcraft$clientChunkUnloading(CallbackInfo callback, @Local LevelChunk chunk) {
        // Both native coordinate/range guards have passed and the chunk is still in storage.
        // FAPI also emits unload for slot replacement/view-radius shrink; Neo did not, so keep this exact point.
        FabricClientWorldEvents.chunkUnloading(level, chunk);
    }
}
