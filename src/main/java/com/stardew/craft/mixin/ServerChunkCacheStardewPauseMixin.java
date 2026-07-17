package com.stardew.craft.mixin;

import com.stardew.craft.time.StardewTimePauseService;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.TickRateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Freezes random/block chunk simulation without skipping {@code tickChunks()} itself.
 *
 * <p>The tail of vanilla {@code tickChunks()} calls {@code ChunkHolder.broadcastChanges()} for
 * every ticking chunk. Skipping the whole method lets block and light updates accumulate until
 * unpause, causing a large one-tick network/render spike after closing a menu.</p>
 */
@Mixin(ServerChunkCache.class)
public abstract class ServerChunkCacheStardewPauseMixin {

    @Redirect(
        method = "tickChunks",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/TickRateManager;runsNormally()Z"
        )
    )
    private boolean stardewcraft$freezeChunkSimulation(TickRateManager tickRateManager) {
        ServerChunkCache cache = (ServerChunkCache) (Object) this;
        return !StardewTimePauseService.shouldPauseLevel(cache.level)
            && tickRateManager.runsNormally();
    }
}
