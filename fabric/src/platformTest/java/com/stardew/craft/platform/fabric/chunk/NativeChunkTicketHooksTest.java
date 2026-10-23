package com.stardew.craft.platform.fabric.chunk;

import static org.junit.jupiter.api.Assertions.*;

import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ForcedChunksSavedData;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.injection.Inject;

class NativeChunkTicketHooksTest {
    @Test void chunkTickingHooksTargetBothRealShortCircuitGatesAndLeaveTheNativeTickBodyInPlace() throws Exception {
        var nativeMethod = method(ServerChunkCache.class, "tickChunks"); var calls = calls(nativeMethod); int hooks = 0;
        var mixin = Class.forName("com.stardew.craft.platform.mixin.chunk.mixin.ServerChunkTicketTickMixin");
        for (var declared : mixin.getDeclaredMethods()) {
            var hook = declared.getAnnotation(WrapOperation.class); if (hook == null) continue;
            hooks++; assertArrayEquals(new String[]{"tickChunks"}, hook.method()); assertEquals(1, hook.require());
            var target = hook.at()[0].target(); String call = target.substring(1, target.indexOf(';')) + "." + target.substring(target.indexOf(';') + 1);
            assertEquals(1, calls.stream().filter(call::equals).count());
        }
        assertEquals(2, hooks);
        assertTrue(calls.contains("net/minecraft/world/level/chunk/LevelChunk.incrementInhabitedTime(J)V"));
        assertTrue(calls.contains("net/minecraft/server/level/ServerLevel.shouldTickBlocksAt(J)Z"));
        assertTrue(calls.contains("net/minecraft/server/level/ServerLevel.tickChunk(Lnet/minecraft/world/level/chunk/LevelChunk;I)V"));
        for (boolean a : new boolean[]{false, true}) for (boolean b : new boolean[]{false, true}) for (boolean forced : new boolean[]{false, true})
            assertEquals(a && b || forced, (a || forced) && (b || forced));
    }

    @Test void worldSleepHookTargetsOnlyTheNativeForcedSetEmptinessCheckBeforeEntityAndBlockEntityTicks() throws Exception {
        var calls = calls(method(ServerLevel.class, "tick"));
        String empty = "it/unimi/dsi/fastutil/longs/LongSet.isEmpty()Z";
        assertEquals(1, calls.stream().filter(empty::equals).count());
        assertTrue(calls.indexOf(empty) < calls.indexOf("net/minecraft/server/level/ServerLevel.resetEmptyTime()V"));
        assertTrue(calls.indexOf(empty) < calls.indexOf("net/minecraft/server/level/ServerLevel.tickBlockEntities()V"));
        var mixin = Class.forName("com.stardew.craft.platform.mixin.chunk.mixin.ServerLevelTicketSleepMixin");
        var hook = java.util.Arrays.stream(mixin.getDeclaredMethods()).map(m -> m.getAnnotation(WrapOperation.class)).filter(java.util.Objects::nonNull).findFirst().orElseThrow();
        assertArrayEquals(new String[]{"tick"}, hook.method()); assertEquals(1, hook.require()); assertEquals("Lit/unimi/dsi/fastutil/longs/LongSet;isEmpty()Z", hook.at()[0].target());
    }

