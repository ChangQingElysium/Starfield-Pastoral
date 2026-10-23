package com.stardew.craft.platform.fabric.client.event.screen;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

class ScreenHookContractTest {
    @Test
    void openingJoinIsAfterVanillaFallbackAndBeforeTheExactAssignmentOperands() throws IOException {
        var method = method(node("net/minecraft/client/Minecraft"), "setScreen");
        var assignments = java.util.stream.StreamSupport.stream(method.instructions.spliterator(), false)
                .filter(value -> value instanceof FieldInsnNode field && field.name.equals("screen") && field.getOpcode() == Opcodes.PUTFIELD).toList();
        assertEquals(1, assignments.size()); var assignment = assignments.getFirst();
        assertInstanceOf(VarInsnNode.class, assignment.getPrevious()); assertEquals(Opcodes.ALOAD, assignment.getPrevious().getOpcode()); assertEquals(1, ((VarInsnNode) assignment.getPrevious()).var);
        assertInstanceOf(VarInsnNode.class, assignment.getPrevious().getPrevious()); assertEquals(Opcodes.ALOAD, assignment.getPrevious().getPrevious().getOpcode()); assertEquals(0, ((VarInsnNode) assignment.getPrevious().getPrevious()).var);
        int commit = method.instructions.indexOf(assignment);
        assertTrue(call(method, "net/minecraft/client/gui/screens/Screen", "removed") < call(method, "net/minecraft/client/gui/screens/TitleScreen", "<init>"));
        assertTrue(call(method, "net/minecraft/client/gui/screens/DeathScreen", "<init>") < commit);
        assertTrue(call(method, "net/minecraft/client/player/LocalPlayer", "respawn") < commit);
        assertTrue(commit < call(method, "net/minecraft/client/gui/screens/Screen", "added"));
        assertTrue(commit < call(method, "net/minecraft/client/gui/screens/Screen", "init"));
        var defer = method(node("com/stardew/craft/platform/mixin/client/event/screen/mixin/ScreenOpeningMixin"), "stardewcraft$deferRemoved");
        assertTrue(java.util.stream.StreamSupport.stream(defer.instructions.spliterator(), false).noneMatch(value -> value instanceof MethodInsnNode));
        var transition = method(node("com/stardew/craft/platform/fabric/client/event/screen/FabricScreenEvents"), "transition");
        assertTrue(call(transition, "com/stardew/craft/platform/fabric/client/event/ScreenEvent$Opening", "isCanceled")
                < call(transition, "net/minecraft/client/gui/screens/Screen", "removed"));
    }

    @Test
    void inputWrapsEachActualScreenCallbackAndIncludesStaticErrorHandlerLambdas() throws IOException {
        var mouse = node("net/minecraft/client/MouseHandler");
        for (String name : List.of("mouseClicked", "mouseReleased", "mouseDragged", "mouseScrolled")) assertEquals(1, callbackCount(mouse, name));
        var keyboard = node("net/minecraft/client/KeyboardHandler");
        for (String name : List.of("keyPressed", "keyReleased")) assertEquals(1, callbackCount(keyboard, name));
        for (var owner : List.of(mouse, keyboard)) for (var method : owner.methods) {
            if (method.instructions.iterator().hasNext() && java.util.stream.StreamSupport.stream(method.instructions.spliterator(), false)
                    .anyMatch(value -> value instanceof MethodInsnNode call && call.owner.equals("net/minecraft/client/gui/screens/Screen")
                            && List.of("mouseClicked", "mouseReleased", "keyPressed", "keyReleased").contains(call.name)))
                assertTrue((method.access & Opcodes.ACC_STATIC) != 0);
        }
        assertEquals(1, callbackCount(node("net/minecraft/client/renderer/GameRenderer"), "renderWithTooltip"));
    }
    private static long callbackCount(ClassNode node, String name) {
        return node.methods.stream().flatMap(method -> java.util.stream.StreamSupport.stream(method.instructions.spliterator(), false))
                .filter(value -> value instanceof MethodInsnNode call && call.owner.equals("net/minecraft/client/gui/screens/Screen") && call.name.equals(name)).count();
    }
    private static int call(MethodNode method, String owner, String name) {
        for (int i = 0; i < method.instructions.size(); i++) if (method.instructions.get(i) instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) return i;
        fail(owner + "." + name); return -1;
    }
    private static MethodNode method(ClassNode node, String name) { return node.methods.stream().filter(method -> method.name.equals(name)).findFirst().orElseThrow(); }
    private static ClassNode node(String name) throws IOException {
        try (var bytes = ScreenHookContractTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(bytes, name); var node = new ClassNode(); new ClassReader(bytes).accept(node, ClassReader.SKIP_FRAMES); return node;
        }
    }
}
