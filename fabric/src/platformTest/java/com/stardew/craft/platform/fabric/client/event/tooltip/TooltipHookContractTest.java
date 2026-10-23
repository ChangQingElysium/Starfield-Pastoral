package com.stardew.craft.platform.fabric.client.event.tooltip;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

class TooltipHookContractTest {
    @Test
    void colorRunsAfterPositioningAndPosePushBeforeTheManagedBackgroundAndBothNativePasses() throws IOException {
        var method = method("net/minecraft/client/gui/GuiGraphics", "renderTooltipInternal");
        int measure = call(method, "net/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipComponent", "getWidth").getFirst();
        int position = call(method, "net/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner", "positionTooltip").getFirst();
        int push = call(method, "com/mojang/blaze3d/vertex/PoseStack", "pushPose").getFirst();
        int background = call(method, "net/minecraft/client/gui/GuiGraphics", "drawManaged").getFirst();
        int translate = call(method, "com/mojang/blaze3d/vertex/PoseStack", "translate").getFirst();
        int text = call(method, "net/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipComponent", "renderText").getFirst();
        int images = call(method, "net/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipComponent", "renderImage").getFirst();
        assertTrue(measure < position && position < push && push < background && background < translate && translate < text && text < images);
        assertEquals(1, call(method, "net/minecraft/client/gui/GuiGraphics", "drawManaged").size());
        for (int slot : List.of(8, 9, 11, 12)) assertTrue(method.localVariables.stream().anyMatch(local -> local.index == slot
                && local.desc.equals("I") && method.instructions.indexOf(local.start) <= background && method.instructions.indexOf(local.end) > background), "missing live tooltip dimension/origin slot " + slot);
        assertEquals(2, java.util.stream.StreamSupport.stream(method.instructions.spliterator(), false)
                .filter(instruction -> instruction.getOpcode() == Opcodes.RETURN).count()); // empty list exits before Pre
    }

    @Test
    void tooltipLineQueryHasAnEarlyHiddenReturnAndAnActualNullablePlayerArgument() throws IOException {
        var tooltip = method("net/minecraft/world/item/ItemStack", "getTooltipLines");
        assertEquals("(Lnet/minecraft/world/item/Item$TooltipContext;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/TooltipFlag;)Ljava/util/List;", tooltip.desc);
        assertEquals(2, java.util.stream.StreamSupport.stream(tooltip.instructions.spliterator(), false)
                .filter(instruction -> instruction.getOpcode() == Opcodes.ARETURN).count());
        assertTrue(java.util.stream.StreamSupport.stream(tooltip.instructions.spliterator(), false)
                .anyMatch(instruction -> instruction instanceof FieldInsnNode field && field.name.equals("HIDE_TOOLTIP")));
    }

    @Test
    void handSelectionPrecedesTheTwoActualHandCallsAndBufferFlushFollowsThem() throws IOException {
        var hands = method("net/minecraft/client/renderer/ItemInHandRenderer", "renderHandsWithItems");
        int selected = call(hands, "net/minecraft/client/renderer/ItemInHandRenderer", "evaluateWhichHandsToRender").getFirst();
        var calls = call(hands, "net/minecraft/client/renderer/ItemInHandRenderer", "renderArmWithItem");
        assertEquals(2, calls.size());
        assertTrue(selected < calls.getFirst());
        assertTrue(calls.getLast() < call(hands, "net/minecraft/client/renderer/MultiBufferSource$BufferSource", "endBatch").getFirst());
        var run = method("net/minecraft/client/Minecraft", "runTick");
        int render = call(run, "net/minecraft/client/renderer/GameRenderer", "render").getFirst();
        var gameRenderer = new ArrayList<Integer>();
        for (int i = 0; i < run.instructions.size(); i++) if (run.instructions.get(i) instanceof LdcInsnNode value && value.cst.equals("gameRenderer")) gameRenderer.add(i);
        assertEquals(1, gameRenderer.size());
        assertTrue(gameRenderer.getFirst() < render);
        int postPop = call(run, "net/minecraft/util/profiling/ProfilerFiller", "pop").stream().filter(index -> index > render).findFirst().orElseThrow();
        assertTrue(postPop < call(run, "net/minecraft/client/Minecraft", "renderFpsMeter").getFirst());
        var minecraft = node("net/minecraft/client/Minecraft");
        assertTrue(minecraft.fields.stream().anyMatch(field -> field.name.equals("timer") && field.desc.equals("Lnet/minecraft/client/DeltaTracker$Timer;")));
    }

    private static List<Integer> call(MethodNode method, String owner, String name) {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < method.instructions.size(); i++) if (method.instructions.get(i) instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) result.add(i);
        assertFalse(result.isEmpty(), owner + "." + name);
        return result;
    }
    private static MethodNode method(String owner, String name) throws IOException {
        return node(owner).methods.stream().filter(method -> method.name.equals(name)).findFirst().orElseThrow();
    }
    private static ClassNode node(String owner) throws IOException {
        try (var bytes = TooltipHookContractTest.class.getClassLoader().getResourceAsStream(owner + ".class")) {
            assertNotNull(bytes, owner);
            var result = new ClassNode(); new ClassReader(bytes).accept(result, ClassReader.SKIP_FRAMES); return result;
        }
    }
}
