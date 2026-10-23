package com.stardew.craft.platform.fabric.client.render;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

class GuiLayerHookContractTest {
    @Test
    void nativeLowerGroupHasTheSixRetainedCallbacksAndEveryNativeLayerAdvancesZ() throws IOException {
        var gui = node("net/minecraft/client/gui/Gui");
        var constructor = method(gui, "<init>");
        List<String> references = new ArrayList<>();
        for (var instruction : constructor.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals("net/minecraft/client/gui/LayeredDraw")
                    && call.name.equals("add") && call.desc.contains("Ljava/util/function/BooleanSupplier;")) break;
            if (instruction instanceof InvokeDynamicInsnNode dynamic && dynamic.desc.endsWith("Lnet/minecraft/client/gui/LayeredDraw$Layer;")) {
                references.add(((Handle) dynamic.bsmArgs[1]).getName());
            }
        }
        // Two six/eight callback children are assembled before the first outer add(child,predicate).
        assertEquals(14, references.size());
        assertEquals(List.of("renderCameraOverlays", "renderCrosshair", "renderHotbarAndDecorations", "renderExperienceLevel", "renderEffects"), references.subList(0, 5));
        var bossCallback = method(gui, references.get(5));
        assertTrue(calls(bossCallback).contains("net/minecraft/client/gui/components/BossHealthOverlay.render"));
        var draw = method(node("net/minecraft/client/gui/LayeredDraw"), "renderInner");
        assertTrue(calls(draw).contains("net/minecraft/client/gui/LayeredDraw$Layer.render"));
        assertTrue(calls(draw).contains("com/mojang/blaze3d/vertex/PoseStack.translate"));
        assertTrue(java.util.stream.StreamSupport.stream(draw.instructions.spliterator(), false)
                .anyMatch(instruction -> instruction instanceof org.objectweb.asm.tree.LdcInsnNode constant && Float.valueOf(200).equals(constant.cst)));
    }

    @Test
    void heightPatchesHaveOnePreciseConstantAndTheHealthHelpersMatchActualNativeDescriptors() throws IOException {
        var gui = node("net/minecraft/client/gui/Gui");
        assertEquals(1, intConstants(method(gui, "renderVehicleHealth"), 39));
        assertEquals(1, intConstants(method(gui, "renderSelectedItemName"), 59));
        var mixin = node("com/stardew/craft/platform/mixin/client/render/mixin/GuiLowerLayersMixin");
        int shadowMethods = 0;
        for (var shadow : mixin.methods) {
            if (shadow.visibleAnnotations == null || shadow.visibleAnnotations.stream().noneMatch(annotation -> annotation.desc.equals("Lorg/spongepowered/asm/mixin/Shadow;"))) continue;
            shadowMethods++;
            assertTrue(gui.methods.stream().anyMatch(nativeMethod -> nativeMethod.name.equals(shadow.name) && nativeMethod.desc.equals(shadow.desc)), shadow.name + shadow.desc);
        }
        assertEquals(12, shadowMethods);
        int shadowFields = 0;
        for (var shadow : mixin.fields) {
            if (shadow.visibleAnnotations == null || shadow.visibleAnnotations.stream().noneMatch(annotation -> annotation.desc.equals("Lorg/spongepowered/asm/mixin/Shadow;"))) continue;
            shadowFields++;
            assertTrue(gui.fields.stream().anyMatch(nativeField -> nativeField.name.equals(shadow.name) && nativeField.desc.equals(shadow.desc)), shadow.name + shadow.desc);
        }
        assertEquals(10, shadowFields);
        var reset = calls(method(node("com/stardew/craft/platform/mixin/client/render/mixin/GuiRenderEventsMixin"), "stardewcraft$renderGui"));
        assertTrue(reset.indexOf("com/stardew/craft/platform/fabric/client/render/GuiHudLayout.reset")
                < reset.indexOf("com/stardew/craft/platform/fabric/client/render/FabricGuiRenderEvents.render"));
    }

    private static long intConstants(MethodNode method, int value) {
        return java.util.stream.StreamSupport.stream(method.instructions.spliterator(), false)
                .filter(instruction -> instruction instanceof IntInsnNode constant && constant.operand == value).count();
    }

    private static List<String> calls(MethodNode method) {
        List<String> result = new ArrayList<>();
        for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call) result.add(call.owner + "." + call.name);
        return result;
    }

    private static MethodNode method(ClassNode owner, String name) {
        return owner.methods.stream().filter(method -> method.name.equals(name)).findFirst().orElseThrow();
    }

    private static ClassNode node(String owner) throws IOException {
        try (var bytes = GuiLayerHookContractTest.class.getClassLoader().getResourceAsStream(owner + ".class")) {
            assertNotNull(bytes, owner);
            var result = new ClassNode();
            new ClassReader(bytes).accept(result, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return result;
        }
    }
}
