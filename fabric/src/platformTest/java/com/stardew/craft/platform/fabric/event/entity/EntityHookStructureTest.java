package com.stardew.craft.platform.fabric.event.entity;

import static org.junit.jupiter.api.Assertions.*;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.platform.fabric.event.entity.living.LivingDeathEvent;
import com.stardew.craft.platform.fabric.event.entity.living.LivingEvent;
import com.stardew.craft.platform.mixin.event.entity.mixin.LivingEntityDeathMixin;
import com.stardew.craft.platform.mixin.event.entity.mixin.PersistentEntityJoinMixin;
import com.stardew.craft.platform.mixin.event.entity.mixin.PlayerDeathMixin;
import com.stardew.craft.platform.mixin.event.entity.mixin.ServerEntityLeaveMixin;
import com.stardew.craft.platform.mixin.event.entity.mixin.ServerEntityTickMixin;
import com.stardew.craft.platform.mixin.event.entity.mixin.ServerPlayerDeathMixin;
import com.stardew.craft.platform.mixin.event.entity.mixin.client.ClientEntityLeaveMixin;
import com.stardew.craft.platform.mixin.event.entity.mixin.client.ClientEntityLifecycleMixin;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

/** Native target assertions; this suite does not apply mixins or claim runtime game acceptance. */
class EntityHookStructureTest {
    @Test void tickHooksWrapOnlyTheEntityActionAfterNativeBookkeepingAndBeforePassengers() throws IOException {
        for (String owner : List.of("net/minecraft/server/level/ServerLevel", "net/minecraft/client/multiplayer/ClientLevel")) {
            var method = read(owner, "tickNonPassenger", "(Lnet/minecraft/world/entity/Entity;)V");
            assertEquals(1, method.operations.stream().filter("call:net/minecraft/world/entity/Entity.tick"::equals).count());
            int tick = method.operations.indexOf("call:net/minecraft/world/entity/Entity.tick");
            assertTrue(method.operations.indexOf("call:net/minecraft/world/entity/Entity.setOldPosAndRot") < tick);
            assertTrue(method.operations.indexOf("put:net/minecraft/world/entity/Entity.tickCount") < tick);
            assertTrue(tick < method.operations.indexOf("call:net/minecraft/world/entity/Entity.getPassengers"));
        }
        for (Class<?> mixin : List.of(ServerEntityTickMixin.class, ClientEntityLifecycleMixin.class)) {
            WrapOperation hook = Arrays.stream(mixin.getDeclaredMethods()).map(method -> method.getAnnotation(WrapOperation.class))
                    .filter(annotation -> annotation != null).findFirst().orElseThrow();
            assertArrayEquals(new String[]{"tickNonPassenger"}, hook.method());
            assertEquals("Lnet/minecraft/world/entity/Entity;tick()V", hook.at()[0].target());
            assertEquals(1, hook.require());
        }
    }

    @Test void passengerTicksRemainOutsideTheUpstreamEntityTickEvent() throws IOException {
        for (String owner : List.of("net/minecraft/server/level/ServerLevel", "net/minecraft/client/multiplayer/ClientLevel")) {
            var method = read(owner, "tickPassenger", "(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;)V");
            assertEquals(1, method.operations.stream().filter("call:net/minecraft/world/entity/Entity.rideTick"::equals).count());
            assertFalse(method.operations.contains("call:net/minecraft/world/entity/Entity.tick"));
        }
    }

    @Test void persistentJoinWrapsTheMethodBeforeUuidAndSectionStorageMutations() throws IOException {
        String descriptor = "(Lnet/minecraft/world/level/entity/EntityAccess;Z)Z";
        var method = read("net/minecraft/world/level/entity/PersistentEntitySectionManager", "addEntity", descriptor);
        assertEquals("call:net/minecraft/world/level/entity/PersistentEntitySectionManager.addEntityUuid", method.operations.getFirst());
        assertTrue(method.operations.contains("call:net/minecraft/world/level/entity/EntitySection.add"));
        var hook = Arrays.stream(PersistentEntityJoinMixin.class.getDeclaredMethods()).map(nativeMethod -> nativeMethod.getAnnotation(WrapMethod.class))
                .filter(annotation -> annotation != null).findFirst().orElseThrow();
        assertArrayEquals(new String[]{"addEntity" + descriptor}, hook.method());
    }

    @Test void clientJoinWrapsOldIdRemovalAndNewInsertionTogether() throws IOException {
        var method = read("net/minecraft/client/multiplayer/ClientLevel", "addEntity", "(Lnet/minecraft/world/entity/Entity;)V");
        int removal = method.operations.indexOf("call:net/minecraft/client/multiplayer/ClientLevel.removeEntity");
        int insertion = method.operations.indexOf("call:net/minecraft/world/level/entity/TransientEntitySectionManager.addEntity");
        assertTrue(removal >= 0 && insertion > removal);
        var hook = Arrays.stream(ClientEntityLifecycleMixin.class.getDeclaredMethods()).map(nativeMethod -> nativeMethod.getAnnotation(WrapMethod.class))
                .filter(annotation -> annotation != null).findFirst().orElseThrow();
        assertArrayEquals(new String[]{"addEntity"}, hook.method());
    }

