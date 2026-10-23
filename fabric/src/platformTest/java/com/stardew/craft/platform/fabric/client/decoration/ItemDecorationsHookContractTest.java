package com.stardew.craft.platform.fabric.client.decoration;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

class ItemDecorationsHookContractTest {
    @Test
    void tailKeepsNativeCountDurabilityAndCooldownBeforeTheBalancedPoseReturn() throws IOException {
        var gui = node("net/minecraft/client/gui/GuiGraphics");
        var method = gui.methods.stream().filter(value -> value.name.equals("renderItemDecorations") && value.desc.endsWith("Ljava/lang/String;)V")).findFirst().orElseThrow();
        int pop = position(method, "com/mojang/blaze3d/vertex/PoseStack", "popPose");
        assertTrue(position(method, "net/minecraft/client/gui/GuiGraphics", "drawString") < pop);
        for (String name : new String[] { "getCount", "isBarVisible", "getBarWidth", "getBarColor" })
            assertTrue(position(method, "net/minecraft/world/item/ItemStack", name) < pop);
        assertTrue(position(method, "net/minecraft/world/item/ItemCooldowns", "getCooldownPercent") < pop);
        assertEquals(3, java.util.stream.StreamSupport.stream(method.instructions.spliterator(), false)
                .filter(value -> value instanceof MethodInsnNode call && call.owner.equals("net/minecraft/client/gui/GuiGraphics") && call.name.equals("fill")).count());
        assertEquals(2, java.util.stream.StreamSupport.stream(method.instructions.spliterator(), false).filter(value -> value.getOpcode() == Opcodes.RETURN).count());
        assertTrue(method.instructions.indexOf(method.instructions.getLast()) > pop);
    }
    @Test
    void snapshotCoversTheNeoStateSetAndRestoreDoesNotRewriteScissorCoordinatesOrViewport() throws IOException {
        var state = node("com/stardew/craft/platform/fabric/client/decoration/DecorationGlState$Snapshot");
        assertEquals(27, state.recordComponents.size());
        var restore = state.methods.stream().filter(method -> method.name.equals("restore")).findFirst().orElseThrow();
        for (var instruction : restore.instructions) if (instruction instanceof MethodInsnNode call) {
            assertFalse(call.name.equals("_scissorBox")); assertFalse(call.name.equals("_viewport"));
        }
        for (String name : new String[] { "_blendFuncSeparate", "_depthMask", "_depthFunc", "_polygonOffset", "_logicOp", "_stencilFunc", "_stencilMask", "_stencilOp", "_colorMask" })
            assertTrue(position(restore, "com/mojang/blaze3d/platform/GlStateManager", name) >= 0);
    }
    private static int position(MethodNode method, String owner, String name) {
        for (int i = 0; i < method.instructions.size(); i++) if (method.instructions.get(i) instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) return i;
        fail(owner + "." + name); return -1;
    }
    private static ClassNode node(String name) throws IOException {
        try (var bytes = ItemDecorationsHookContractTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(bytes); var node = new ClassNode(); new ClassReader(bytes).accept(node, ClassReader.SKIP_FRAMES); return node;
        }
    }
}
