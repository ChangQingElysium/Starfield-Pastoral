package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.stardew.craft.time.StardewTimePauseService;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Freezes random/block chunk simulation without skipping {@code tickChunks()} itself.
 *
 * <p>The tail of vanilla {@code tickChunks()} calls {@code ChunkHolder.broadcastChanges()} for
 * every ticking chunk. Skipping the whole method lets block and light updates accumulate until
 * unpause, causing a large one-tick network/render spike after closing a menu.</p>
 *
 * <p>PORT(1.20.1): 1.21 gates inhabited time, natural spawning, random ticks and custom spawners
 * behind {@code TickRateManager#runsNormally()}, which this mixin redirected. 1.20.1 has no tick
 * rate manager, so each of those calls is wrapped with the same pause condition instead.</p>
 */
@Mixin(ServerChunkCache.class)
public abstract class ServerChunkCacheStardewPauseMixin {

    @WrapWithCondition(method = "tickChunks", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/LevelChunk;incrementInhabitedTime(J)V"))
    private boolean stardewcraft$freezeInhabitedTime(LevelChunk chunk, long amount) {
        return stardewcraft$runsNormally();
    }

    @WrapWithCondition(method = "tickChunks", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/NaturalSpawner;spawnForChunk(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/LevelChunk;Lnet/minecraft/world/level/NaturalSpawner$SpawnState;ZZZ)V"))
    private boolean stardewcraft$freezeNaturalSpawning(ServerLevel level, LevelChunk chunk,
            NaturalSpawner.SpawnState state, boolean spawnFriendlies, boolean spawnEnemies, boolean spawnPersistent) {
        return stardewcraft$runsNormally();
    }

    @WrapWithCondition(method = "tickChunks", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;tickChunk(Lnet/minecraft/world/level/chunk/LevelChunk;I)V"))
    private boolean stardewcraft$freezeChunkSimulation(ServerLevel level, LevelChunk chunk, int randomTickSpeed) {
        return stardewcraft$runsNormally();
    }

    @WrapWithCondition(method = "tickChunks", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;tickCustomSpawners(ZZ)V"))
    private boolean stardewcraft$freezeCustomSpawners(ServerLevel level, boolean spawnEnemies, boolean spawnFriendlies) {
        return stardewcraft$runsNormally();
    }

    private boolean stardewcraft$runsNormally() {
        return !StardewTimePauseService.shouldPauseLevel(((ServerChunkCache) (Object) this).level);
    }
}
