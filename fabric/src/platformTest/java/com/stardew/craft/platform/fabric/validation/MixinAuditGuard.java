package com.stardew.craft.platform.fabric.validation;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

/** Child-process guard: real Mixin transformation is allowed, game/graphics initialization is rejected. */
public final class MixinAuditGuard implements ClassFileTransformer {
    public static void premain(String ignored, Instrumentation instrumentation) {
        instrumentation.addTransformer(new MixinAuditGuard());
        System.setProperty("stardew.mixinAudit.guard", "active");
    }

    @Override public byte[] transform(ClassLoader loader, String name, Class<?> redefining, ProtectionDomain domain, byte[] bytes) {
        return protect(name, bytes);
    }

    static byte[] protect(String name, byte[] bytes) {
        if (name == null) return null;
        boolean game = name.startsWith("net/minecraft/") || name.startsWith("com/mojang/blaze3d/");
        boolean graphics = name.equals("org/lwjgl/glfw/GLFW") || name.equals("org/lwjgl/opengl/GL");
        if (!game && !graphics) return null;
        var node = new ClassNode(); new ClassReader(bytes).accept(node, 0); boolean changed = false;
        for (var method : node.methods) {
            boolean forbidden = method.name.equals("<clinit>")
                    || game && method.name.equals("main")
                    || graphics && (method.name.equals("glfwInit") || method.name.equals("glfwCreateWindow") || method.name.equals("createCapabilities"));
            if (!forbidden || (method.access & (Opcodes.ACC_NATIVE | Opcodes.ACC_ABSTRACT)) != 0) continue;
            var code = new InsnList();
            code.add(new TypeInsnNode(Opcodes.NEW, "java/lang/AssertionError")); code.add(new InsnNode(Opcodes.DUP));
            code.add(new LdcInsnNode("Forbidden initialization in Mixin bytecode audit: " + name + "." + method.name));
            code.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/AssertionError", "<init>", "(Ljava/lang/Object;)V", false));
            code.add(new InsnNode(Opcodes.ATHROW)); method.instructions = code; method.tryCatchBlocks.clear();
            if (method.localVariables != null) method.localVariables.clear();
            method.visibleLocalVariableAnnotations = null; method.invisibleLocalVariableAnnotations = null;
            method.maxStack = 3; changed = true;
        }
        if (!changed) return null;
        var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray();
    }
}
