package com.stardew.craft.platform.fabric.client.event.viewport;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

class FogHookContractTest {
    @Test
    void finalFogShaderAssignmentsHaveOneJoinedExitAndTheOriginalCameraFogTypeIsLiveThere() throws IOException {
        var fog = node("net/minecraft/client/renderer/FogRenderer");
        var planes = method(fog, "setupFog");
        List<String> shader = new ArrayList<>();
        int finalShader = -1;
        for (int i = 0; i < planes.instructions.size(); i++) if (planes.instructions.get(i) instanceof MethodInsnNode call
                && call.owner.equals("com/mojang/blaze3d/systems/RenderSystem")) { shader.add(call.name); finalShader = i; }
        assertEquals(List.of("setShaderFogStart", "setShaderFogEnd", "setShaderFogShape"), shader);
        final int tail = finalShader;
        assertTrue(planes.localVariables.stream().anyMatch(local -> local.desc.equals("Lnet/minecraft/world/level/material/FogType;")
                && planes.instructions.indexOf(local.start) <= tail && planes.instructions.indexOf(local.end) > tail));
        assertEquals(1, java.util.stream.StreamSupport.stream(planes.instructions.spliterator(), false)
                .filter(instruction -> instruction.getOpcode() == Opcodes.RETURN).count());
        var colors = method(fog, "setupColor");
        // Ordinal zero is an intermediate powder-snow clear before the final common color stage.
        assertEquals(2, java.util.stream.StreamSupport.stream(colors.instructions.spliterator(), false)
                .filter(instruction -> instruction instanceof MethodInsnNode call && call.owner.equals("com/mojang/blaze3d/systems/RenderSystem") && call.name.equals("clearColor")).count());
        for (String name : List.of("fogRed", "fogGreen", "fogBlue"))
            assertTrue(fog.fields.stream().anyMatch(field -> field.name.equals(name) && field.desc.equals("F")));
    }

    @Test
    void fogColorWrapperUsesTheLateInjectorThatComposesAfterThePondStandardModifyArgs() throws IOException {
        // WrapOperation's documented composition is backed by the actual pinned runtime's late injector.
        var wrap = node("com/llamalad7/mixinextras/injector/wrapoperation/WrapOperationInjectionInfo");
        assertEquals("com/llamalad7/mixinextras/injector/MixinExtrasLateInjectionInfo", wrap.superName);
        var mixin = node("com/stardew/craft/platform/mixin/client/event/viewport/mixin/FogViewportMixin");
        var color = method(mixin, "stardewcraft$fogColor");
        List<String> calls = new ArrayList<>();
        for (var instruction : color.instructions) if (instruction instanceof MethodInsnNode call) calls.add(call.owner + "." + call.name);
        assertTrue(calls.indexOf("com/stardew/craft/platform/fabric/client/event/viewport/FabricViewportEvents.color")
                < calls.indexOf("com/llamalad7/mixinextras/injector/wrapoperation/Operation.call"));
    }

    private static MethodNode method(ClassNode owner, String name) {
        return owner.methods.stream().filter(method -> method.name.equals(name)).findFirst().orElseThrow();
    }
    private static ClassNode node(String owner) throws IOException {
        try (var bytes = FogHookContractTest.class.getClassLoader().getResourceAsStream(owner + ".class")) {
            assertNotNull(bytes, owner);
            var result = new ClassNode(); new ClassReader(bytes).accept(result, ClassReader.SKIP_FRAMES); return result;
        }
    }
}
