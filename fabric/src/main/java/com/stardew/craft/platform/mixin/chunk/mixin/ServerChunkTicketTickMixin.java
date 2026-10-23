package com.stardew.craft.platform.mixin.chunk.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.platform.fabric.chunk.TicketControllers;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerChunkCache.class)
abstract class ServerChunkTicketTickMixin {
    @Shadow @Final private ServerLevel level;
    // (a || forced) && (b || forced) equals vanilla's (a && b) with Neo's force-ticking disjunct.
    @WrapOperation(method = "tickChunks", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;isNaturalSpawningAllowed(Lnet/minecraft/world/level/ChunkPos;)Z"), require = 1)
    private boolean stardewcraft$forcedChunksPastTickingGate(ServerLevel level, ChunkPos pos, Operation<Boolean> original) {
        return original.call(level, pos) || TicketControllers.shouldForceTicks(level, pos.toLong());
    }
    @WrapOperation(method = "tickChunks", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkMap;anyPlayerCloseEnoughForSpawning(Lnet/minecraft/world/level/ChunkPos;)Z"), require = 1)
    private boolean stardewcraft$forcedChunksWithoutNearbyPlayers(ChunkMap map, ChunkPos pos, Operation<Boolean> original) {
        return original.call(map, pos) || TicketControllers.shouldForceTicks(level, pos.toLong());
    }
}
