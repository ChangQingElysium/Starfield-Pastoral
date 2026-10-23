package com.stardew.craft.platform.fabric.event;

import static org.junit.jupiter.api.Assertions.*;

import com.stardew.craft.platform.fabric.event.entity.living.MobEffectEvent;
import com.stardew.craft.platform.fabric.event.entity.living.MobEffectHooks;
import com.stardew.craft.platform.fabric.event.entity.player.MiningSpeedHooks;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

class EffectAndMiningHooksTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void effectDecisionPreservesSourceAndOnlyDefaultCallsNativeImmunityCheck() throws Exception {
        var target = EventTestObjects.allocate(ServerPlayer.class);
        var effect = new MobEffectInstance(MobEffects.POISON, 200);
        var checks = new AtomicInteger();
        Consumer<MobEffectEvent.Applicable> deny = event -> {
            assertSame(target, event.getEntity());
            assertSame(target, event.getEffectSource());
            assertSame(effect, event.getEffectInstance());
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        };
        FabricEvents.GAME.addListener(MobEffectEvent.Applicable.class, deny);
        try { assertFalse(MobEffectHooks.applicable(target, effect, target, () -> { checks.incrementAndGet(); return true; })); }
        finally { FabricEvents.GAME.unregister(deny); }
        assertEquals(0, checks.get());
        Consumer<MobEffectEvent.Applicable> force = event -> {
            assertNull(event.getEffectSource());
            event.setResult(MobEffectEvent.Applicable.Result.APPLY);
        };
        FabricEvents.GAME.addListener(MobEffectEvent.Applicable.class, force);
        try { assertTrue(MobEffectHooks.applicable(target, effect, null, () -> { checks.incrementAndGet(); return false; })); }
        finally { FabricEvents.GAME.unregister(force); }
        assertEquals(0, checks.get());
        assertFalse(MobEffectHooks.applicable(target, effect, null, () -> { checks.incrementAndGet(); return false; }));
        assertEquals(1, checks.get());
    }

    @Test void reapplyingAdjustedEffectHasAnIndependentNestedDecisionAndKeepsOuterDenial() throws Exception {
        var target = EventTestObjects.allocate(ServerPlayer.class);
        var original = new MobEffectInstance(MobEffects.POISON, 200);
        var adjusted = new MobEffectInstance(MobEffects.POISON, 100);
        var calls = new AtomicInteger();
        Consumer<MobEffectEvent.Applicable> listener = event -> {
            calls.incrementAndGet();
            if (event.getEffectInstance() == original) {
                event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
                assertTrue(MobEffectHooks.applicable(target, adjusted, event.getEffectSource(), () -> true));
            }
        };
        FabricEvents.GAME.addListener(MobEffectEvent.Applicable.class, listener);
        try { assertFalse(MobEffectHooks.applicable(target, original, null, () -> fail("outer native check"))); }
        finally { FabricEvents.GAME.unregister(listener); }
        assertEquals(2, calls.get());
    }

    @Test void breakSpeedKeepsOriginalContextAndCanceledSpeedMinusOne() throws Exception {
        var player = EventTestObjects.allocate(ServerPlayer.class);
        var state = Blocks.STONE.defaultBlockState();
        Consumer<PlayerEvent.BreakSpeed> listener = event -> {
            assertSame(state, event.getState());
            assertEquals(2.5F, event.getOriginalSpeed());
            event.setNewSpeed(event.getOriginalSpeed() * 4);
        };
        FabricEvents.GAME.addListener(PlayerEvent.BreakSpeed.class, listener);
        try { assertEquals(10, MiningSpeedHooks.result(player, state, 2.5F)); }
        finally { FabricEvents.GAME.unregister(listener); }
        Consumer<PlayerEvent.BreakSpeed> cancel = event -> { event.setNewSpeed(99); event.setCanceled(true); };
        FabricEvents.GAME.addListener(PlayerEvent.BreakSpeed.class, cancel);
        try { assertEquals(-1, MiningSpeedHooks.result(player, state, 2.5F)); }
        finally { FabricEvents.GAME.unregister(cancel); }
    }

    @Test void nestedPositionScopesCopyMutableCoordinatesAndRestoreAfterFailure() throws Exception {
        var player = EventTestObjects.allocate(ServerPlayer.class);
        var state = Blocks.STONE.defaultBlockState();
        var expected = new java.util.ArrayList<BlockPos>();
        Consumer<PlayerEvent.BreakSpeed> listener = event -> assertEquals(expected.removeFirst(), event.getPosition().orElse(null));
        FabricEvents.GAME.addListener(PlayerEvent.BreakSpeed.class, listener);
        try {
            var mutable = new BlockPos.MutableBlockPos(1, 2, 3);
            MiningSpeedHooks.at(mutable, () -> {
                mutable.set(9, 9, 9);
                expected.add(new BlockPos(1, 2, 3)); MiningSpeedHooks.result(player, state, 1);
                assertThrows(IllegalArgumentException.class, () -> MiningSpeedHooks.at(BlockPos.ZERO, () -> {
                    expected.add(BlockPos.ZERO); MiningSpeedHooks.result(player, state, 1);
                    throw new IllegalArgumentException("fixture");
                }));
                expected.add(new BlockPos(1, 2, 3)); MiningSpeedHooks.result(player, state, 1);
                return null;
            });
            expected.add(null); MiningSpeedHooks.result(player, state, 1);
            assertTrue(expected.isEmpty());
        } finally { FabricEvents.GAME.unregister(listener); }
    }

    @Test void nativeConsumersHaveOneImmunityCheckPerSourceAwareEntryAndOneMiningPositionCall() throws Exception {
        var living = nativeType("net/minecraft/world/entity/LivingEntity");
        for (String methodName : java.util.List.of("addEffect", "forceAddEffect")) {
            var method = living.methods.stream().filter(m -> m.name.equals(methodName)
                    && m.desc.startsWith("(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)"))
                    .findFirst().orElseThrow();
            assertEquals(1, java.util.Arrays.stream(method.instructions.toArray()).filter(i -> i instanceof MethodInsnNode call
                    && call.name.equals("canBeAffected")).count());
        }
        var block = nativeType("net/minecraft/world/level/block/state/BlockBehaviour");
        var progress = block.methods.stream().filter(m -> m.name.equals("getDestroyProgress")).findFirst().orElseThrow();
        assertEquals(1, java.util.Arrays.stream(progress.instructions.toArray()).filter(i -> i instanceof MethodInsnNode call
                && call.owner.equals("net/minecraft/world/entity/player/Player") && call.name.equals("getDestroySpeed")).count());
        var speed = nativeType("net/minecraft/world/entity/player/Player").methods.stream()
                .filter(m -> m.name.equals("getDestroySpeed")).findFirst().orElseThrow();
        assertEquals(1, java.util.Arrays.stream(speed.instructions.toArray()).filter(i -> i.getOpcode() == Opcodes.FRETURN).count());
    }

    private static ClassNode nativeType(String name) throws Exception {
        var result = new ClassNode();
        try (var input = EffectAndMiningHooksTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(input); new ClassReader(input).accept(result, 0);
        }
        return result;
    }
}