    @Test void nativeRegionTicketDistanceTwoFeedsBothLoadingAndTickingTrackersAndIsRemovedSymmetrically() throws Exception {
        var add = calls(method(DistanceManager.class, "addRegionTicket")); var remove = calls(method(DistanceManager.class, "removeRegionTicket"));
        assertTrue(add.contains("net/minecraft/server/level/DistanceManager.addTicket(JLnet/minecraft/server/level/Ticket;)V"));
        assertTrue(add.contains("net/minecraft/server/level/TickingTracker.addTicket(JLnet/minecraft/server/level/Ticket;)V"));
        assertTrue(remove.contains("net/minecraft/server/level/DistanceManager.removeTicket(JLnet/minecraft/server/level/Ticket;)V"));
        assertTrue(remove.contains("net/minecraft/server/level/TickingTracker.removeTicket(JLnet/minecraft/server/level/Ticket;)V"));
        var backend = Class.forName("com.stardew.craft.platform.fabric.chunk.TicketControllers$NativeBackend");
        var load = calls(method(backend, "load")); assertTrue(load.contains("net/minecraft/server/level/ServerLevel.getChunk(II)Lnet/minecraft/world/level/chunk/LevelChunk;"));
        for (String name : List.of("add", "remove")) {
            var method = method(backend, name);
            assertEquals(1, java.util.stream.StreamSupport.stream(method.instructions.spliterator(), false).filter(insn -> insn instanceof InsnNode && insn.getOpcode() == Opcodes.ICONST_2).count());
            assertTrue(calls(method).stream().anyMatch(call -> call.startsWith("net/minecraft/server/level/ServerChunkCache." + name + "RegionTicket(")));
        }
    }

    @Test void nativeSavedDataLoadAndSaveRetainVanillaForcedAndUseReturnHooksForOpaqueControllerData() throws Exception {
        var mixin = Class.forName("com.stardew.craft.platform.mixin.chunk.mixin.ForcedChunksDataMixin"); int hooks = 0;
        for (var declared : mixin.getDeclaredMethods()) {
            var hook = declared.getAnnotation(Inject.class); if (hook == null) continue;
            hooks++; assertEquals(1, hook.require()); assertEquals("RETURN", hook.at()[0].value()); assertFalse(hook.cancellable());
            var nativeMethod = method(ForcedChunksSavedData.class, hook.method()[0]);
            assertTrue(nativeMethod.desc.startsWith("(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)"));
        }
        assertEquals(2, hooks);
        assertTrue(calls(method(ForcedChunksSavedData.class, "save")).contains("net/minecraft/nbt/CompoundTag.putLongArray(Ljava/lang/String;[J)V"));
        assertTrue(calls(method(ForcedChunksSavedData.class, "load")).contains("net/minecraft/nbt/CompoundTag.getLongArray(Ljava/lang/String;)[J"));
    }

    @Test void theWholeCommonTicketModuleHasNoClientLinkForceloadMutationOrFilesystemBypass() throws Exception {
        for (var name : List.of("TicketController", "TicketControllers", "TicketControllers$NativeBackend", "TicketHelper", "TicketOwner", "TicketSet", "ChunkTicketData", "ForcedChunkDataAccess", "RegisterTicketControllersEvent", "LoadingValidationCallback", "mixin.ForcedChunksDataMixin", "mixin.ServerChunkTicketTickMixin", "mixin.ServerLevelTicketSleepMixin")) {
            var type = Class.forName((name.startsWith("mixin.") ? "com.stardew.craft.platform.mixin.chunk." : "com.stardew.craft.platform.fabric.chunk.") + name);
            try (var stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
                assertNotNull(stream); var bytes = new String(stream.readAllBytes(), StandardCharsets.ISO_8859_1);
                assertFalse(bytes.contains("net/minecraft/client/"), name); assertFalse(bytes.contains("net/fabricmc/api/Environment"), name);
                assertFalse(bytes.contains("setChunkForced"), name); assertFalse(bytes.contains("java/nio/file"), name); assertFalse(bytes.contains("java/io/File"), name);
            }
        }
    }

    private static MethodNode method(Class<?> type, String name) throws Exception {
        var node = new ClassNode(); try (var stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) { assertNotNull(stream); new ClassReader(stream).accept(node, 0); }
        var methods = node.methods.stream().filter(method -> method.name.equals(name)).toList(); assertEquals(1, methods.size(), type.getName() + "." + name); return methods.getFirst();
    }
    private static List<String> calls(MethodNode method) { var result = new ArrayList<String>(); for (var insn : method.instructions) if (insn instanceof MethodInsnNode call) result.add(call.owner + "." + call.name + call.desc); return result; }
}
