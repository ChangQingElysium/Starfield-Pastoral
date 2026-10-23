package com.stardew.craft.platform.fabric.validation;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

/**
 * Standalone development tool, never a mod entrypoint. Arguments: runtime-classpath file,
 * processed main resource directory, report JSON. The parent starts a guarded child process.
 * Knot setup is real; its preLaunch invocation is strictly removed in memory. Targets are only
 * transformed to bytes through KnotClassDelegate, never defined or initialized by this tool.
 */
public final class ClientMixinTransformProbe {
    private static final String KNOT = "net.fabricmc.loader.impl.launch.knot.Knot";
    private static final String[] CONFIGS = { "stardewcraft.mixins.json", "stardewcraft.fabric.mixins.json" };
    private ClientMixinTransformProbe() {}

    public static void main(String[] args) throws Exception {
        if (args.length == 4 && args[0].equals("--guarded")) { runGuarded(Path.of(args[1]), Path.of(args[2]), Path.of(args[3])); return; }
        if (args.length != 3) throw new IllegalArgumentException("runtime-classpath.txt processed-resources report.json");
        Path report = Path.of(args[2]).toAbsolutePath(); Files.createDirectories(report.getParent());
        Files.deleteIfExists(report); // A failed child must never leave an earlier successful audit visible.
        Path temp = Files.createTempDirectory(report.getParent(), "client-mixin-probe-");
        int exit;
        try {
            List<Path> entries = classpath(Path.of(args[0]));
            entries.add(Path.of(ClientMixinTransformProbe.class.getProtectionDomain().getCodeSource().getLocation().toURI()));
            Path cpFile = temp.resolve("classpath.txt"); Files.write(cpFile, entries.stream().map(Path::toString).distinct().toList());
            Path agent = temp.resolve("guard.jar"); var manifest = new Manifest();
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            manifest.getMainAttributes().putValue("Premain-Class", MixinAuditGuard.class.getName());
            String resource = MixinAuditGuard.class.getName().replace('.', '/') + ".class";
            try (var jar = new JarOutputStream(Files.newOutputStream(agent), manifest);
                    var source = ClientMixinTransformProbe.class.getClassLoader().getResourceAsStream(resource)) {
                if (source == null) throw new IllegalStateException("Missing audit agent class");
                jar.putNextEntry(new JarEntry(resource)); source.transferTo(jar); jar.closeEntry();
            }
            String cp = String.join(File.pathSeparator, entries.stream().map(Path::toString).distinct().toList());
            var command = List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-javaagent:" + agent,
                    "-Djava.awt.headless=true", "-Dfabric.noGui=true", "-Dfabric.development=true", "-Dfabric.gameMappingNamespace=named",
                    "-Dfabric.runtimeMappingNamespace=named", "-Dfabric.defaultModDistributionNamespace=intermediary", "-cp", cp,
                    ClientMixinTransformProbe.class.getName(), "--guarded", cpFile.toString(), Path.of(args[1]).toAbsolutePath().toString(), report.toString());
            Process child = new ProcessBuilder(command).directory(temp.toFile()).redirectErrorStream(true)
                    .redirectOutput(report.resolveSibling("client-transform.log").toFile()).start();
            Thread cleanup = new Thread(() -> { if (child.isAlive()) child.destroyForcibly(); }, "stardew-mixin-audit-cleanup");
            Runtime.getRuntime().addShutdownHook(cleanup);
            try {
                if (child.waitFor(180, java.util.concurrent.TimeUnit.SECONDS)) exit = child.exitValue();
                else { child.destroyForcibly(); child.waitFor(10, java.util.concurrent.TimeUnit.SECONDS); exit = 124; }
            } finally {
                if (child.isAlive()) child.destroyForcibly();
                Runtime.getRuntime().removeShutdownHook(cleanup);
            }
            if (!Files.exists(report)) Files.writeString(report, new GsonBuilder().setPrettyPrinting().create().toJson(
                    Map.of("mode", "CLIENT post-Mixin bytes only", "setup_failure", "Audit child exited before producing a report", "exit_code", exit)));
            var outcome = JsonParser.parseString(Files.readString(report)).getAsJsonObject(); outcome.addProperty("audit_process_exit_code", exit);
            if (exit != 0) outcome.addProperty("process_failure", exit == 124 ? "Audit child exceeded the 180 second limit" : "Audit child failed; inspect setup_failure, targets and client-transform.log");
            Files.writeString(report, new GsonBuilder().setPrettyPrinting().create().toJson(outcome));
            System.out.println("Client bytecode audit exit=" + exit + "; report=" + report);
        } finally {
            try (var paths = Files.walk(temp)) { for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path); }
        }
        if (exit != 0) System.exit(exit);
    }

    private static List<Path> classpath(Path file) throws Exception {
        List<Path> entries = new ArrayList<>();
        for (String line : Files.readAllLines(file)) for (String value : line.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            if (!value.isBlank()) { Path entry = Path.of(value).toAbsolutePath().normalize(); if (Files.exists(entry)) entries.add(entry); }
        }
        return entries;
    }

    private static void runGuarded(Path cpFile, Path resources, Path reportFile) throws Exception {
        if (!"active".equals(System.getProperty("stardew.mixinAudit.guard"))) throw new IllegalStateException("Initialization guard agent is mandatory");
        List<Path> cp = classpath(cpFile); System.setProperty("java.class.path", String.join(File.pathSeparator, cp.stream().map(Path::toString).toList()));
        Map<String, Object> report = new LinkedHashMap<>(); List<Object> outcomes = new ArrayList<>(); report.put("targets", outcomes);
        report.put("started_utc", java.time.Instant.now().toString());
        report.put("mode", "CLIENT post-Mixin bytes only; preLaunch removed; game/graphics initializers forbidden");
        Map<String, Object> configurations = new LinkedHashMap<>();
        for (String config : CONFIGS) {
            byte[] bytes = Files.readAllBytes(resources.resolve(config)); var json = JsonParser.parseString(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            configurations.put(config, Map.of("sha256", java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)),
                    "common", json.has("mixins") ? json.getAsJsonArray("mixins").size() : 0, "client", json.has("client") ? json.getAsJsonArray("client").size() : 0));
        }
        report.put("configurations", configurations);
        int failed = 0;
        try (var isolated = new Loader(cp.stream().map(path -> { try { return path.toUri().toURL(); } catch (Exception failure) { throw new IllegalArgumentException(failure); } }).toArray(URL[]::new))) {
            Class<?> knotType = Class.forName(KNOT, true, isolated);
            Class<?> side = Class.forName("net.fabricmc.api.EnvType", true, isolated);
            @SuppressWarnings({"unchecked", "rawtypes"}) Object client = Enum.valueOf((Class) side, "CLIENT");
            Object knot = knotType.getConstructor(side).newInstance(client);
            Path game = Path.of("game").toAbsolutePath(); Files.createDirectories(game);
            knotType.getMethod("init", String[].class).invoke(knot, (Object) new String[] { "--gameDir", game.toString() });
            if (isolated.removedPreLaunch != 1) throw new IllegalStateException("Knot preLaunch removal was not exactly one");
            report.put("removed_preLaunch_calls", isolated.removedPreLaunch);
            var delegateField = knotType.getDeclaredField("classLoader"); delegateField.setAccessible(true); Object delegate = delegateField.get(knot);
            var transform = delegate.getClass().getDeclaredMethod("getPostMixinClassByteArray", String.class, boolean.class); transform.setAccessible(true);
            Map<String, Set<String>> targets = targets(resources, isolated); report.put("configured_targets", targets.size());
            for (var entry : targets.entrySet()) {
                Map<String, Object> result = new LinkedHashMap<>(); result.put("target", entry.getKey()); result.put("configured_mixins", entry.getValue()); outcomes.add(result);
                if (isolated.getResource(entry.getKey().replace('.', '/') + ".class") == null) { result.put("status", "optional-target-absent"); continue; }
                try {
                    byte[] transformed = (byte[]) transform.invoke(delegate, entry.getKey(), true);
                    if (transformed == null) throw new IllegalStateException("Transformer returned no bytes");
                    var node = new ClassNode(); new ClassReader(transformed).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    if (entry.getKey().equals("net.minecraft.client.Minecraft")) {
                        report.put("client_bootstrap_order", bootstrapOrder(node));
                    }
                    Set<String> merged = new LinkedHashSet<>();
                    for (var method : node.methods) for (var annotation : annotations(method.visibleAnnotations, method.invisibleAnnotations))
                        if (annotation.desc.endsWith("/MixinMerged;")) { Object name = value(annotation, "mixin"); if (name instanceof String text) merged.add(text); }
                    merged.retainAll(entry.getValue()); result.put("status", "transformed"); result.put("bytes", transformed.length); result.put("merged_owned_mixins", merged);
                } catch (Throwable failure) { failed++; result.put("status", "failed"); result.put("failure", trace(failure)); }
            }
            ClassLoader targetLoader = (ClassLoader) knotType.getMethod("getTargetClassLoader").invoke(knot);
            var index = JsonParser.parseString(Files.readString(resources.resolve("META-INF/stardewcraft/fabric-subscribers.json"))).getAsJsonObject();
            Set<String> subscribers = new LinkedHashSet<>();
            for (var item : index.getAsJsonArray("classes")) {
                var entry = item.getAsJsonObject();
                if (entry.getAsJsonArray("environments").asList().stream().anyMatch(sideName -> sideName.getAsString().equals("CLIENT")))
                    subscribers.add(entry.get("className").getAsString());
            }
            if (subscribers.isEmpty()) throw new IllegalStateException("Physical-client subscriber index is empty");
            List<Object> linkageFailures = new ArrayList<>();
            for (String name : subscribers) {
                try {
                    Class<?> type = Class.forName(name, false, targetLoader);
                    for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) current.getDeclaredMethods();
                } catch (Throwable failure) { linkageFailures.add(Map.of("class", name, "failure", trace(failure))); }
            }
            report.put("physical_client_subscriber_linkage", Map.of("classes", subscribers.size(), "failed", linkageFailures,
                    "mode", "Class definition and method signature resolution only; no subscriber invocation or initialization"));
            failed += linkageFailures.size();
            report.put("failed_targets", failed);
        } catch (Throwable failure) { failed++; report.put("setup_failure", trace(failure)); report.put("failed_targets", failed); }
        report.put("finished_utc", java.time.Instant.now().toString());
        Files.writeString(reportFile, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        System.out.println("Failed targets=" + failed); if (failed != 0) System.exit(1);
    }

    private static Map<String, Set<String>> targets(Path resources, ClassLoader loader) throws Exception {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (String config : CONFIGS) {
            var json = JsonParser.parseString(Files.readString(resources.resolve(config))).getAsJsonObject(); String prefix = json.get("package").getAsString();
            for (String side : List.of("mixins", "client")) if (json.has(side)) for (var item : json.getAsJsonArray(side)) {
                String mixin = prefix + "." + item.getAsString(); String resource = mixin.replace('.', '/') + ".class";
                try (var bytes = loader.getResourceAsStream(resource)) {
                    if (bytes == null) throw new IllegalStateException("Configured Mixin class is absent: " + mixin);
                    var node = new ClassNode(); new ClassReader(bytes).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
                    for (var annotation : annotations(node.visibleAnnotations, node.invisibleAnnotations)) if (annotation.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")) {
                        Object classes = value(annotation, "value"); if (classes instanceof List<?> list) for (Object type : list) result.computeIfAbsent(((Type) type).getClassName(), ignored -> new LinkedHashSet<>()).add(mixin);
                        Object names = value(annotation, "targets"); if (names instanceof List<?> list) for (Object name : list) result.computeIfAbsent(name.toString(), ignored -> new LinkedHashSet<>()).add(mixin);
                    }
                }
            }
        }
        return result;
    }

    /** Inspects Loader-patched and Mixin-transformed bytes, without defining Minecraft. */
    private static Map<String, Object> bootstrapOrder(ClassNode node) {
        var constructor = node.methods.stream().filter(method -> method.name.equals("<init>"))
                .reduce((first, second) -> { throw new IllegalStateException("Unexpected multiple Minecraft constructors"); })
                .orElseThrow();
        Map<String, Integer> points = new LinkedHashMap<>();
        Map<String, Integer> fields = new LinkedHashMap<>();
        Set<String> extensionCallbacks = new LinkedHashSet<>();
        for (var method : node.methods) for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals("com/stardew/craft/platform/fabric/client/extension/FabricClientExtensions")
                    && call.name.equals("initialize")) extensionCallbacks.add(method.name);
        }
        for (int index = 0; index < constructor.instructions.size(); index++) {
            if (constructor.instructions.get(index) instanceof FieldInsnNode field && field.owner.equals(node.name)
                    && (field.getOpcode() == Opcodes.PUTFIELD || field.getOpcode() == Opcodes.PUTSTATIC)) fields.put(field.name, index);
            if (!(constructor.instructions.get(index) instanceof MethodInsnNode call)) continue;
            String key = null;
            if (call.owner.equals("net/fabricmc/loader/impl/game/minecraft/Hooks") && call.name.equals("startClient")) key = "loader_client_entrypoints";
            if (call.name.equals("<init>")) key = switch (call.owner) {
                case "net/minecraft/client/Options" -> "options";
                case "net/minecraft/client/renderer/RenderBuffers" -> "render_buffers";
                case "net/minecraft/client/gui/Gui" -> "gui";
                default -> key;
            };
            if (call.owner.equals("net/minecraft/server/packs/resources/ReloadableResourceManager") && call.name.equals("createReload")) key = "initial_resource_reload";
            if (call.owner.equals(node.name) && extensionCallbacks.contains(call.name)) key = "late_extension_initialization";
            if (key != null && points.putIfAbsent(key, index) != null) throw new IllegalStateException("Repeated client bootstrap point: " + key);
        }
        int initialize = java.util.Objects.requireNonNull(points.get("loader_client_entrypoints"), "Missing real Fabric client entrypoint hook");
        for (String key : List.of("options", "render_buffers", "gui", "initial_resource_reload")) {
            Integer point = points.get(key);
            if (point == null || point <= initialize) throw new IllegalStateException("Client entrypoints must precede " + key + ": " + points);
        }
        for (String key : List.of("instance", "user")) {
            Integer point = fields.get(key);
            if (point == null || point >= initialize) throw new IllegalStateException("Early client setup requires initialized " + key + ": " + fields);
        }
        int extensions = java.util.Objects.requireNonNull(points.get("late_extension_initialization"), "Missing applied extension initialization callback");
        for (String key : List.of("entityModels", "blockEntityRenderDispatcher", "itemRenderer", "particleEngine")) {
            Integer point = fields.get(key);
            if (point == null || point >= extensions) throw new IllegalStateException("Late client extensions require initialized " + key + ": " + fields);
        }
        if (extensions >= points.get("initial_resource_reload")) throw new IllegalStateException("Extensions initialized after resource reload");
        return Map.of("instruction_indices", points, "entrypoints_precede_all_consumers", true,
                "early_setup_user_ready", true, "late_extensions_after_renderer_fields_before_reload", true,
                "source", "Real Knot game patch and applied Mixins; no class definition or initialization");
    }

    @SafeVarargs private static List<AnnotationNode> annotations(List<AnnotationNode>... groups) {
        List<AnnotationNode> result = new ArrayList<>(); for (var group : groups) if (group != null) result.addAll(group); return result;
    }
    private static Object value(AnnotationNode annotation, String name) {
        if (annotation.values != null) for (int i = 0; i < annotation.values.size(); i += 2) if (annotation.values.get(i).equals(name)) return annotation.values.get(i + 1);
        return null;
    }
    private static String trace(Throwable failure) {
        while (failure instanceof java.lang.reflect.InvocationTargetException && failure.getCause() != null) failure = failure.getCause();
        var text = new StringWriter(); failure.printStackTrace(new PrintWriter(text)); return text.toString();
    }

    /** Separate Loader/Mixin state from this tool. Only the pinned Knot preLaunch call is removed. */
    static final class Loader extends URLClassLoader {
        int removedPreLaunch;
        Loader(URL[] urls) { super(urls, ClassLoader.getPlatformClassLoader()); }
        @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (!name.equals(KNOT)) return super.findClass(name);
            try (var bytes = getResourceAsStream(name.replace('.', '/') + ".class")) {
                if (bytes == null) throw new ClassNotFoundException(name);
                var node = new ClassNode(); new ClassReader(bytes).accept(node, 0);
                for (var method : node.methods) if (method.name.equals("init") && method.desc.equals("([Ljava/lang/String;)Ljava/lang/ClassLoader;")) {
                    for (var instruction : method.instructions.toArray()) if (instruction instanceof MethodInsnNode call
                            && call.owner.equals("net/fabricmc/loader/impl/FabricLoaderImpl") && call.name.equals("invokeEntrypoints")
                            && call.desc.equals("(Ljava/lang/String;Ljava/lang/Class;Ljava/util/function/Consumer;)V")) {
                        for (int i = 0; i < 4; i++) method.instructions.insertBefore(call, new InsnNode(Opcodes.POP)); method.instructions.remove(call); removedPreLaunch++;
                    }
                }
                if (removedPreLaunch != 1) throw new IllegalStateException("Pinned Knot preLaunch bytecode changed; refusing unsafe setup");
                var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); byte[] transformed = writer.toByteArray();
                URL resource = findResource(name.replace('.', '/') + ".class");
                if (resource == null || !(resource.openConnection() instanceof java.net.JarURLConnection jar)) throw new IllegalStateException("Pinned Knot must come from its loader jar");
                try (var originalBytes = jar.getInputStream()) { originalBytes.readAllBytes(); }
                var origin = new java.security.CodeSource(jar.getJarFileURL(), jar.getCertificates());
                return defineClass(name, transformed, 0, transformed.length, origin);
            } catch (Exception failure) { throw new ClassNotFoundException(name, failure); }
        }
    }
}
