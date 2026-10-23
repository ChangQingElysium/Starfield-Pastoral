package com.stardew.craft.platform.fabric.validation;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Development-only premain agent for the actual Loom JavaExec command. It never
 * invokes DLI, defines a game class, or initializes graphics. Only the requested
 * launch checks and resource URLs are reported; JVM/program arguments are not.
 */
public final class ClientLaunchCommandProbe {
    private static final String REPORT_PROPERTY = "stardewcraft.fabric.launchProbeReport";
    private static final String SNAPSHOT_PATH = "/.gradle/client-runtimes/";
    private static final List<String> RESOURCES = List.of(
            "com/stardew/craft/fabric/StardewCraftFabric.class",
            "com/stardew/craft/platform/fabric/network/FabricNetwork.class",
            "fabric.mod.json");

    private ClientLaunchCommandProbe() {}

    public static void premain(String ignoredAgentArguments, Instrumentation instrumentation) {
        Map<String, Object> report = new LinkedHashMap<>();
        List<String> failures = new ArrayList<>();
        report.put("schemaVersion", 1);
        report.put("mode", "Actual JVM premain command probe; exits before DLI main");
        report.put("failures", failures);
        report.put("clientLaunched", false);
        Path destination = null;
        int exitCode = 1;
        try {
            String configured = System.getProperty(REPORT_PROPERTY);
            if (configured == null || configured.isBlank()) {
                throw new IllegalStateException("An absolute launch probe report path is required");
            }
            Path requested = Path.of(configured);
            if (!requested.isAbsolute()) {
                throw new IllegalStateException("Launch probe report path must be absolute");
            }
            destination = requested.normalize();

            List<String> input = ManagementFactory.getRuntimeMXBean().getInputArguments();
            boolean macOS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
            boolean firstThread = input.contains("-XstartOnFirstThread");
            // OpenJDK 21 macOS libjli consumes this launcher flag and sets a PID-scoped
            // marker; LWJGL 3.3.3 EventLoop.isJavaStartedOnFirstThread reads the same marker.
            boolean launcherFirstThread = macOS && "1".equals(System.getenv(
                    "JAVA_STARTED_ON_FIRST_THREAD_" + ProcessHandle.current().pid()));
            boolean firstThreadVerified = firstThread || launcherFirstThread;
            boolean minimumHeap = heapMatches(input, "-Xms", 2L << 30);
            boolean maximumHeap = heapMatches(input, "-Xmx", 8L << 30);
            List<String> debugger = input.stream().filter(argument ->
                    argument.startsWith("-agentlib:jdwp=") || argument.startsWith("-Xrunjdwp:")).toList();
            boolean localDebugger = debugger.size() == 1 && localhostDebugger(debugger.getFirst());
            report.put("macOS", macOS);
            report.put("macFirstThreadJvmArgument", firstThread);
            report.put("macLauncherFirstThreadMarker", launcherFirstThread);
            report.put("macFirstThreadVerified", firstThreadVerified);
            report.put("minimumHeap2GiB", minimumHeap);
            report.put("maximumHeap8GiB", maximumHeap);
            report.put("localhostDebuggerEnabled", localDebugger);
            require(!macOS || firstThreadVerified, failures, "macOS first-thread launcher mode could not be verified");
            require(minimumHeap, failures, "The effective explicit initial heap is not 2 GiB");
            require(maximumHeap, failures, "The effective explicit maximum heap is not 8 GiB");
            require(debugger.isEmpty() || localDebugger, failures, "Configured JDWP is not a single localhost-only socket server");

            String classpath = System.getProperty("java.class.path", "");
            int entries = classpath.isEmpty() ? 0 : classpath.split(Pattern.quote(File.pathSeparator), -1).length;
            report.put("classpathEntries", entries);
            require(entries > 0, failures, "The JVM classpath is empty");

            String dli = System.getProperty("fabric.dli.config");
            boolean dliExists = false;
            boolean dliFrozen = false;
            if (dli != null && !dli.isBlank()) {
                Path configuration = Path.of(decodeDli(dli));
                dliExists = configuration.isAbsolute() && Files.isRegularFile(configuration);
                dliFrozen = configuration.toString().replace('\\', '/').contains(SNAPSHOT_PATH);
            }
            report.put("dliConfigExists", dliExists);
            report.put("dliConfigFrozen", dliFrozen);
            require(dliExists, failures, "The real DLI configuration does not exist at an absolute path");
            require(dliFrozen, failures, "The real DLI configuration is outside the frozen client runtime");

            Map<String, Object> resources = new LinkedHashMap<>();
            for (String name : RESOURCES) {
                // Resource lookup does not define or initialize the referenced class.
                URL location = ClassLoader.getSystemResource(name);
                String value = location == null ? null : location.toExternalForm();
                resources.put(name, value);
                require(value != null && value.contains(SNAPSHOT_PATH), failures,
                        "Required system resource is absent or outside the frozen client runtime: " + name);
            }
            report.put("systemResources", resources);

            int minecraft = 0;
            int blaze3d = 0;
            int lwjgl = 0;
            for (Class<?> loaded : instrumentation.getAllLoadedClasses()) {
                String name = loaded.getName();
                if (name.startsWith("net.minecraft.")) minecraft++;
                if (name.startsWith("com.mojang.blaze3d.")) blaze3d++;
                if (name.startsWith("org.lwjgl.")) lwjgl++;
            }
            report.put("loadedMinecraftClasses", minecraft);
            report.put("loadedBlaze3dClasses", blaze3d);
            report.put("loadedLwjglClasses", lwjgl);
            require(minecraft == 0 && blaze3d == 0 && lwjgl == 0, failures,
                    "Game or graphics classes were already loaded before the probe finished");
            exitCode = failures.isEmpty() ? 0 : 1;
        } catch (Throwable failure) {
            // Exception messages/stacks can contain arguments or arbitrary launcher
            // values. Record only the type, plus our fixed diagnostic text.
            report.put("probeFailureType", failure.getClass().getName());
            failures.add("The probe could not complete all launch checks");
        } finally {
            report.put("passed", exitCode == 0);
            report.put("exitCode", exitCode);
            try {
                if (destination == null) throw new IllegalStateException("No absolute report destination");
                Files.createDirectories(destination.getParent());
                Files.writeString(destination, json(report) + "\n", StandardCharsets.UTF_8);
            } catch (Throwable failure) {
                exitCode = 1;
                System.err.println("ClientLaunchCommandProbe: report could not be written; DLI main will not run.");
            }
            // There is deliberately no path that returns from premain to DLI.
            System.exit(exitCode);
            throw new AssertionError("JVM exit unexpectedly returned from the launch probe");
        }
    }

