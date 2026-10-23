package com.stardew.craft.platform.mixin.event.level.mixin;


import com.stardew.craft.platform.fabric.event.FabricEvents;
import com.stardew.craft.platform.fabric.event.level.ChunkWatchEvent;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkMap.class)
abstract class PlayerChunkWatchMixin {
    @Inject(method = "markChunkPendingToSend(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/chunk/LevelChunk;)V",
            at = @At("TAIL"))
    private static void stardewcraft$watch(ServerPlayer player, LevelChunk chunk, CallbackInfo ci) {
        FabricEvents.GAME.post(new ChunkWatchEvent.Watch(player, chunk, player.serverLevel()));
    }

    @Inject(method = "dropChunk", at = @At("HEAD"))
    private static void stardewcraft$unwatch(ServerPlayer player, ChunkPos pos, CallbackInfo ci) {
        FabricEvents.GAME.post(new ChunkWatchEvent.UnWatch(player, pos, player.serverLevel()));
    }
}
