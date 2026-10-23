package com.stardew.craft.platform.fabric.client.hook;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

class RenderHookContractTest {
    @Test
    void playerHookFollowsModelPropertiesAndLivingHookEnclosesItsPoseAndNameRendering() throws IOException {
        var player = node("net/minecraft/client/renderer/entity/player/PlayerRenderer").methods.stream()
                .filter(method -> method.name.equals("render") && method.desc.startsWith("(Lnet/minecraft/client/player/AbstractClientPlayer;")).findFirst().orElseThrow();
        int properties = call(player, "net/minecraft/client/renderer/entity/player/PlayerRenderer", "setModelProperties");
        int parent = call(player, "net/minecraft/client/renderer/entity/LivingEntityRenderer", "render");
        assertTrue(properties < parent); assertEquals(Opcodes.INVOKESPECIAL, player.instructions.get(parent).getOpcode());
        var living = node("net/minecraft/client/renderer/entity/LivingEntityRenderer").methods.stream()
                .filter(method -> method.name.equals("render") && method.desc.startsWith("(Lnet/minecraft/world/entity/LivingEntity;")).findFirst().orElseThrow();
        assertTrue(call(living, "com/mojang/blaze3d/vertex/PoseStack", "pushPose") < call(living, "com/mojang/blaze3d/vertex/PoseStack", "popPose"));
        assertTrue(call(living, "com/mojang/blaze3d/vertex/PoseStack", "popPose") < call(living, "net/minecraft/client/renderer/entity/EntityRenderer", "render"));
        assertEquals(1, java.util.stream.StreamSupport.stream(living.instructions.spliterator(), false).filter(value -> value.getOpcode() == Opcodes.RETURN).count());
    }

    @Test
    void nativeTransformAfterInjectionComposesAfterThePinnedLateModelWrapper() throws IOException {
        var injection = node("com/llamalad7/mixinextras/injector/wrapoperation/WrapOperationInjectionInfo");
        assertEquals("com/llamalad7/mixinextras/injector/MixinExtrasLateInjectionInfo", injection.superName);
        var itemRender = node("net/minecraft/client/renderer/entity/ItemRenderer").methods.stream().filter(method -> method.name.equals("render")).findFirst().orElseThrow();
        int transform = call(itemRender, "net/minecraft/client/renderer/block/model/ItemTransform", "apply");
        int translate = call(itemRender, "com/mojang/blaze3d/vertex/PoseStack", "translate");
        assertTrue(transform < translate);
        assertEquals(1, java.util.stream.StreamSupport.stream(itemRender.instructions.spliterator(), false)
                .filter(value -> value instanceof MethodInsnNode call && call.owner.equals("net/minecraft/client/renderer/block/model/ItemTransform") && call.name.equals("apply")).count());
        var wrapper = node("com/stardew/craft/platform/mixin/client/model/mixin/ItemModelTransformMixin").methods.stream()
                .filter(method -> method.name.equals("stardewcraft$transform")).findFirst().orElseThrow();
        int replacement = call(wrapper, "com/llamalad7/mixinextras/sugar/ref/LocalRef", "set");
        assertTrue(replacement < wrapper.instructions.size() - 1);
        // Standard @Inject AFTER is inserted after the original call. WrapOperation is late and
        // replaces that call in place, so the capture runs after the complete wrapper/model.set.
    }
    private static int call(MethodNode method, String owner, String name) {
        for (int i = 0; i < method.instructions.size(); i++) if (method.instructions.get(i) instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) return i;
        fail(owner + "." + name); return -1;
    }
    private static ClassNode node(String name) throws IOException {
        try (var bytes = RenderHookContractTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(bytes, name); var node = new ClassNode(); new ClassReader(bytes).accept(node, ClassReader.SKIP_FRAMES); return node;
        }
    }
}
