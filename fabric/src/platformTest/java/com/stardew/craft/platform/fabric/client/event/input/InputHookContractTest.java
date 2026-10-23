package com.stardew.craft.platform.fabric.client.event.input;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Native target and local-variable checks; no Minecraft client or OpenGL context is created. */
class InputHookContractTest {
    @Test
    void keyboardSliceContainsBothReleaseAndPressExitsAfterAllEarlyConsumers() throws IOException {
        var method = method("net/minecraft/client/KeyboardHandler", "keyPress", "(JIIII)V");
        int start = call(method, "com/mojang/blaze3d/platform/InputConstants", "getKey").getFirst();
        assertEquals(2, returnsAfter(method, start));
        assertEquals(6, returnsAfter(method, 0));
        assertTrue(start > call(method, "net/minecraft/client/gui/screens/Screen", "wrapScreenError").getFirst());
        assertTrue(start > call(method, "com/mojang/blaze3d/platform/Window", "toggleFullScreen").getFirst());
    }

    @Test
    void mouseHooksFollowTouchBookkeepingAndUseTheAdjustedLiveScrollLocals() throws IOException {
        var press = method("net/minecraft/client/MouseHandler", "onPress", "(JIII)V");
        assertEquals(4, returnsAfter(press, 0));
        int pre = call(press, "net/minecraft/client/Minecraft", "getOverlay").getFirst();
        assertEquals(3, returnsAfter(press, 0) - returnsAfter(press, pre));
        assertTrue(pre < call(press, "net/minecraft/client/KeyMapping", "set").getFirst());
        var scroll = method("net/minecraft/client/MouseHandler", "onScroll", "(JDD)V");
        int point = call(scroll, "net/minecraft/client/player/LocalPlayer", "isSpectator").getFirst();
        assertEquals(1, call(scroll, "net/minecraft/client/player/LocalPlayer", "isSpectator").size());
        assertLiveLocal(scroll, point, 10, "D");
        assertLiveLocal(scroll, point, 12, "D");
        assertTrue(point > call(scroll, "net/minecraft/client/gui/screens/Screen", "mouseScrolled").getFirst());
        assertTrue(point < call(scroll, "net/minecraft/world/entity/player/Inventory", "swapPaint").getFirst());
    }

    @Test
    void interactionEventsRemainInsideNativeGuardsAndTheNativeHandLoop() throws IOException {
        var attack = method("net/minecraft/client/Minecraft", "startAttack", "()Z");
        assertEquals(1, call(attack, "net/minecraft/world/phys/HitResult", "getType").size());
        assertTrue(call(attack, "net/minecraft/world/phys/HitResult", "getType").getFirst()
                > call(attack, "net/minecraft/world/item/ItemStack", "isItemEnabled").getFirst());
        assertEquals(1, call(attack, "net/minecraft/client/player/LocalPlayer", "swing").size());
        var held = method("net/minecraft/client/Minecraft", "continueAttack", "(Z)V");
        int heldPoint = call(held, "net/minecraft/world/phys/BlockHitResult", "getDirection").getFirst();
        assertEquals(1, call(held, "net/minecraft/world/phys/BlockHitResult", "getDirection").size());
        assertLiveLocal(held, heldPoint, 2, "Lnet/minecraft/world/phys/BlockHitResult;");
        assertLiveLocal(held, heldPoint, 3, "Lnet/minecraft/core/BlockPos;");
        assertTrue(heldPoint < call(held, "net/minecraft/client/multiplayer/MultiPlayerGameMode", "continueDestroyBlock").getFirst());
        var use = method("net/minecraft/client/Minecraft", "startUseItem", "()V");
        int usePoint = call(use, "net/minecraft/client/player/LocalPlayer", "getItemInHand").getFirst();
        assertEquals(1, call(use, "net/minecraft/client/player/LocalPlayer", "getItemInHand").size());
        assertLiveLocal(use, usePoint, 4, "Lnet/minecraft/world/InteractionHand;");
        assertTrue(usePoint > call(use, "net/minecraft/world/InteractionHand", "values").getFirst());
        // NeoForge gates the first two (entity/block) but not the third (air-use) swing predicates.
        assertEquals(3, call(use, "net/minecraft/world/InteractionResult", "shouldSwing").size());
        var pick = method("net/minecraft/client/Minecraft", "pickBlock", "()V");
        assertTrue(call(pick, "net/minecraft/client/player/LocalPlayer", "getAbilities").getFirst()
                > call(pick, "net/minecraft/world/phys/HitResult", "getType").getFirst());
    }

    private static void assertLiveLocal(MethodNode method, int point, int slot, String descriptor) {
        assertTrue(method.localVariables.stream().anyMatch(local -> local.index == slot && local.desc.equals(descriptor)
                && method.instructions.indexOf(local.start) <= point && method.instructions.indexOf(local.end) > point),
                "No live " + descriptor + " in slot " + slot + " at " + point);
    }

    private static int returnsAfter(MethodNode method, int point) {
        int count = 0;
        for (int i = point; i < method.instructions.size(); i++) if (method.instructions.get(i).getOpcode() == Opcodes.RETURN) count++;
        return count;
    }

    private static List<Integer> call(MethodNode method, String owner, String name) {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < method.instructions.size(); i++) {
            AbstractInsnNode instruction = method.instructions.get(i);
            if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) result.add(i);
        }
        assertFalse(result.isEmpty(), owner + "." + name);
        return result;
    }

    private static MethodNode method(String owner, String name, String descriptor) throws IOException {
        try (var bytes = InputHookContractTest.class.getClassLoader().getResourceAsStream(owner + ".class")) {
            assertNotNull(bytes, owner);
            var node = new ClassNode();
            new ClassReader(bytes).accept(node, ClassReader.SKIP_FRAMES);
            return node.methods.stream().filter(method -> method.name.equals(name) && method.desc.equals(descriptor)).findFirst().orElseThrow();
        }
    }
}
