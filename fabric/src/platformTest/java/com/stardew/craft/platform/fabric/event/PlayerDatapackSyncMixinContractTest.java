package com.stardew.craft.platform.fabric.event;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.TypeInsnNode;

/** Match the maintained Mixin's full NEW owner, rather than only a packet's simple name. */
class PlayerDatapackSyncMixinContractTest {
    @Test void loginAndReloadInjectorsMatchTheExactNativePacketPackages() throws IOException {
        assertBoundary("stardewcraft$login", "placeNewPlayer", "net/minecraft/network/protocol/game/ClientboundUpdateRecipesPacket");
        assertBoundary("stardewcraft$reload", "reloadResources", "net/minecraft/network/protocol/common/ClientboundUpdateTagsPacket");
    }

    private static void assertBoundary(String handlerName, String nativeMethod, String packet) throws IOException {
        var mixin = node("com/stardew/craft/platform/mixin/event/mixin/PlayerDatapackSyncMixin");
        var handler = mixin.methods.stream().filter(method -> method.name.equals(handlerName)).findFirst().orElseThrow();
        List<AnnotationNode> annotations = new ArrayList<>();
        if (handler.visibleAnnotations != null) annotations.addAll(handler.visibleAnnotations);
        if (handler.invisibleAnnotations != null) annotations.addAll(handler.invisibleAnnotations);
        var inject = annotations.stream().filter(annotation -> annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;"))
                .findFirst().orElseThrow();
        assertEquals(List.of(nativeMethod), value(inject, "method"));
        @SuppressWarnings("unchecked") var targets = (List<AnnotationNode>) value(inject, "at");
        assertEquals(1, targets.size()); assertEquals("NEW", value(targets.getFirst(), "value"));
        assertEquals(packet, value(targets.getFirst(), "target"));
        var methods = node("net/minecraft/server/players/PlayerList").methods.stream().filter(method -> method.name.equals(nativeMethod)).toList();
        assertEquals(1, methods.size());
        long matches = java.util.stream.StreamSupport.stream(methods.getFirst().instructions.spliterator(), false)
                .filter(instruction -> instruction instanceof TypeInsnNode type && type.getOpcode() == Opcodes.NEW && type.desc.equals(packet)).count();
        assertEquals(1, matches, "The injector must match one full native packet class, including common/game package");
    }

    private static ClassNode node(String owner) throws IOException {
        try (var stream = PlayerDatapackSyncMixinContractTest.class.getClassLoader().getResourceAsStream(owner + ".class")) {
            assertNotNull(stream, owner); var node = new ClassNode(); new ClassReader(stream).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES); return node;
        }
    }
    private static Object value(AnnotationNode annotation, String name) {
        if (annotation.values != null) for (int i = 0; i < annotation.values.size(); i += 2)
            if (annotation.values.get(i).equals(name)) return annotation.values.get(i + 1);
        return null;
    }
}
