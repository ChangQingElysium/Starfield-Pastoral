package com.stardew.craft.platform.fabric.light;

import static org.junit.jupiter.api.Assertions.*;

import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.BlockLightEngine;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.injection.Inject;

class LightEmissionStructureTest {
    @Test void bothNativePropagationEntryPointsUseTheContextualEmissionHook() throws Exception {
        String getter = "net/minecraft/world/level/block/state/BlockState.getLightEmission()I";
        var hooks = Class.forName("com.stardew.craft.platform.mixin.light.mixin.BlockLightEngineEmissionMixin").getDeclaredMethods();
        int count = 0;
        for (var method : hooks) {
            var hook = method.getAnnotation(WrapOperation.class); if (hook == null) continue;
            count++; assertEquals(1, hook.require());
            String name = hook.method()[0].split("\\(")[0];
            assertEquals(1, calls(BlockLightEngine.class, name).stream().filter(getter::equals).count());
        }
        assertEquals(2, count);
        assertTrue(calls(BlockLightEngine.class, "propagateLightSources").contains(
                "net/minecraft/world/level/chunk/LightChunk.findBlockLightSources(Ljava/util/function/BiConsumer;)V"));
        assertTrue(calls(BlockLightEngine.class, "checkNode").contains(
                "net/minecraft/world/level/lighting/BlockLightEngine.getEmission(JLnet/minecraft/world/level/block/state/BlockState;)I"));
    }

    @Test void blockLightMixinInheritsTheNativeSuperclassInsteadOfShadowingItsInheritedField() throws Exception {
        var mixin = Class.forName("com.stardew.craft.platform.mixin.light.mixin.BlockLightEngineEmissionMixin");
        assertSame(net.minecraft.world.level.lighting.LightEngine.class, mixin.getSuperclass());
        assertThrows(NoSuchFieldException.class, () -> mixin.getDeclaredField("chunkSource"));
        assertThrows(NoSuchFieldException.class, () -> BlockLightEngine.class.getDeclaredField("chunkSource"));
        var inherited = mixin.getSuperclass().getDeclaredField("chunkSource");
        assertSame(net.minecraft.world.level.chunk.LightChunkGetter.class, inherited.getType());
        assertTrue(java.lang.reflect.Modifier.isProtected(inherited.getModifiers()));
    }

    @Test void nativeChunkScanPreservesThePaletteScanAndHasAnExplicitDynamicSourceHook() throws Exception {
        var type = Class.forName("com.stardew.craft.platform.mixin.light.mixin.ChunkLightSourcesMixin");
        var hook = java.util.Arrays.stream(type.getDeclaredMethods()).map(m -> m.getAnnotation(WrapOperation.class))
                .filter(a -> a != null).findFirst().orElseThrow();
        assertArrayEquals(new String[]{"findBlockLightSources"}, hook.method()); assertEquals(1, hook.require());
        assertEquals(1, calls(ChunkAccess.class, "findBlockLightSources").stream().filter(
                "net/minecraft/world/level/chunk/ChunkAccess.findBlocks(Ljava/util/function/Predicate;Ljava/util/function/BiConsumer;)V"::equals).count());
    }

    @Test void replacementAndExplicitRemovalClearSourcesWithoutClearingSavedCacheOnChunkUnload() throws Exception {
        var type = Class.forName("com.stardew.craft.platform.mixin.light.mixin.LevelChunkAuxiliaryLightsMixin");
        var methods = java.util.Arrays.stream(type.getDeclaredMethods()).map(m -> m.getAnnotation(Inject.class)).filter(a -> a != null).toList();
        assertEquals(2, methods.size());
        for (var hook : methods) {
            assertEquals(1, hook.require());
            if (hook.method()[0].equals("removeBlockEntity")) assertEquals("HEAD", hook.at()[0].value());
            else {
                assertArrayEquals(new String[]{"setBlockEntity"}, hook.method());
                assertEquals("Lnet/minecraft/world/level/block/entity/BlockEntity;setRemoved()V", hook.at()[0].target());
                assertEquals(1, calls(LevelChunk.class, "setBlockEntity").stream().filter(
                        "net/minecraft/world/level/block/entity/BlockEntity.setRemoved()V"::equals).count());
            }
        }
        assertFalse(calls(LevelChunk.class, "clearAllBlockEntities").contains(
                "net/minecraft/world/level/chunk/LevelChunk.removeBlockEntity(Lnet/minecraft/core/BlockPos;)V"));
    }

    @Test void initialSyncWrapsTheRealSingleChunkSendAndLeavesDebugAndBatchBehaviorNative() throws Exception {
        var type = Class.forName("com.stardew.craft.platform.mixin.light.mixin.AuxiliaryLightChunkSendMixin");
        var hook = java.util.Arrays.stream(type.getDeclaredMethods()).map(m -> m.getAnnotation(WrapOperation.class))
                .filter(a -> a != null).findFirst().orElseThrow();
        assertArrayEquals(new String[]{"sendChunk"}, hook.method()); assertEquals(1, hook.require());
        var calls = calls(PlayerChunkSender.class, "sendChunk");
        String send = "net/minecraft/server/network/ServerGamePacketListenerImpl.send(Lnet/minecraft/network/protocol/Packet;)V";
        String debug = "net/minecraft/network/protocol/game/DebugPackets.sendPoiPacketsForChunk(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/ChunkPos;)V";
        assertEquals(1, calls.stream().filter(send::equals).count()); assertTrue(calls.indexOf(send) < calls.indexOf(debug));
    }

    @Test void commonLightingContractsDoNotLinkClientClassesAndCacheReadersHaveNoBlockEntityLookup() throws Exception {
        for (var type : List.of(AuxiliaryLightManager.class, AuxiliaryLightView.class, AuxiliaryLightSnapshot.class,
                AuxiliaryLights.class, ChunkAuxiliaryLightManager.class, LightEmissionHooks.class, LightEmissionProvider.class, LightSyncPayload.class)) {
            try (var stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
                assertNotNull(stream); var bytes = new String(stream.readAllBytes(), StandardCharsets.ISO_8859_1);
                assertFalse(bytes.contains("net/minecraft/client/")); assertFalse(bytes.contains("getBlockEntity"));
                assertFalse(bytes.contains("net/fabricmc/api/Environment"));
            }
        }
    }

    private static List<String> calls(Class<?> type, String methodName) throws Exception {
        var calls = new ArrayList<String>();
        try (var stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            assertNotNull(stream);
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                    if (!name.equals(methodName)) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean itf) { calls.add(owner + "." + name + descriptor); }
                    };
                }
            }, 0);
        }
        return calls;
    }
}
