package com.stardew.craft.platform.fabric.client.widget;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

class ClientNativeRemainderContractTest {
    @Test
    void highlightPredicateIsUniqueInsideTheHoveredSlotBranchBeforeNativeDrawing() throws Exception {
        var render = node("net/minecraft/client/gui/screens/inventory/AbstractContainerScreen").methods.stream().filter(method -> method.name.equals("render")).findFirst().orElseThrow();
        int hovered = -1; List<Integer> predicates = new ArrayList<>(); int highlight = -1;
        for (int i = 0; i < render.instructions.size(); i++) {
            var value = render.instructions.get(i);
            if (value instanceof FieldInsnNode field && field.name.equals("hoveredSlot") && field.getOpcode() == org.objectweb.asm.Opcodes.PUTFIELD) hovered = i;
            if (value instanceof MethodInsnNode call && call.owner.equals("net/minecraft/world/inventory/Slot") && call.name.equals("isHighlightable")) predicates.add(i);
            if (value instanceof MethodInsnNode call && call.name.equals("renderSlotHighlight")) { assertEquals("(Lnet/minecraft/client/gui/GuiGraphics;III)V", call.desc); highlight = i; }
        }
        assertEquals(1, predicates.size()); assertTrue(hovered >= 0); assertTrue(hovered < predicates.getFirst()); assertTrue(predicates.getFirst() < highlight);
    }
    @Test
    void framebufferWrappersTargetOnlyDepthStorageAndDepthAttachmentAndKeepStatusAndClear() throws Exception {
        var create = node("com/mojang/blaze3d/pipeline/RenderTarget").methods.stream().filter(method -> method.name.equals("createBuffers")).findFirst().orElseThrow();
        List<Integer> images = new ArrayList<>(); List<Integer> attachments = new ArrayList<>(); int status = -1; int clear = -1;
        for (int i = 0; i < create.instructions.size(); i++) if (create.instructions.get(i) instanceof MethodInsnNode call) {
            if (call.name.equals("_texImage2D")) images.add(i);
            if (call.name.equals("_glFramebufferTexture2D")) attachments.add(i);
            if (call.name.equals("checkStatus")) status = i;
            if (call.name.equals("clear")) clear = i;
        }
        assertEquals(2, images.size()); assertEquals(2, attachments.size()); assertTrue(images.getLast() < attachments.getFirst()); assertTrue(attachments.getLast() < status); assertTrue(status < clear);
        var mixin = node("com/stardew/craft/platform/mixin/client/framebuffer/mixin/RenderTargetStencilMixin");
        assertEquals(0, ordinal(mixin, "stardewcraft$depthStorage")); assertEquals(1, ordinal(mixin, "stardewcraft$depthAndStencilAttachments"));
    }
    private static int ordinal(ClassNode node, String methodName) {
        var method = node.methods.stream().filter(value -> value.name.equals(methodName)).findFirst().orElseThrow();
        List<AnnotationNode> annotations = new ArrayList<>(); if (method.visibleAnnotations != null) annotations.addAll(method.visibleAnnotations); if (method.invisibleAnnotations != null) annotations.addAll(method.invisibleAnnotations);
        var wrap = annotations.stream().filter(value -> value.desc.endsWith("/WrapOperation;")).findFirst().orElseThrow();
        Object at = value(wrap, "at"); var annotation = at instanceof List<?> list ? (AnnotationNode) list.getFirst() : (AnnotationNode) at;
        return (int) value(annotation, "ordinal");
    }
    private static Object value(AnnotationNode node, String name) {
        for (int i = 0; i < node.values.size(); i += 2) if (node.values.get(i).equals(name)) return node.values.get(i + 1);
        throw new AssertionError(name);
    }
    private static ClassNode node(String name) throws Exception {
        try (var bytes = ClientNativeRemainderContractTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(bytes); var node = new ClassNode(); new ClassReader(bytes).accept(node, ClassReader.SKIP_FRAMES); return node;
        }
    }
}
