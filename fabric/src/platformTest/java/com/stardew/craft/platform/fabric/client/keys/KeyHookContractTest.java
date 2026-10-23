package com.stardew.craft.platform.fabric.client.keys;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

class KeyHookContractTest {
    @Test
    void optionsStillHasOneNativeKeyReadAndWriteWithTheActualMappingInScope() throws IOException {
        var node = node("net/minecraft/client/Options");
        var method = node.methods.stream().filter(value -> value.name.equals("processOptions")).findFirst().orElseThrow();
        var calls = java.util.stream.StreamSupport.stream(method.instructions.spliterator(), false)
                .filter(value -> value instanceof MethodInsnNode).map(value -> (MethodInsnNode) value).toList();
        assertEquals(1, calls.stream().filter(call -> call.owner.equals("net/minecraft/client/KeyMapping") && call.name.equals("saveString")).count());
        var reads = calls.stream().filter(call -> call.owner.equals("com/mojang/blaze3d/platform/InputConstants") && call.name.equals("getKey")).toList();
        assertEquals(1, reads.size()); assertEquals("(Ljava/lang/String;)Lcom/mojang/blaze3d/platform/InputConstants$Key;", reads.getFirst().desc);
        int read = method.instructions.indexOf(reads.getFirst());
        assertEquals(1, method.localVariables.stream().filter(local -> local.desc.equals("Lnet/minecraft/client/KeyMapping;")
                && method.instructions.indexOf(local.start) <= read && method.instructions.indexOf(local.end) > read).count());
        assertEquals(1, calls.stream().filter(call -> call.owner.equals("net/minecraft/client/KeyMapping") && call.name.equals("setKey")).count());
    }

    @Test
    void resetHooksOnlyTargetNativeResetActionsAndReleaseCanBeAddedAtTheActualSuperclass() throws IOException {
        var screen = node("net/minecraft/client/gui/screens/options/controls/KeyBindsScreen");
        assertEquals("net/minecraft/client/gui/screens/options/OptionsSubScreen", screen.superName);
        assertTrue(screen.methods.stream().noneMatch(method -> method.name.equals("keyReleased")));
        assertTrue(screen.fields.stream().anyMatch(field -> field.name.equals("selectedKey") && field.desc.equals("Lnet/minecraft/client/KeyMapping;")));
        assertTrue(screen.fields.stream().anyMatch(field -> field.name.equals("keyBindsList") && field.desc.equals("Lnet/minecraft/client/gui/screens/options/controls/KeyBindsList;")));
        for (var entry : List.of(screen, node("net/minecraft/client/gui/screens/options/controls/KeyBindsList$KeyEntry"))) {
            int resets = 0;
            for (var method : entry.methods) for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call
                    && call.owner.equals(entry == screen ? "net/minecraft/client/KeyMapping" : "net/minecraft/client/Options") && call.name.equals("setKey")) {
                resets++; assertTrue((method.access & Opcodes.ACC_SYNTHETIC) != 0, "reset call moved outside its native lambda");
                assertTrue((method.access & Opcodes.ACC_STATIC) == 0, "reset callback unexpectedly became static");
            }
            assertEquals(1, resets);
        }
    }

    @Test
    void keyMixinUsesNativeInstanceStateAndLeavesRawReleaseAndToggleOperationsAvailable() throws IOException {
        var nativeKeys = node("net/minecraft/client/KeyMapping");
        assertTrue(nativeKeys.fields.stream().anyMatch(field -> field.name.equals("ALL") && field.desc.equals("Ljava/util/Map;") && (field.access & Opcodes.ACC_STATIC) != 0));
        assertTrue(nativeKeys.fields.stream().anyMatch(field -> field.name.equals("key") && field.desc.equals("Lcom/mojang/blaze3d/platform/InputConstants$Key;")));
        assertTrue(nativeKeys.fields.stream().anyMatch(field -> field.name.equals("clickCount") && field.desc.equals("I")));
        for (var method : List.of("releaseAll", "resetToggleKeys", "setAll", "resetMapping")) assertTrue(nativeKeys.methods.stream().anyMatch(value -> value.name.equals(method)));
        for (var method : List.of("click", "set")) assertTrue(nativeKeys.methods.stream().filter(value -> value.name.equals(method))
                .allMatch(value -> (value.access & Opcodes.ACC_STATIC) != 0));
    }

    private static ClassNode node(String name) throws IOException {
        try (var bytes = KeyHookContractTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(bytes, name); var node = new ClassNode(); new ClassReader(bytes).accept(node, ClassReader.SKIP_FRAMES); return node;
        }
    }
}
