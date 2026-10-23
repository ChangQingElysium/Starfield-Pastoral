package com.stardew.craft.platform.fabric.event.sleep;

import com.mojang.datafixers.util.Either;
import com.stardew.craft.platform.fabric.event.IEventBus;
import java.util.Objects;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Player.BedSleepingProblem;
import net.minecraft.world.level.block.state.BlockState;

public final class FabricSleepEvents {
    private static IEventBus installedBus;
    private FabricSleepEvents() {}
    public static synchronized void install(IEventBus bus) {
        Objects.requireNonNull(bus, "bus"); if (installedBus == bus) return;
        if (installedBus != null) throw new IllegalStateException("Sleep event bus already installed");
        installedBus = bus;
        EntitySleepEvents.ALLOW_SLEEP_TIME.register((player, pos, vanilla) -> {
            // FAPI invokes this API both for the initial ServerPlayer validation and the tick.
            // Only the tick is Neo's CanContinueSleeping stage; initial attempts use CanPlayerSleep.
            if (player.level().isClientSide || !player.isSleeping()) return InteractionResult.PASS;
            return canContinue(bus, player, vanilla ? null : BedSleepingProblem.NOT_POSSIBLE_NOW) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        });
    }
    public static IEventBus bus() {
        if (installedBus == null) throw new IllegalStateException("Sleep event bus is not installed"); return installedBus;
    }
    public static boolean canContinue(IEventBus bus, LivingEntity entity, BedSleepingProblem problem) {
        return bus.post(new CanContinueSleepingEvent(entity, problem)).mayContinueSleeping();
    }
    public static Either<BedSleepingProblem, Unit> attempt(ServerPlayer player, BlockPos pos, Either<BedSleepingProblem, Unit> candidate,
            Supplier<Either<BedSleepingProblem, Unit>> actualStart) {
        return attempt(bus(), player, pos, player.level().getBlockState(pos), candidate, actualStart);
    }
    static Either<BedSleepingProblem, Unit> attempt(IEventBus bus, ServerPlayer player, BlockPos pos, BlockState state,
            Either<BedSleepingProblem, Unit> candidate, Supplier<Either<BedSleepingProblem, Unit>> actualStart) {
        var event = bus.post(new CanPlayerSleepEvent(player, pos, state, candidate.left().orElse(null)));
        return event.getProblem() == null ? actualStart.get() : Either.left(event.getProblem());
    }
    public static void wake(Player player, boolean immediately, boolean update) { bus().post(new PlayerWakeUpEvent(player, immediately, update)); }
    public static long finished(ServerLevel level, long proposed) {
        return bus().post(new SleepFinishedTimeEvent(level, proposed, level.getDayTime())).getNewTime();
    }
}
