package com.stardew.craft.platform.mixin.light.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.platform.fabric.light.AuxiliaryLights;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerChunkSender.class)
abstract class AuxiliaryLightChunkSendMixin {
    @WrapOperation(method = "sendChunk", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V"), require = 1)
    private static void stardewcraft$sendCacheAfterNativeChunk(ServerGamePacketListenerImpl target, Packet<?> packet,
            Operation<Void> original, ServerGamePacketListenerImpl listener, ServerLevel level, LevelChunk chunk) {
        if (!(packet instanceof ClientboundLevelChunkWithLightPacket chunkPacket)) throw new IllegalStateException("Unexpected native chunk packet");
        original.call(target, AuxiliaryLights.bundle(chunk, chunkPacket));
    }
}