    @Test void leaveTargetsConcreteTrackingCallbacksAfterCleanupWithTheCorrectWorld() throws IOException {
        String descriptor = "(Lnet/minecraft/world/entity/Entity;)V";
        for (var spec : List.of(
                new LeaveSpec(ServerEntityLeaveMixin.class, "net/minecraft/server/level/ServerLevel$EntityCallbacks", "field_26936", "Lnet/minecraft/server/level/ServerLevel;"),
                new LeaveSpec(ClientEntityLeaveMixin.class, "net/minecraft/client/multiplayer/ClientLevel$EntityCallbacks", "field_27735", "Lnet/minecraft/client/multiplayer/ClientLevel;"))) {
            var nativeMethod = read(spec.owner, "onTrackingEnd", descriptor);
            assertTrue(nativeMethod.found);
            assertTrue(nativeMethod.fields.contains(spec.field + ":" + spec.type));
            Inject hook = injection(spec.mixin);
            assertArrayEquals(new String[]{"onTrackingEnd" + descriptor}, hook.method());
            assertEquals("TAIL", hook.at()[0].value());
            assertEquals(1, hook.require());
        }
    }

    @Test void deathHooksPrecedeTheDeadFlagDropsAndMessagesIncludingPlayerOverrides() throws IOException {
        assertEquals("HEAD", injection(LivingEntityDeathMixin.class).at()[0].value());
        assertEquals("HEAD", injection(PlayerDeathMixin.class).at()[0].value());
        var server = injection(ServerPlayerDeathMixin.class);
        assertTrue(server.cancellable());
        assertEquals("Lnet/minecraft/server/level/ServerPlayer;gameEvent(Lnet/minecraft/core/Holder;)V", server.at()[0].target());
        assertEquals(At.Shift.AFTER, server.at()[0].shift());
        var serverNative = read("net/minecraft/server/level/ServerPlayer", "die", "(Lnet/minecraft/world/damagesource/DamageSource;)V");
        assertEquals("call:net/minecraft/server/level/ServerPlayer.gameEvent", serverNative.operations.getFirst());
        assertFalse(serverNative.operations.contains("call:net/minecraft/world/entity/player/Player.die"));
        assertFalse(serverNative.operations.contains("call:net/minecraft/world/entity/LivingEntity.die"));
        var player = read("net/minecraft/world/entity/player/Player", "die", "(Lnet/minecraft/world/damagesource/DamageSource;)V");
        assertEquals("call:net/minecraft/world/entity/LivingEntity.die", player.operations.getFirst());
        assertTrue(player.operations.contains("call:net/minecraft/world/entity/player/Player.dropAllDeathLoot"));
        var commonHook = read(LivingEntityDeathMixin.class.getName().replace('.', '/'), "stardewcraft$death",
                "(Lnet/minecraft/world/damagesource/DamageSource;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V");
        assertTrue(commonHook.operations.indexOf("instanceof:net/minecraft/world/entity/player/Player")
                < commonHook.operations.indexOf("call:com/stardew/craft/platform/fabric/event/entity/EntityDispatch.allowDeath"),
                "the LivingEntity hook must skip the Player super-chain to avoid a second death publication");
    }

    @Test void commonEntityEventsAndMixinsDoNotReferenceClientClasses() throws IOException {
        for (Class<?> type : List.of(EntityEvent.class, EntityTickEvent.class, EntityJoinLevelEvent.class,
                EntityLeaveLevelEvent.class, EntityDispatch.class, LivingEvent.class, LivingDeathEvent.class,
                ServerEntityTickMixin.class, PersistentEntityJoinMixin.class, ServerEntityLeaveMixin.class,
                LivingEntityDeathMixin.class, PlayerDeathMixin.class, ServerPlayerDeathMixin.class)) {
            try (var stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
                assertNotNull(stream);
                assertFalse(new String(stream.readAllBytes(), StandardCharsets.ISO_8859_1).contains("net/minecraft/client/"), type.getName());
            }
        }
    }

    private static Inject injection(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods()).map(method -> method.getAnnotation(Inject.class))
                .filter(annotation -> annotation != null).findFirst().orElseThrow();
    }

    private static NativeMethod read(String owner, String name, String descriptor) throws IOException {
        var method = new NativeMethod();
        try (var stream = EntityHookStructureTest.class.getResourceAsStream("/" + owner + ".class")) {
            assertNotNull(stream);
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public FieldVisitor visitField(int access, String fieldName, String fieldDescriptor,
                        String signature, Object value) {
                    method.fields.add(fieldName + ":" + fieldDescriptor);
                    return null;
                }
                @Override public MethodVisitor visitMethod(int access, String nativeName, String nativeDescriptor,
                        String signature, String[] exceptions) {
                    if (!nativeName.equals(name) || !nativeDescriptor.equals(descriptor)) return null;
                    method.found = true;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int opcode, String calledOwner, String calledName,
                                String calledDescriptor, boolean isInterface) {
                            method.operations.add("call:" + calledOwner + "." + calledName);
                        }
                        @Override public void visitFieldInsn(int opcode, String fieldOwner, String fieldName, String fieldDescriptor) {
                            if (opcode == Opcodes.PUTFIELD) method.operations.add("put:" + fieldOwner + "." + fieldName);
                        }
                        @Override public void visitTypeInsn(int opcode, String type) {
                            if (opcode == Opcodes.INSTANCEOF) method.operations.add("instanceof:" + type);
                        }
                    };
                }
            }, 0);
        }
        assertTrue(method.found, owner + "." + name + descriptor);
        return method;
    }

    private record LeaveSpec(Class<?> mixin, String owner, String field, String type) {}
    private static final class NativeMethod {
        boolean found;
        final List<String> operations = new ArrayList<>();
        final List<String> fields = new ArrayList<>();
    }
}
