package com.stardew.craft.platform.fabric.validation;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.concurrent.atomic.AtomicInteger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

/** Mandatory agent: the protocol peer may initialize vanilla data, never client or graphics classes. */
public final class ProtocolPeerGuard {
    private static final AtomicInteger BLOCKED = new AtomicInteger();
    private ProtocolPeerGuard() {}

    public static void premain(String ignored, Instrumentation instrumentation) {
        System.setProperty("stardew.protocolPeer.guard", "active");
        instrumentation.addTransformer(new ClassFileTransformer() {
            @Override public byte[] transform(ClassLoader loader, String name, Class<?> type,
                    ProtectionDomain domain, byte[] bytes) {
                if (name == null || !(name.startsWith("net/minecraft/client/") || name.startsWith("com/mojang/blaze3d/")
                        || name.startsWith("org/lwjgl/"))) return null;
                BLOCKED.incrementAndGet();
                System.setProperty("stardew.protocolPeer.blockedClientDefinitions", Integer.toString(BLOCKED.get()));
                var node = new ClassNode(); new ClassReader(bytes).accept(node, 0);
                MethodNode initializer = node.methods.stream().filter(method -> method.name.equals("<clinit>")).findFirst().orElse(null);
                if (initializer == null) {
                    initializer = new MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
                    node.methods.add(initializer);
                }
                initializer.instructions = new InsnList(); initializer.tryCatchBlocks.clear();
                if (initializer.localVariables != null) initializer.localVariables.clear();
                initializer.instructions.add(new TypeInsnNode(Opcodes.NEW, "java/lang/IllegalStateException"));
                initializer.instructions.add(new InsnNode(Opcodes.DUP));
                initializer.instructions.add(new LdcInsnNode("Protocol peer forbids client/graphics initialization: " + name));
                initializer.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/IllegalStateException", "<init>", "(Ljava/lang/String;)V", false));
                initializer.instructions.add(new InsnNode(Opcodes.ATHROW));
                initializer.maxStack = 3; initializer.maxLocals = 0;
                var writer = new ClassWriter(0); node.accept(writer); return writer.toByteArray();
            }
        });
    }
}
