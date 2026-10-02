import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;

/** Loads only rendering fixtures and their actually woven targets in a separate class loader. */
public final class RenderGeometryCheck {
    public static void main(String[] args) throws Exception {
        boolean optional = args.length > 1 && args[1].equals("--embeddium");
        ClassLoader loader = new WovenLoader(Path.of(args[0]), optional ? Path.of(args[2]) : null);
        for (int i = optional ? 3 : 1; i < args.length; i++) {
            loader.loadClass(args[i]).getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        }
    }

    private static final class WovenLoader extends ClassLoader {
        private final Path dump;
        private final Map<String, String> readable = new HashMap<>();
        private WovenLoader(Path dump, Path mcpToSrg) throws java.io.IOException {
            super(RenderGeometryCheck.class.getClassLoader());
            this.dump = dump;
            if (mcpToSrg != null) for (String line : Files.readAllLines(mcpToSrg)) {
                if (!line.startsWith("\t") || line.startsWith("\t\t")) continue;
                String[] fields = line.strip().split(" ");
                String srg = fields[fields.length - 1];
                if (srg.startsWith("m_") || srg.startsWith("f_")) {
                    String old = readable.putIfAbsent(srg, fields[0]);
                    if (old != null && !old.equals(fields[0])) throw new IllegalStateException("Ambiguous official mapping " + srg);
                }
            }
        }

        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!readable.isEmpty() && (name.equals("com.stardew.craft.mixin.SodiumRingLightQuadAccessor")
                    || name.equals("com.stardew.craft.mixin.SodiumColoredLightQuadAccessor")
                    || name.equals("com.stardew.craft.mixin.SodiumImmediateQuadNormalAccessor"))) {
                throw new ClassNotFoundException("Ordinary interface Mixins are not loadable at runtime: " + name);
            }
            boolean isolated = name.startsWith("RenderNormalsFixture") || name.startsWith("JsonBakingCheck")
                    || name.startsWith("com.mojang.blaze3d.vertex.PoseStack")
                    || name.equals("com.mojang.blaze3d.vertex.VertexConsumer")
                    || name.startsWith("net.minecraft.client.model.geom.ModelPart")
                    || name.equals("net.minecraft.client.renderer.block.model.FaceBakery")
                    || name.equals("net.minecraftforge.client.model.ElementsModel")
                    || name.startsWith("com.stardew.craft.port.PortVertex")
                    || name.equals("com.stardew.craft.port.PortNormalPose")
                    || name.startsWith("com.stardew.craft.port.PortFaceBakery")
                    || !readable.isEmpty() && (name.startsWith("EmbeddiumNormalsFixture")
                            || name.startsWith("me.jellysquid.mods.sodium.")
                            || name.startsWith("net.caffeinemc.mods.sodium.")
                            || name.startsWith("org.embeddedt.embeddium.")
                            || name.startsWith("com.mojang.blaze3d.vertex.VertexFormat")
                            || name.equals("com.mojang.blaze3d.vertex.DefaultVertexFormat")
                            || name.equals("net.minecraft.client.renderer.block.model.BakedQuad")
                            || name.equals("com.stardew.craft.client.light.EmbeddiumLightBridge")
                            || name.startsWith("com.stardew.craft.client.render.EmbeddiumNormalBridge"));
            if (!isolated) return super.loadClass(name, resolve);
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    String path = name.replace('.', '/') + ".class";
                    Path transformed = dump.resolve(path);
                    try (InputStream stream = Files.isRegularFile(transformed)
                            ? Files.newInputStream(transformed) : getParent().getResourceAsStream(path)) {
                        if (stream == null) throw new ClassNotFoundException(name);
                        byte[] bytes = stream.readAllBytes();
                        if (!readable.isEmpty()) {
                            ClassNode node = new ClassNode();
                            new ClassReader(bytes).accept(node, 0);
                            if (node.invisibleAnnotations != null && node.invisibleAnnotations.stream().anyMatch(annotation ->
                                    annotation.desc.equals("Lorg/embeddedt/embeddium/asm/OptionalInterface;"))) {
                                // Embeddium's own plugin performs this for optional Fabric APIs.
                                // Execute its real processor rather than fabricate missing interfaces.
                                try {
                                    getParent().loadClass("org.embeddedt.embeddium.asm.AnnotationProcessingEngine")
                                            .getMethod("processClass", ClassNode.class).invoke(null, node);
                                } catch (ReflectiveOperationException failure) { throw new ClassNotFoundException(name, failure); }
                                ClassWriter processed = new ClassWriter(0);
                                node.accept(processed);
                                bytes = processed.toByteArray();
                            }
                            // The transform already succeeded against untouched SRG release bytecode.
                            // Only resolve Minecraft names for execution with the mapped development jar.
                            ClassWriter writer = new ClassWriter(0);
                            new ClassReader(bytes).accept(new ClassRemapper(writer, new Remapper() {
                                @Override public String mapMethodName(String owner, String method, String descriptor) {
                                    return readable.getOrDefault(method, method);
                                }
                                @Override public String mapFieldName(String owner, String field, String descriptor) {
                                    return readable.getOrDefault(field, field);
                                }
                            }), 0);
                            bytes = writer.toByteArray();
                        }
                        loaded = defineClass(name, bytes, 0, bytes.length);
                    } catch (java.io.IOException failure) { throw new ClassNotFoundException(name, failure); }
                }
                if (resolve) resolveClass(loaded);
                return loaded;
            }
        }
    }
}
