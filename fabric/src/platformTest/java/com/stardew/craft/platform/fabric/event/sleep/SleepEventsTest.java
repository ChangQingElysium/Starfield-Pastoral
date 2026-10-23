package com.stardew.craft.platform.fabric.event.sleep;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.datafixers.util.Either;
import com.stardew.craft.platform.fabric.event.EventBus;
import com.stardew.craft.platform.fabric.event.PlayerEvent;
import com.stardew.craft.platform.fabric.event.entity.living.LivingEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.player.Player.BedSleepingProblem;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SleepEventsTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test
    void everyVanillaRejectionMayBeOverriddenBeforeTheSingleRealSleepAndAwardTail() throws Exception {
        var player = player(); var state = Blocks.RED_BED.defaultBlockState(); var calls = new AtomicInteger();
        for (var failure : BedSleepingProblem.values()) {
            var bus = new EventBus(); List<String> order = new ArrayList<>();
            bus.addListener(CanPlayerSleepEvent.class, event -> {
                assertInstanceOf(PlayerEvent.class, event); assertSame(player, event.getEntity()); assertSame(state, event.getState());
                assertEquals(BlockPos.ZERO, event.getPos()); assertSame(failure, event.getVanillaProblem()); assertSame(failure, event.getProblem());
                assertEquals(0, calls.get()); event.setProblem(null); order.add("event");
            });
            assertTrue(FabricSleepEvents.attempt(bus, player, BlockPos.ZERO, state, Either.left(failure), () -> {
                order.add("start"); calls.incrementAndGet(); return Either.right(Unit.INSTANCE);
            }).right().isPresent());
            assertEquals(1, calls.get()); assertEquals(List.of("event", "start"), order); calls.set(0);
        }
    }

    @Test
    void aSuccessfulCandidateCanBeDeniedAndBaseFabricRejectionIsReturnedWithoutDuplicatingItsTail() throws Exception {
        var player = player(); var state = Blocks.RED_BED.defaultBlockState(); var bus = new EventBus();
        bus.addListener(CanPlayerSleepEvent.class, event -> { assertNull(event.getVanillaProblem()); event.setProblem(BedSleepingProblem.NOT_SAFE); });
        var denied = FabricSleepEvents.attempt(bus, player, BlockPos.ZERO, state, Either.right(Unit.INSTANCE), () -> { fail("denied attempt started"); return null; });
        assertSame(BedSleepingProblem.NOT_SAFE, denied.left().orElseThrow());
        var calls = new AtomicInteger();
        var baseDenied = FabricSleepEvents.attempt(new EventBus(), player, BlockPos.ZERO, state, Either.right(Unit.INSTANCE), () -> {
            calls.incrementAndGet(); return Either.left(BedSleepingProblem.OTHER_PROBLEM);
        });
        assertSame(BedSleepingProblem.OTHER_PROBLEM, baseDenied.left().orElseThrow()); assertEquals(1, calls.get());
    }

    @Test
    void continuationSupportsBothDirectionsWithBothBedAndDayProblemsAndLivingInheritance() throws Exception {
        var player = player(); var defaultBus = new EventBus();
        assertTrue(FabricSleepEvents.canContinue(defaultBus, player, null));
        assertFalse(FabricSleepEvents.canContinue(defaultBus, player, BedSleepingProblem.NOT_POSSIBLE_NOW));
        assertFalse(FabricSleepEvents.canContinue(defaultBus, player, BedSleepingProblem.NOT_POSSIBLE_HERE));
        var allow = new EventBus(); allow.addListener(CanContinueSleepingEvent.class, event -> { assertInstanceOf(LivingEvent.class, event); event.setContinueSleeping(true); });
        assertTrue(FabricSleepEvents.canContinue(allow, player, BedSleepingProblem.NOT_POSSIBLE_NOW));
        assertTrue(FabricSleepEvents.canContinue(allow, player, BedSleepingProblem.NOT_POSSIBLE_HERE));
        var deny = new EventBus(); deny.addListener(CanContinueSleepingEvent.class, event -> event.setContinueSleeping(false));
        assertFalse(FabricSleepEvents.canContinue(deny, player, null));
    }

    @Test
    void wakeFlagsRemainIndependentAndFinishedTimeRejectsOnlyTimesBeforeTheCurrentDay() throws Exception {
        for (boolean immediately : new boolean[] { false, true }) for (boolean update : new boolean[] { false, true }) {
            var event = new PlayerWakeUpEvent(player(), immediately, update);
            assertEquals(immediately, event.wakeImmediately()); assertEquals(update, event.updateLevel()); assertFalse(event.isCancelable());
        }
        var event = new SleepFinishedTimeEvent(null, 48000, 43000);
        assertFalse(event.setTimeAddition(42999)); assertEquals(48000, event.getNewTime());
        assertTrue(event.setTimeAddition(43000)); assertEquals(43000, event.getNewTime());
        assertTrue(event.setTimeAddition(72000)); assertEquals(72000, event.getNewTime());
    }

    private static ServerPlayer player() throws ReflectiveOperationException {
        var type = Class.forName("sun.misc.Unsafe"); var field = type.getDeclaredField("theUnsafe"); field.setAccessible(true);
        return (ServerPlayer) type.getMethod("allocateInstance", Class.class).invoke(field.get(null), ServerPlayer.class);
    }
}
