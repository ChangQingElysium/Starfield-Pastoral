package com.stardew.craft.platform.fabric.network;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.impl.transformer.ClassStripper;
import net.fabricmc.loader.impl.transformer.EnvironmentStrippingData;
import net.minecraft.client.gui.screens.Screen;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

/** Real javac lambdas and Fabric Loader 0.19.5 stripping; no Minecraft initialization. */
class ClientPayloadIsolationTest {
    @Test void methodOnlyAnnotationLeavesTheSyntheticClientSignatureAndFailsServerReflection() throws Exception {
        String name = getClass().getName() + "$UnsafePayload";
        var data = stripping(bytes(name));
        assertTrue(data.getStripMethods().stream().anyMatch(method -> method.startsWith("client(")));
        assertFalse(data.getStripMethods().stream().anyMatch(method -> method.startsWith("lambda$")),
                "Fabric strips annotated members, unlike NeoForge's recursive LambdaGatherer");
        var loader = new ServerLoader();
        assertThrows(LinkageError.class, () -> Class.forName(name, false, loader).getDeclaredMethods());
        assertTrue(loader.clientRequests > 0);
    }

    @Test void nestedClientHolderKeepsTheCommonHandlerLinkableWithoutResolvingTheClientClass() throws Exception {
        String name = getClass().getName() + "$IsolatedPayload";
        assertTrue(stripping(bytes(name + "$Client")).stripEntireClass());
        var loader = new ServerLoader();
        var payload = Class.forName(name, false, loader);
        assertNotNull(payload.getDeclaredMethod("handle"));
        payload.getDeclaredMethods();
        assertEquals(0, loader.clientRequests, "Linking a common enqueue callback must not load its client implementation");
        assertThrows(ClassNotFoundException.class, () -> Class.forName(name + "$Client", false, loader));
    }

    public static final class UnsafePayload {
        public static void handle() {}
        @Environment(EnvType.CLIENT) private static void client(Screen screen) {
            Runnable callback = () -> consume(screen);
            callback.run();
        }
        @Environment(EnvType.CLIENT) private static void consume(Screen screen) {}
    }
    public static final class IsolatedPayload {
        public static void handle() {
            Runnable callback = () -> Client.client(null);
            callback.run();
        }
        @Environment(EnvType.CLIENT) private static final class Client {
            private static void client(Screen screen) {
                Runnable callback = () -> consume(screen);
                callback.run();
            }
            private static void consume(Screen screen) {}
        }
    }

    private static final class ServerLoader extends ClassLoader {
        int clientRequests;
        ServerLoader() { super(ClientPayloadIsolationTest.class.getClassLoader()); }
        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("net.minecraft.client.")) {
                clientRequests++; throw new ClassNotFoundException("Client classes are absent on SERVER: " + name);
            }
            if (!name.startsWith(ClientPayloadIsolationTest.class.getName() + "$")) return super.loadClass(name, resolve);
            var loaded = findLoadedClass(name); if (loaded != null) return loaded;
            try {
                byte[] raw = bytes(name); var data = stripping(raw);
                if (data.stripEntireClass()) throw new ClassNotFoundException("Environment.CLIENT: " + name);
                var writer = new ClassWriter(0);
                new ClassReader(raw).accept(new ClassStripper(Opcodes.ASM9, writer,
                        data.getStripInterfaces(), data.getStripFields(), data.getStripMethods()), 0);
                byte[] stripped = writer.toByteArray();
                var type = defineClass(name, stripped, 0, stripped.length); if (resolve) resolveClass(type); return type;
            } catch (IOException exception) { throw new ClassNotFoundException(name, exception); }
        }
    }
    private static EnvironmentStrippingData stripping(byte[] raw) {
        var data = new EnvironmentStrippingData(Opcodes.ASM9, "SERVER");
        new ClassReader(raw).accept(data, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES); return data;
    }
    private static byte[] bytes(String name) throws IOException {
        try (var stream = ClientPayloadIsolationTest.class.getClassLoader().getResourceAsStream(name.replace('.', '/') + ".class")) {
            assertNotNull(stream, name); return stream.readAllBytes();
        }
    }
}
