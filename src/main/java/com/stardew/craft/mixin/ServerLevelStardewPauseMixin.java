package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.stardew.craft.time.StardewTimePauseService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.raid.Raids;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraft.world.ticks.LevelTicks;
import java.util.function.BiConsumer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTickList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Consumer;

/**
 * Freezes farm/mine gameplay simulation while preserving chunk IO, unloading and entity storage
 * maintenance. This mirrors the useful part of vanilla tick freeze without freezing other worlds.
 */
@Mixin(ServerLevel.class)
@SuppressWarnings("deprecation") // EntityTickList is the vanilla 1.21.1 entity-tick call site.
public abstract class ServerLevelStardewPauseMixin {

    // PORT(1.20.1): 1.21 gates world border, weather, time, scheduled block/fluid ticks, raids, block events and
    // the dragon fight behind one TickRateManager#runsNormally() call, which this mixin redirected. 1.20.1 has no
    // tick rate manager, so each of those calls is wrapped with the same pause condition.
    @WrapWithCondition(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/border/WorldBorder;tick()V"))
    private boolean stardewcraft$freezeWorldBorder(WorldBorder border) {
        return !stardewcraft$isPaused();
    }

    @WrapWithCondition(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;advanceWeatherCycle()V"))
    private boolean stardewcraft$freezeWeather(ServerLevel level) {
        return !stardewcraft$isPaused();
    }

    @WrapWithCondition(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;tickTime()V"))
    private boolean stardewcraft$freezeTime(ServerLevel level) {
        return !stardewcraft$isPaused();
    }

    @WrapWithCondition(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/ticks/LevelTicks;tick(JILjava/util/function/BiConsumer;)V"))
    private boolean stardewcraft$freezeScheduledTicks(LevelTicks<?> ticks, long gameTime, int maxTicks,
            BiConsumer<?, ?> ticker) {
        return !stardewcraft$isPaused();
    }

    @WrapWithCondition(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/raid/Raids;tick()V"))
    private boolean stardewcraft$freezeRaids(Raids raids) {
        return !stardewcraft$isPaused();
    }

    @WrapWithCondition(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;runBlockEvents()V"))
    private boolean stardewcraft$freezeBlockEvents(ServerLevel level) {
        return !stardewcraft$isPaused();
    }

    @WrapWithCondition(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/dimension/end/EndDragonFight;tick()V"))
    private boolean stardewcraft$freezeDragonFight(EndDragonFight fight) {
        return !stardewcraft$isPaused();
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
