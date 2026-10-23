package com.stardew.craft.platform.fabric.validation;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.loader.impl.transformer.ClassStripper;
import net.fabricmc.loader.impl.transformer.EnvironmentStrippingData;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

/** Post-classes, no-initialization linkage check of the actual four payloads with real SERVER stripping. */
public final class PayloadClientIsolationProbe {
    private static final List<String> PAYLOADS = List.of("OpenFairFortunePayload", "OpenCalicoJackPromptPayload",
            "OpenDesertFestivalQuestionPayload", "OpenWinterStarGiftPromptPayload");
    private PayloadClientIsolationProbe() {}

    public static void main(String[] args) throws Exception {
        URL[] urls = Arrays.stream(System.getProperty("java.class.path").split(java.io.File.pathSeparator))
                .map(value -> { try { return Path.of(value).toUri().toURL(); } catch (Exception failure) { throw new IllegalArgumentException(value, failure); } })
                .toArray(URL[]::new);
        try (var loader = new ServerLoader(urls)) {
            var context = Class.forName("com.stardew.craft.platform.fabric.network.IPayloadContext", false, loader);
            for (String simple : PAYLOADS) {
                String name = "com.stardew.craft.network.payload." + simple;
                byte[] raw = loader.bytes(name);
                String pool = new String(raw, StandardCharsets.ISO_8859_1);
                if (pool.contains("net/minecraft/client/") || pool.contains("com/stardew/craft/client/"))
                    throw new AssertionError("Common payload still contains client bytecode: " + name);
                if (!stripping(loader.bytes(name + "$Client")).stripEntireClass())
                    throw new AssertionError("Client holder is not environment-isolated: " + name);
                var payload = Class.forName(name, false, loader);
                payload.getDeclaredMethods();
                MethodHandles.publicLookup().findStatic(payload, "handle", MethodType.methodType(void.class, payload, context));
                System.out.println("PASS common handler linkage + client holder stripping: " + name);
            }
            if (loader.clientRequests != 0) throw new AssertionError("Common handler resolution requested client code " + loader.clientRequests + " times");
        }
    }

    private static EnvironmentStrippingData stripping(byte[] raw) {
        var data = new EnvironmentStrippingData(Opcodes.ASM9, "SERVER");
        new ClassReader(raw).accept(data, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES); return data;
    }
    private static final class ServerLoader extends URLClassLoader {
        int clientRequests;
        ServerLoader(URL[] urls) { super(urls, ClassLoader.getPlatformClassLoader()); }
        byte[] bytes(String name) throws IOException, ClassNotFoundException {
            var resource = findResource(name.replace('.', '/') + ".class");
            if (resource == null) throw new ClassNotFoundException(name);
            try (var stream = resource.openStream()) { return stream.readAllBytes(); }
        }
        @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (name.startsWith("net.minecraft.client.") || name.startsWith("com.mojang.blaze3d.")) {
                clientRequests++; throw new ClassNotFoundException("Absent on SERVER: " + name);
            }
            try {
                byte[] raw = bytes(name); var data = stripping(raw);
                if (data.stripEntireClass()) {
                    clientRequests++; throw new ClassNotFoundException("Environment.CLIENT: " + name);
                }
                var writer = new ClassWriter(0);
                new ClassReader(raw).accept(new ClassStripper(Opcodes.ASM9, writer,
                        data.getStripInterfaces(), data.getStripFields(), data.getStripMethods()), 0);
                byte[] stripped = writer.toByteArray(); return defineClass(name, stripped, 0, stripped.length);
            } catch (IOException failure) { throw new ClassNotFoundException(name, failure); }
        }
    }
}