    private static void require(boolean condition, List<String> failures, String message) {
        if (!condition) failures.add(message);
    }

    private static boolean heapMatches(List<String> arguments, String prefix, long expected) {
        String value = null;
        for (String argument : arguments) if (argument.startsWith(prefix)) value = argument.substring(prefix.length());
        if (value == null || value.isEmpty()) return false;
        try {
            long multiplier = switch (Character.toLowerCase(value.charAt(value.length() - 1))) {
                case 'k' -> 1L << 10;
                case 'm' -> 1L << 20;
                case 'g' -> 1L << 30;
                default -> 1;
            };
            String number = multiplier == 1 ? value : value.substring(0, value.length() - 1);
            return Math.multiplyExact(Long.parseLong(number), multiplier) == expected;
        } catch (ArithmeticException | NumberFormatException invalid) {
            return false;
        }
    }

    private static boolean localhostDebugger(String argument) {
        String options = argument.substring(argument.indexOf('=') >= 0 && argument.startsWith("-agentlib:")
                ? argument.indexOf('=') + 1 : "-Xrunjdwp:".length());
        Map<String, String> values = new LinkedHashMap<>();
        for (String option : options.split(",")) {
            int equals = option.indexOf('=');
            if (equals > 0 && values.put(option.substring(0, equals), option.substring(equals + 1)) != null) return false;
        }
        String address = values.getOrDefault("address", "");
        return "dt_socket".equals(values.get("transport")) && "y".equals(values.get("server"))
                && address.matches("(?:127\\.0\\.0\\.1|localhost|\\[::1\\]):[0-9]{1,5}");
    }

    /** DLI encodes path spaces and literal doubled at-signs before passing -D values. */
    private static String decodeDli(String encoded) {
        Matcher matcher = Pattern.compile("@@([0-9a-fA-F]{4})").matcher(encoded);
        StringBuilder decoded = new StringBuilder();
        int start = 0;
        while (matcher.find()) {
            decoded.append(encoded, start, matcher.start()).append((char) Integer.parseInt(matcher.group(1), 16));
            start = matcher.end();
        }
        return decoded.append(encoded, start, encoded.length()).toString();
    }

    /** Minimal JSON writer so the premain agent itself needs only the JDK. */
    private static String json(Object value) {
        if (value == null) return "null";
        if (value instanceof Boolean || value instanceof Number) return value.toString();
        if (value instanceof Map<?, ?> map) {
            List<String> fields = new ArrayList<>();
            map.forEach((key, item) -> fields.add(json(key.toString()) + ":" + json(item)));
            return "{" + String.join(",", fields) + "}";
        }
        if (value instanceof Iterable<?> iterable) {
            List<String> items = new ArrayList<>();
            iterable.forEach(item -> items.add(json(item)));
            return "[" + String.join(",", items) + "]";
        }
        if (!(value instanceof String text)) throw new IllegalArgumentException("Unsupported probe report value");
        StringBuilder quoted = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            switch (character) {
                case '"' -> quoted.append("\\\"");
                case '\\' -> quoted.append("\\\\");
                case '\n' -> quoted.append("\\n");
                case '\r' -> quoted.append("\\r");
                case '\t' -> quoted.append("\\t");
                default -> {
                    if (character < 0x20) quoted.append(String.format(Locale.ROOT, "\\u%04x", (int) character));
                    else quoted.append(character);
                }
            }
        }
        return quoted.append('"').toString();
    }
}
