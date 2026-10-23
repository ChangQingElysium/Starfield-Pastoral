package com.stardew.craft.platform.fabric.client.event.entityrender;

import static org.junit.jupiter.api.Assertions.*;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.stardew.craft.platform.mixin.client.event.entityrender.mixin.RidingPoseMixin;
import com.stardew.craft.platform.fabric.event.sleep.riding.RiderPoseProvider;
import com.stardew.craft.platform.fabric.event.sleep.riding.RiderPoses;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.SharedConstants;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

class RidingPoseTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test
    void providerTrueFalseAndNativeDefaultRemainRealEntityQueries() throws Exception {
        var seat = allocate(Seat.class); seat.sitting = true;
        assertTrue(RiderPoses.shouldSit(true, seat)); seat.sitting = false; assertFalse(RiderPoses.shouldSit(true, seat));
        assertTrue(RiderPoses.shouldSit(true, allocate(ArmorStand.class)));
        assertFalse(RiderPoses.shouldSit(false, seat)); assertFalse(RiderPoses.shouldSit(true, null));
    }
    @Test
    void allThreeNativeQueriesReuseTheFirstComputedPoseAndOnlyCallTheProviderOnce() throws Exception {
        var seat = allocate(Seat.class); var rider = allocate(Rider.class); rider.vehicle = seat;
        var calls = new AtomicInteger(); Operation<Boolean> original = ignored -> { calls.incrementAndGet(); return true; };
        var mixin = new RidingPoseMixin() {};
        var cache = new LocalRef<Boolean>() { Boolean value; public Boolean get() { return value; } public void set(Boolean value) { this.value = value; } };
        var method = RidingPoseMixin.class.getDeclaredMethod("stardewcraft$shouldSit", LivingEntity.class, Operation.class, LocalRef.class); method.setAccessible(true);
        assertEquals(false, method.invoke(mixin, rider, original, cache)); seat.sitting = true;
        assertEquals(false, method.invoke(mixin, rider, original, cache)); assertEquals(false, method.invoke(mixin, rider, original, cache));
        assertEquals(1, calls.get()); assertEquals(1, seat.queries);
        try (var bytes = getClass().getClassLoader().getResourceAsStream("net/minecraft/client/renderer/entity/LivingEntityRenderer.class")) {
            var node = new ClassNode(); new ClassReader(bytes).accept(node, ClassReader.SKIP_FRAMES);
            var render = node.methods.stream().filter(value -> value.name.equals("render") && value.desc.startsWith("(Lnet/minecraft/world/entity/LivingEntity;")).findFirst().orElseThrow();
            assertEquals(3, java.util.stream.StreamSupport.stream(render.instructions.spliterator(), false)
                    .filter(value -> value instanceof MethodInsnNode call && call.owner.equals("net/minecraft/world/entity/LivingEntity") && call.name.equals("isPassenger")).count());
        }
    }
    private static <T> T allocate(Class<T> type) throws ReflectiveOperationException {
        var unsafe = Class.forName("sun.misc.Unsafe"); var field = unsafe.getDeclaredField("theUnsafe"); field.setAccessible(true);
        return type.cast(unsafe.getMethod("allocateInstance", Class.class).invoke(field.get(null), type));
    }
    private static final class Seat extends ArmorStand implements RiderPoseProvider {
        boolean sitting; int queries;
        Seat() { super(EntityType.ARMOR_STAND, null); }
        @Override public boolean shouldRiderSit() { queries++; return sitting; }
    }
    private static final class Rider extends ArmorStand {
        Entity vehicle;
        Rider() { super(EntityType.ARMOR_STAND, null); }
        @Override public Entity getVehicle() { return vehicle; }
    }
}
