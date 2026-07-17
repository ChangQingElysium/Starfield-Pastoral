package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.stardew.craft.time.StardewTimePauseService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTickList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Consumer;

/**
 * Freezes farm/mine gameplay simulation while preserving chunk IO, unloading and entity storage
 * maintenance. This mirrors the useful part of vanilla tick freeze without freezing other worlds.
 */
@Mixin(ServerLevel.class)
@SuppressWarnings("deprecation") // EntityTickList is the vanilla 1.21.1 entity-tick call site.
public abstract class ServerLevelStardewPauseMixin {

    @Redirect(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/TickRateManager;runsNormally()Z"
        )
    )
    private boolean stardewcraft$freezeTimedSimulation(TickRateManager tickRateManager) {
        return !stardewcraft$isPaused() && tickRateManager.runsNormally();
    }

    @WrapWithCondition(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/entity/EntityTickList;forEach(Ljava/util/function/Consumer;)V"
        )
    )
    private boolean stardewcraft$skipEntitySimulation(EntityTickList entities, Consumer<Entity> ticker) {
        return !stardewcraft$isPaused();
    }

    @WrapWithCondition(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;tickBlockEntities()V"
        )
    )
    private boolean stardewcraft$skipBlockEntitySimulation(ServerLevel level) {
        return !stardewcraft$isPaused();
    }

    private boolean stardewcraft$isPaused() {
        return StardewTimePauseService.shouldPauseLevel((ServerLevel) (Object) this);
    }
}
