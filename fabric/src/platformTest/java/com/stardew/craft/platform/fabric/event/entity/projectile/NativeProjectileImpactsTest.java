package com.stardew.craft.platform.fabric.event.entity.projectile;

import static org.junit.jupiter.api.Assertions.*;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.stardew.craft.platform.fabric.event.FabricEvents;
import com.stardew.craft.platform.fabric.event.entity.ProjectileImpactEvent;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Calls real producer decisions and the actual arrow Mixin handlers, without constructing a world. */
class NativeProjectileImpactsTest {
    private final List<Consumer<ProjectileImpactEvent>> listeners = new ArrayList<>();
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @AfterEach void cleanup() { listeners.forEach(FabricEvents.GAME::unregister); }
    private void listen(Consumer<ProjectileImpactEvent> listener) {
        listeners.add(listener); FabricEvents.GAME.addListener(ProjectileImpactEvent.class, listener);
    }
    private static BlockHitResult block() { return new BlockHitResult(Vec3.ZERO, Direction.UP, BlockPos.ZERO, false); }
    private static BlockHitResult miss() { return BlockHitResult.miss(Vec3.ZERO, Direction.UP, BlockPos.ZERO); }

    @Test void realProjectileAndHitResultReachTheCancelableEventBeforeCollisionOrDeflection() throws Exception {
        var arrow = arrow(); var result = block(); int[] collisions = {0};
        listen(event -> {
            assertSame(arrow, event.getProjectile()); assertSame(arrow, event.getEntity());
            assertSame(result, event.getRayTraceResult()); assertTrue(event.isCancelable());
            assertEquals(0, collisions[0]); event.setCanceled(true);
        });
        assertSame(ProjectileDeflection.NONE, NativeProjectileImpacts.hitTarget(arrow, result, args -> {
            collisions[0]++; return ProjectileDeflection.REVERSE;
        }));
        assertEquals(0, collisions[0]);
    }

    @Test void acceptedEntityCollisionPreservesOriginalArgumentsAndActualDeflection() throws Exception {
        var arrow = arrow(); var result = new EntityHitResult(arrow(), Vec3.ZERO); int[] events = {0}, collisions = {0};
        listen(event -> { events[0]++; assertSame(result, event.getRayTraceResult()); });
        assertSame(ProjectileDeflection.REVERSE, NativeProjectileImpacts.hitTarget(arrow, result, args -> {
            assertSame(arrow, args[0]); assertSame(result, args[1]); collisions[0]++; return ProjectileDeflection.REVERSE;
        }));
        assertEquals(1, events[0]); assertEquals(1, collisions[0]);
    }

    @Test void missesHaveNoEventAndTheTwoNativeGatesRetainTheirDifferentDelegationRules() throws Exception {
        var arrow = arrow(); int[] events = {0}; listen(event -> events[0]++);
        assertSame(ProjectileDeflection.NONE, NativeProjectileImpacts.hitTarget(arrow, miss(), args -> {
            throw new AssertionError("Thrown projectile misses must not call onHit or deflection");
        }));
        assertTrue(NativeProjectileImpacts.allowDirectHit(arrow, miss()), "Fishing/firework onHit retains native miss delegation");
        assertEquals(0, events[0]);
    }

    @Test void directFishingAndFireworkGatesStillCancelBothBlockAndEntityImpacts() throws Exception {
        var arrow = arrow(); int[] events = {0}; listen(event -> { events[0]++; event.setCanceled(true); });
        assertFalse(NativeProjectileImpacts.allowDirectHit(arrow, block()));
        assertFalse(NativeProjectileImpacts.allowDirectHit(arrow, new EntityHitResult(arrow(), Vec3.ZERO)));
        assertEquals(2, events[0]);
    }

    @Test void cancelledPiercingArrowExitsTheLoopWithoutImpulseAndStillReachesMovement() throws Exception {
        var arrow = arrow(); var result = new EntityHitResult(arrow(), Vec3.ZERO);
        var entityHit = new Ref<EntityHitResult>(result); var skip = new Bool(); int[] collisions = {0}, writes = {0}, events = {0};
        listen(event -> { events[0]++; event.setCanceled(true); });
        int iterations = 0;
        do {
            assertTrue(++iterations < 3, "A cancelled piercing arrow must never hit the same target indefinitely");
            assertSame(ProjectileDeflection.NONE, impact(arrow, result, args -> { collisions[0]++; return ProjectileDeflection.NONE; }, entityHit, skip));
            impulse(arrow, args -> { writes[0]++; return null; }, skip);
        } while (entityHit.get() != null); // Exact native tail test, regardless of a positive pierce level.
        assertEquals(1, iterations); assertEquals(1, events[0]);
        assertEquals(0, collisions[0]); assertEquals(0, writes[0]);
    }

    @Test void acceptedArrowResetsPriorDecisionAndAMissSkipsTheImpulseWrite() throws Exception {
        var arrow = arrow(); var result = new EntityHitResult(arrow(), Vec3.ZERO);
        var entityHit = new Ref<EntityHitResult>(result); var skip = new Bool(); skip.set(true);
        int[] writes = {0};
        assertSame(ProjectileDeflection.REVERSE, impact(arrow, result, args -> ProjectileDeflection.REVERSE, entityHit, skip));
        assertSame(result, entityHit.get()); assertFalse(skip.get());
        impulse(arrow, args -> { assertSame(arrow, args[0]); assertEquals(true, args[1]); writes[0]++; return null; }, skip);
        assertEquals(1, writes[0]);
        assertSame(ProjectileDeflection.NONE, impact(arrow, miss(), args -> { throw new AssertionError("MISS hit"); }, entityHit, skip));
        assertNull(entityHit.get()); assertTrue(skip.get());
        impulse(arrow, args -> { writes[0]++; return null; }, skip); assertEquals(1, writes[0]);
    }

    private static ProjectileDeflection impact(AbstractArrow arrow, HitResult result, Operation<ProjectileDeflection> original,
            LocalRef<EntityHitResult> entityHit, LocalBooleanRef skip) throws Exception {
        return (ProjectileDeflection) handler("stardewcraft$impact", AbstractArrow.class, HitResult.class, Operation.class, LocalRef.class, LocalBooleanRef.class)
                .invoke(null, arrow, result, original, entityHit, skip);
    }
    private static void impulse(AbstractArrow arrow, Operation<Void> original, LocalBooleanRef skip) throws Exception {
        handler("stardewcraft$impactImpulse", AbstractArrow.class, boolean.class, Operation.class, LocalBooleanRef.class)
                .invoke(null, arrow, true, original, skip);
    }
    private static Method handler(String name, Class<?>... parameters) throws Exception {
        var method = Class.forName("com.stardew.craft.platform.mixin.event.entity.projectile.ArrowImpactMixin").getDeclaredMethod(name, parameters);
        assertTrue(method.trySetAccessible()); return method;
    }
    private static Arrow arrow() throws Exception {
        // Only event identity is used; real projectile construction/ticking requires the headless runtime gate.
        var type = Class.forName("sun.misc.Unsafe"); var field = type.getDeclaredField("theUnsafe"); assertTrue(field.trySetAccessible());
        return (Arrow) type.getMethod("allocateInstance", Class.class).invoke(field.get(null), Arrow.class);
    }
    private static final class Ref<T> implements LocalRef<T> {
        private T value; Ref(T value) { this.value = value; }
        public T get() { return value; } public void set(T value) { this.value = value; }
    }
    private static final class Bool implements LocalBooleanRef {
        private boolean value;
        public boolean get() { return value; } public void set(boolean value) { this.value = value; }
    }
}
