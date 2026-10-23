package com.stardew.craft.platform.fabric.client.blockentity;

import static org.junit.jupiter.api.Assertions.*;

import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.spongepowered.asm.mixin.injection.Inject;

class NativeBlockEntityVisibilityTest {
    @Test void bothNativeSectionAndGlobalDrawCallsHaveExactlyOneLiveFrustumForTheHook() throws Exception {
        var method = method(LevelRenderer.class, "renderLevel"); var calls = new ArrayList<MethodInsnNode>();
        for (var insn : method.instructions) if (insn instanceof MethodInsnNode call && call.owner.equals("net/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher") && call.name.equals("render")) calls.add(call);
        assertEquals(2, calls.size());
        var frames = new Analyzer<BasicValue>(new TypedInterpreter()).analyze("net/minecraft/client/renderer/LevelRenderer", method);
        for (var call : calls) {
            var frame = frames[method.instructions.indexOf(call)]; int frustums = 0;
            for (int slot = 0; slot < frame.getLocals(); slot++) {
                var value = frame.getLocal(slot); if (value != null && value.getType() != null && value.getType().getDescriptor().equals("Lnet/minecraft/client/renderer/culling/Frustum;")) frustums++;
            }
            assertEquals(1, frustums);
        }
        var mixin = Class.forName("com.stardew.craft.platform.mixin.client.blockentity.mixin.BlockEntityVisibilityMixin");
        var hook = java.util.Arrays.stream(mixin.getDeclaredMethods()).map(m -> m.getAnnotation(WrapOperation.class)).filter(java.util.Objects::nonNull).findFirst().orElseThrow();
        assertArrayEquals(new String[]{"renderLevel"}, hook.method()); assertEquals(2, hook.require());
        assertEquals("Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher;render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V", hook.at()[0].target());
    }

    @Test void sectionCollectionInterceptsTheRealNativeOffscreenClassification() throws Exception {
        var nativeMethod = method(SectionCompiler.class, "handleBlockEntity");
        assertEquals("(Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;Lnet/minecraft/world/level/block/entity/BlockEntity;)V", nativeMethod.desc);
        var calls = calls(nativeMethod);
        assertEquals(1, calls.stream().filter("net/minecraft/client/renderer/blockentity/BlockEntityRenderer.shouldRenderOffScreen(Lnet/minecraft/world/level/block/entity/BlockEntity;)Z"::equals).count());
        var mixin = Class.forName("com.stardew.craft.platform.mixin.client.blockentity.mixin.BlockEntitySectionCollectionMixin");
        var hook = java.util.Arrays.stream(mixin.getDeclaredMethods()).map(m -> m.getAnnotation(Inject.class)).filter(java.util.Objects::nonNull).findFirst().orElseThrow();
        assertArrayEquals(new String[]{"handleBlockEntity"}, hook.method()); assertEquals("HEAD", hook.at()[0].value());
        assertTrue(hook.cancellable()); assertEquals(1, hook.require());
    }

    @Test void nativeDispatcherKeepsTheTypeWorldAndShouldRenderChecksBeforeSamplingLight() throws Exception {
        var method = method(BlockEntityRenderDispatcher.class, "render"); var calls = calls(method);
        int hasLevel = calls.indexOf("net/minecraft/world/level/block/entity/BlockEntity.hasLevel()Z");
        int valid = calls.indexOf("net/minecraft/world/level/block/entity/BlockEntityType.isValid(Lnet/minecraft/world/level/block/state/BlockState;)Z");
        int shouldRender = calls.indexOf("net/minecraft/client/renderer/blockentity/BlockEntityRenderer.shouldRender(Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/phys/Vec3;)Z");
        int tryRender = calls.indexOf("net/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher.tryRender(Lnet/minecraft/world/level/block/entity/BlockEntity;Ljava/lang/Runnable;)V");
        assertTrue(hasLevel >= 0 && hasLevel < valid && valid < shouldRender && shouldRender < tryRender);
        assertFalse(calls.stream().anyMatch(call -> call.contains("getLightColor")));
        assertTrue(calls(method(BlockEntityRenderDispatcher.class, "setupAndRender")).contains(
                "net/minecraft/client/renderer/LevelRenderer.getLightColor(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;)I"));
    }

    @Test void theExtensionOnlyAddsBoundsAndKeepsAllNativeDistanceAndOffscreenDefaults() {
        assertArrayEquals(new String[]{"getRenderBoundingBox"}, java.util.Arrays.stream(BlockEntityRenderer.class.getDeclaredMethods()).map(java.lang.reflect.Method::getName).toArray(String[]::new));
        assertTrue(net.minecraft.client.renderer.blockentity.BlockEntityRenderer.class.isAssignableFrom(BlockEntityRenderer.class));
    }

    private static MethodNode method(Class<?> type, String name) throws Exception {
        var node = new ClassNode(); try (var stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) { assertNotNull(stream); new ClassReader(stream).accept(node, ClassReader.SKIP_FRAMES); }
        var methods = node.methods.stream().filter(method -> method.name.equals(name)).toList(); assertEquals(1, methods.size()); return methods.getFirst();
    }
    private static List<String> calls(MethodNode method) { var result = new ArrayList<String>(); for (var insn : method.instructions) if (insn instanceof MethodInsnNode call) result.add(call.owner + "." + call.name + call.desc); return result; }
    private static final class TypedInterpreter extends BasicInterpreter {
        TypedInterpreter() { super(Opcodes.ASM9); }
        @Override public BasicValue newValue(Type type) { return type != null && (type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY) ? new BasicValue(type) : super.newValue(type); }
        @Override public BasicValue newOperation(AbstractInsnNode insn) throws org.objectweb.asm.tree.analysis.AnalyzerException { return insn.getOpcode() == Opcodes.NEW ? new BasicValue(Type.getObjectType(((TypeInsnNode) insn).desc)) : super.newOperation(insn); }
    }
}
