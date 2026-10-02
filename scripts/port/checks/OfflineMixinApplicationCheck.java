import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.launch.platform.container.ContainerHandleVirtual;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.IMixinTransformerFactory;
import org.spongepowered.asm.service.IClassBytecodeProvider;
import org.spongepowered.asm.service.IClassProvider;
import org.spongepowered.asm.service.IClassTracker;
import org.spongepowered.asm.service.IGlobalPropertyService;
import org.spongepowered.asm.service.IMixinAuditTrail;
import org.spongepowered.asm.service.IMixinInternal;
import org.spongepowered.asm.service.IPropertyKey;
import org.spongepowered.asm.service.ITransformerProvider;
import org.spongepowered.asm.service.MixinServiceAbstract;

/** Real Mixin/Extras application to fresh classes and Forge's mapped dependency bytecode; no game bootstrap. */
public final class OfflineMixinApplicationCheck {
    private static final String PACKAGE = "com.stardew.craft.mixin.";
    private static final Set<String> REQUIRED_CLIENT = Set.of(
            "PortContainerEventHandlerMixin", "PortMouseHandlerScreenInputMixin", "PortMinecraftMouseFrameMixin",
            "PortParticleAlphaInvoker", "PortMapDecorSpatialLightingMixin", "PortAmbientOcclusionSampleMixin",
            "StardewGuiMouseMixin", "PortMultiPlayerGameModeWillDestroyMixin", "PortEntityRendererNameTagMixin",
            "StardewInventoryTextureMixin", "StardewInventorySlotHighlightMixin", "StardewInventoryRecipeButtonMixin");
    private static IMixinTransformerFactory factory;
    private static final Map<String, String> runtimeReadable = new HashMap<>();

