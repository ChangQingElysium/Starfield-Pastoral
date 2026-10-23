package com.stardew.craft.platform.fabric.validation;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

class MixinProbeSafetyTest {
    @Test
    void guardAllowsDefinitionButRejectsClassInitializationBeforeAnyGameSideEffect() throws Exception {
        String internal = "net/minecraft/audit/InitializationFixture"; String name = internal.replace('/', '.');
        String key = "stardew.audit.probe.mustStayUnset"; System.clearProperty(key);
        var writer = new ClassWriter(0); writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, internal, null, "java/lang/Object", null);
        var init = writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null); init.visitCode();
        init.visitLdcInsn(key); init.visitLdcInsn("forbidden side effect");
        init.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "setProperty", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
        init.visitInsn(Opcodes.POP); init.visitInsn(Opcodes.RETURN); init.visitMaxs(2, 0); init.visitEnd(); writer.visitEnd();
        byte[] guarded = MixinAuditGuard.protect(internal, writer.toByteArray()); assertNotNull(guarded);
        var loader = new ClassLoader(null) { @Override protected Class<?> findClass(String value) throws ClassNotFoundException {
            if (value.equals(name)) return defineClass(value, guarded, 0, guarded.length); throw new ClassNotFoundException(value);
        } };
        assertEquals(name, Class.forName(name, false, loader).getName()); assertNull(System.getProperty(key));
        var failure = assertThrows(AssertionError.class, () -> Class.forName(name, true, loader));
        assertTrue(failure.getMessage().contains("Forbidden initialization")); assertNull(System.getProperty(key));
    }
    @Test
    void pinnedKnotDefinitionStripsExactlyOnePreLaunchCallWithoutInitializingKnot() throws Exception {
        var urls = Arrays.stream(System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(java.io.File.pathSeparator)))
                .map(value -> { try { return java.nio.file.Path.of(value).toUri().toURL(); } catch (Exception failure) { throw new IllegalArgumentException(failure); } }).toArray(java.net.URL[]::new);
        try (var loader = new ClientMixinTransformProbe.Loader(urls)) {
            assertEquals("net.fabricmc.loader.impl.launch.knot.Knot", Class.forName("net.fabricmc.loader.impl.launch.knot.Knot", false, loader).getName());
            assertEquals(1, loader.removedPreLaunch);
        }
    }
}
