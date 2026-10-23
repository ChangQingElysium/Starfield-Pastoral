package com.stardew.craft.platform.fabric.client.event.registration;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

class RegistrationHookContractTest {
    @Test
    void systemInjectionFollowsTheBlockedUserReturnAndPrecedesBothDisplayBranchesLoggingAndNarration() throws IOException {
        var method = method(node("net/minecraft/client/multiplayer/chat/ChatListener"), "handleSystemMessage");
        List<Integer> conditions = new ArrayList<>();
        for (int i = 0; i < method.instructions.size(); i++) if (method.instructions.get(i) instanceof JumpInsnNode jump && jump.getOpcode() == Opcodes.IFEQ) conditions.add(i);
        assertEquals(3, conditions.size()); int hook = conditions.get(2);
        assertInstanceOf(VarInsnNode.class, method.instructions.get(hook).getPrevious());
        var overlay = (VarInsnNode) method.instructions.get(hook).getPrevious(); assertEquals(Opcodes.ILOAD, overlay.getOpcode()); assertEquals(2, overlay.var);
        assertTrue(call(method, "net/minecraft/client/Minecraft", "isBlocked") < hook);
        assertTrue(java.util.stream.StreamSupport.stream(method.instructions.spliterator(), false).anyMatch(value -> value.getOpcode() == Opcodes.RETURN && method.instructions.indexOf(value) < hook));
        assertTrue(hook < call(method, "net/minecraft/client/gui/Gui", "setOverlayMessage"));
        assertTrue(hook < call(method, "net/minecraft/client/gui/components/ChatComponent", "addMessage"));
        assertTrue(hook < call(method, "net/minecraft/client/multiplayer/chat/ChatListener", "logSystemMessage"));
        assertTrue(hook < call(method, "net/minecraft/client/GameNarrator", "say"));
    }
    @Test
    void buffersAreRegisteredAfterDefaultMapInitializationBeforeTheSameMapIsBoundToTheNativeSource() throws IOException {
        var ctor = method(node("net/minecraft/client/renderer/RenderBuffers"), "<init>");
        assertTrue(call(ctor, "net/minecraft/Util", "make") < call(ctor, "net/minecraft/client/renderer/MultiBufferSource", "immediateWithBuffers"));
        var wrapped = method(node("com/stardew/craft/platform/mixin/client/event/registration/mixin/RenderBuffersRegistrationMixin"), "stardewcraft$buffers");
        assertTrue(call(wrapped, "com/llamalad7/mixinextras/injector/wrapoperation/Operation", "call")
                < call(wrapped, "com/stardew/craft/platform/fabric/client/event/registration/FabricRenderRegistrations", "buffers"));
        var effects = node("net/minecraft/client/renderer/DimensionSpecialEffects");
        assertTrue(effects.fields.stream().anyMatch(field -> field.name.equals("EFFECTS") && field.desc.equals("Lit/unimi/dsi/fastutil/objects/Object2ObjectMap;")));
    }
    private static int call(MethodNode method, String owner, String name) {
        for (int i = 0; i < method.instructions.size(); i++) if (method.instructions.get(i) instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) return i;
        fail(owner + "." + name); return -1;
    }
    private static MethodNode method(ClassNode owner, String name) { return owner.methods.stream().filter(method -> method.name.equals(name)).findFirst().orElseThrow(); }
    private static ClassNode node(String name) throws IOException {
        try (var bytes = RegistrationHookContractTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(bytes); var node = new ClassNode(); new ClassReader(bytes).accept(node, ClassReader.SKIP_FRAMES); return node;
        }
    }
}