    public static final class Service extends MixinServiceAbstract implements IClassProvider, IClassBytecodeProvider {
        @Override public String getName() { return "OfflineMixinApplicationCheck"; }
        @Override public boolean isValid() { return true; }
        @Override public IClassProvider getClassProvider() { return this; }
        @Override public IClassBytecodeProvider getBytecodeProvider() { return this; }
        @Override public ITransformerProvider getTransformerProvider() { return null; }
        @Override public IClassTracker getClassTracker() { return null; }
        @Override public IMixinAuditTrail getAuditTrail() { return null; }
        @Override public Collection<String> getPlatformAgents() { return List.of(Agent.class.getName()); }
        @Override public IContainerHandle getPrimaryContainer() { return new ContainerHandleVirtual(getName()); }
        @Override public InputStream getResourceAsStream(String name) { return OfflineMixinApplicationCheck.class.getClassLoader().getResourceAsStream(name); }
        @Override public MixinEnvironment.CompatibilityLevel getMaxCompatibilityLevel() { return MixinEnvironment.CompatibilityLevel.JAVA_17; }
        @Override public void offer(IMixinInternal internal) {
            if (internal instanceof IMixinTransformerFactory offered) factory = offered;
            super.offer(internal);
        }
        @Override public URL[] getClassPath() { return new URL[0]; }
        @Override public Class<?> findClass(String name) throws ClassNotFoundException { return findClass(name, true); }
        @Override public Class<?> findClass(String name, boolean initialize) throws ClassNotFoundException {
            return Class.forName(name, initialize, OfflineMixinApplicationCheck.class.getClassLoader());
        }
        @Override public Class<?> findAgentClass(String name, boolean initialize) throws ClassNotFoundException { return findClass(name, initialize); }
        @Override public ClassNode getClassNode(String name) throws ClassNotFoundException, IOException { return getClassNode(name, true); }
        @Override public ClassNode getClassNode(String name, boolean runTransformers) throws ClassNotFoundException, IOException {
            byte[] bytes = bytes(name.replace('.', '/'));
            if (bytes == null) throw new ClassNotFoundException(name);
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, ClassReader.EXPAND_FRAMES);
            return node;
        }
    }

    public static final class Agent extends org.spongepowered.asm.launch.platform.MixinPlatformAgentAbstract
            implements org.spongepowered.asm.launch.platform.IMixinPlatformServiceAgent {
        @Override public void init() {}
        @Override public String getSideName() { return System.getProperty("port.mixin.side", "CLIENT"); }
        @Override public Collection<IContainerHandle> getMixinContainers() { return List.of(); }
    }

    public static final class Props implements IGlobalPropertyService {
        private record Key(String name) implements IPropertyKey {}
        private final Map<String, Object> values = new HashMap<>();
        @Override public IPropertyKey resolveKey(String name) { return new Key(name); }
        @SuppressWarnings("unchecked") @Override public <T> T getProperty(IPropertyKey key) { return (T) values.get(((Key) key).name()); }
        @Override public void setProperty(IPropertyKey key, Object value) { values.put(((Key) key).name(), value); }
        @SuppressWarnings("unchecked") @Override public <T> T getProperty(IPropertyKey key, T fallback) { return (T) values.getOrDefault(((Key) key).name(), fallback); }
        @Override public String getPropertyString(IPropertyKey key, String fallback) {
            Object value = values.get(((Key) key).name());
            return value == null ? fallback : value.toString();
        }
    }

    private static byte[] bytes(String internal) throws IOException {
        try (InputStream input = OfflineMixinApplicationCheck.class.getClassLoader().getResourceAsStream(internal + ".class")) {
            if (input == null) return null;
            byte[] raw = input.readAllBytes();
            if (runtimeReadable.isEmpty()) return raw;
            ClassWriter writer = new ClassWriter(0);
            new ClassReader(raw).accept(new ClassRemapper(writer, new Remapper() {
                @Override public String mapMethodName(String owner, String method, String descriptor) { return runtimeReadable.getOrDefault(method, method); }
                @Override public String mapFieldName(String owner, String field, String descriptor) { return runtimeReadable.getOrDefault(field, field); }
            }), 0);
            return writer.toByteArray();
        }
    }

    private static List<String> targets(String name) throws IOException {
        return targetsOfClass(PACKAGE + name);
    }

    private static List<String> targetsOfClass(String name) throws IOException {
        byte[] bytes = bytes(name.replace('.', '/'));
        if (bytes == null) throw new IllegalStateException("Fresh production class missing: " + name);
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, ClassReader.SKIP_CODE);
        List<AnnotationNode> annotations = new ArrayList<>();
        if (node.visibleAnnotations != null) annotations.addAll(node.visibleAnnotations);
        if (node.invisibleAnnotations != null) annotations.addAll(node.invisibleAnnotations);
        List<String> targets = new ArrayList<>();
        for (AnnotationNode annotation : annotations) {
            if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;") || annotation.values == null) continue;
            for (int index = 0; index < annotation.values.size(); index += 2) {
                String key = (String) annotation.values.get(index);
                Object value = annotation.values.get(index + 1);
                if (key.equals("value")) for (Object type : (List<?>) value) targets.add(((Type) type).getInternalName());
                if (key.equals("targets")) for (Object target : (List<?>) value) targets.add(((String) target).replace('.', '/'));
            }
        }
        if (targets.isEmpty()) throw new IllegalStateException("No @Mixin targets: " + name);
        return targets;
    }

    private static boolean nativeTarget(String name) {
        return name.startsWith("net/minecraft/") || name.startsWith("net/minecraftforge/") || name.startsWith("com/mojang/");
    }

    private static boolean pinnedOptional(String name) {
        return name.startsWith("Sodium") || name.startsWith("TownDoorSodium") || name.startsWith("TownDoorIris")
                || name.startsWith("TownDoorOculus") || name.startsWith("IrisMineLamp") || name.startsWith("Xaero")
                || name.startsWith("PortJade") || name.equals("CtmModelInitializationMixin") || name.equals("Ae2FacadeItemMixin");
    }

    private static Set<String> mergedMixins(byte[] transformed) {
        ClassNode node = new ClassNode();
        new ClassReader(transformed).accept(node, ClassReader.SKIP_CODE);
        Set<String> merged = new LinkedHashSet<>();
        for (MethodNode method : node.methods) {
            if (method.visibleAnnotations == null) continue;
            for (AnnotationNode annotation : method.visibleAnnotations) {
                if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;")) continue;
                for (int index = 0; index < annotation.values.size(); index += 2) {
                    if (annotation.values.get(index).equals("mixin")) merged.add((String) annotation.values.get(index + 1));
                }
            }
        }
        return merged;
    }

    public static void main(String[] args) throws Throwable {
        Path configPath = Path.of(args[0]);
        Path outputDirectory = Path.of(args[1]);
        Path productionClasses = Path.of(args[2]);
        boolean optionalMode = args.length > 3 && args[3].equals("optional");
        boolean embeddiumNormals = args.length > 3 && args[3].equals("embeddium-runtime");
        if (embeddiumNormals) for (String line : Files.readAllLines(Path.of("build/createMcpToSrg/output.tsrg"))) {
            if (!line.startsWith("\t") || line.startsWith("\t\t")) continue;
            String[] fields = line.strip().split(" ");
            String srg = fields[fields.length - 1];
            if (srg.startsWith("m_") || srg.startsWith("f_")) {
                String old = runtimeReadable.putIfAbsent(srg, fields[0]);
                if (old != null && !old.equals(fields[0])) throw new IllegalStateException("Ambiguous official mapping " + srg);
            }
        }
        Set<String> runtimeMixins = Set.of("PortPoseNormalTrustMixin", "PortPoseStackNormalsMixin",
                "SodiumImmediateQuadNormalAccessor", "SodiumImmediateNormalsMixin", "SodiumEntityNormalsMixin",
                "SodiumRingLightQuadAccessor", "SodiumColoredLightQuadAccessor", "SodiumRingLightPipelineMixin",
                "SodiumRingLightDataAccessor", "SodiumColoredLightPositionAccessor");
        boolean client = System.getProperty("port.mixin.side", "CLIENT").equals("CLIENT");
        JsonObject config = JsonParser.parseString(Files.readString(configPath)).getAsJsonObject();
        Map<String, List<String>> byTarget = new TreeMap<>();
        Set<String> included = new LinkedHashSet<>();
        Set<String> optional = new LinkedHashSet<>();
        for (String group : List.of("mixins", "client", "server")) {
            JsonArray filtered = new JsonArray();
            if (config.has(group) && (!group.equals("client") || client) && (!group.equals("server") || !client)) {
                for (var value : config.getAsJsonArray(group)) {
                    String name = value.getAsString();
                    if (embeddiumNormals ? !runtimeMixins.contains(name) : optionalMode && !pinnedOptional(name)) continue;
                    String relative = (PACKAGE + name).replace('.', '/');
                    Path source = Path.of("src/main/java", relative + ".java");
                    if (!Files.isRegularFile(source)) source = Path.of("src/port/java", relative + ".java");
                    Path compiled = productionClasses.resolve(relative + ".class");
                    if (!Files.isRegularFile(compiled) || (Files.isRegularFile(source)
                            && Files.getLastModifiedTime(source).compareTo(Files.getLastModifiedTime(compiled)) > 0)) {
                        throw new IllegalStateException("Run classes first: missing/stale production Mixin " + name);
                    }
                    List<String> targets = targets(name);
                    if (optionalMode || embeddiumNormals || targets.stream().allMatch(OfflineMixinApplicationCheck::nativeTarget)) {
                        filtered.add(name);
                        included.add(name);
                        for (String target : targets) byTarget.computeIfAbsent(target, key -> new ArrayList<>()).add(name);
                    } else optional.add(name);
                }
            }
            config.add(group, filtered);
        }
        if (optionalMode && (!client || !included.contains("SodiumBlockEntityBoundsMixin"))) {
            throw new IllegalStateException("Optional client hooks not ready/registered (including SodiumBlockEntityBoundsMixin)");
        }
        if (embeddiumNormals && !included.containsAll(runtimeMixins)) throw new IllegalStateException("Missing registered runtime normal hooks");
        if (client && !optionalMode && !embeddiumNormals && !included.containsAll(REQUIRED_CLIENT)) {
            Set<String> missing = new LinkedHashSet<>(REQUIRED_CLIENT);
            missing.removeAll(included);
            throw new IllegalStateException("Required current client hooks not registered: " + missing);
        }
        // Gate/version policy is checked separately; do not bootstrap Forge just to select an offline test set.
        config.remove("plugin");
        if (optionalMode) {
            String refmap = config.get("refmap").getAsString();
            Path generated = Path.of("build/tmp/compileJava", refmap);
            for (String name : included) {
                Path source = Path.of("src/main/java", (PACKAGE + name).replace('.', '/') + ".java");
                if (Files.isRegularFile(source)
                        && Files.getLastModifiedTime(source).compareTo(Files.getLastModifiedTime(generated)) > 0) {
                    throw new IllegalStateException("Run classes first: stale generated refmap for " + name);
                }
            }
            Files.copy(generated, outputDirectory.resolve(refmap));
        } else config.remove("refmap");
        String configName = "port-offline-application.mixins.json";
        Files.writeString(outputDirectory.resolve(configName), config.toString());
        MixinBootstrap.init();
        Mixins.addConfiguration(configName);
        if (embeddiumNormals) {
            // The locked renderer's real pose-cache overwrite and vertex-format accessor are
            // necessary for executing its immediate writers; do not replace these APIs with mocks.
            JsonObject rendererConfig = new JsonObject();
            rendererConfig.addProperty("package", "me.jellysquid.mods.sodium.mixin");
            rendererConfig.addProperty("required", true);
            rendererConfig.addProperty("minVersion", "0.8");
            rendererConfig.addProperty("compatibilityLevel", "JAVA_17");
            JsonArray rendererMixins = new JsonArray();
            for (String rendererMixin : List.of("core.render.MatrixStackMixin", "core.render.VertexFormatAccessor", "core.model.quad.BakedQuadMixin")) {
                rendererMixins.add(rendererMixin);
                String binary = "me.jellysquid.mods.sodium.mixin." + rendererMixin;
                for (String target : targetsOfClass(binary))
                    byTarget.computeIfAbsent(target, key -> new ArrayList<>()).add(binary);
            }
            rendererConfig.add("client", rendererMixins);
            String rendererName = "port-offline-embeddium-runtime.mixins.json";
            Files.writeString(outputDirectory.resolve(rendererName), rendererConfig.toString());
            Mixins.addConfiguration(rendererName);
        }
        if (optionalMode) MixinEnvironment.getDefaultEnvironment().setObfuscationContext("searge");
        if (factory == null) throw new IllegalStateException("No real Mixin transformer factory");
        IMixinTransformer transformer = factory.createTransformer();
        com.llamalad7.mixinextras.MixinExtrasBootstrap.init();
        Method gotoPhase = MixinEnvironment.class.getDeclaredMethod("gotoPhase", MixinEnvironment.Phase.class);
        gotoPhase.setAccessible(true);
        gotoPhase.invoke(null, MixinEnvironment.Phase.DEFAULT);
        int transformed = 0;
        int failed = 0;
        Set<String> merged = new LinkedHashSet<>();
        String dumpDirectory = System.getenv("PORT_MIXIN_DUMP_DIR");
        Path dump = dumpDirectory == null || dumpDirectory.isBlank() ? null : Path.of(dumpDirectory).toAbsolutePath().normalize();
        if (dump != null && !dump.startsWith(Path.of("build").toAbsolutePath().normalize()))
            throw new IllegalArgumentException("Mixin runtime fixtures may only dump into build");
        for (var entry : byTarget.entrySet()) {
            String target = entry.getKey();
            try {
                byte[] input = bytes(target);
                if (input == null) throw new IllegalStateException("Missing native target " + target);
                byte[] output = transformer.transformClassBytes(target.replace('/', '.'), target.replace('/', '.'), input);
                if (Arrays.equals(input, output)) throw new IllegalStateException("Registered native target was unchanged");
                merged.addAll(mergedMixins(output));
                if (dump != null) {
                    Path file = dump.resolve(target + ".class");
                    Files.createDirectories(file.getParent());
                    Files.write(file, output);
                }
                transformed++;
                System.out.println("APPLIED " + target + " <- " + entry.getValue());
            } catch (Throwable failure) {
                failed++;
                System.out.println("FAIL " + target + " <- " + entry.getValue());
                failure.printStackTrace(System.out);
            }
        }
        Set<String> requiredHandlers = optionalMode || embeddiumNormals ? included : REQUIRED_CLIENT;
        if (client) for (String required : requiredHandlers) {
            if (!merged.contains(PACKAGE + required)) {
                failed++;
                System.out.println("FAIL no merged handler/invoker from required hook " + required);
            }
        }
        System.out.println("Mixin " + MixinEnvironment.getCurrentEnvironment().getVersion()
                + " / Extras 0.4.1 " + (optionalMode ? "optional SRG-refmap" : embeddiumNormals ? "Embeddium official-name runtime" : "mapped") + " application: side=" + (client ? "CLIENT" : "SERVER")
                + " registered=" + included.size() + " targets=" + byTarget.size()
                + " transformed=" + transformed + " failed=" + failed + "; excluded optional=" + optional);
        if (failed != 0) throw new AssertionError("Offline Mixin application failed: " + failed);
    }
}
