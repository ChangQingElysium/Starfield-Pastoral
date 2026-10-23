package com.stardew.craft.platform.mixin.event.level.mixin;


import com.stardew.craft.platform.fabric.event.FabricEvents;
import com.stardew.craft.platform.fabric.event.level.ChunkWatchEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerChunkSender.class)
abstract class PlayerChunkSentMixin {
    @Inject(method = "sendChunk", at = @At("TAIL"))
    private static void stardewcraft$sent(ServerGamePacketListenerImpl connection, ServerLevel level, LevelChunk chunk,
            CallbackInfo ci) {
        FabricEvents.GAME.post(new ChunkWatchEvent.Sent(connection.player, chunk, level));
    }
}
