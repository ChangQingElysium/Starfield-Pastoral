package com.stardew.craft.platform.fabric.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;

/** Converts preserved metadata into executable vanilla tests, never replacement assertions. */
public final class GameTestBridge {
    public static final String INDEX_RESOURCE = "META-INF/stardewcraft/fabric-gametest-index.json";

    private GameTestBridge() {}

    public static List<TestFunction> indexedTests() {
        ClassLoader loader = GameTestBridge.class.getClassLoader();
        try (var stream = loader.getResourceAsStream(INDEX_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing complete Fabric GameTest index: " + INDEX_RESOURCE);
            }
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return indexedTests(reader, loader);
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read Fabric GameTest index", failure);
        }
    }

    static List<TestFunction> indexedTests(Reader reader, ClassLoader loader) {
        JsonObject index = JsonParser.parseReader(reader).getAsJsonObject();
        if (index.get("schema_version").getAsInt() != 1 || index.get("total_tests").getAsInt() < 1) {
            throw new IllegalStateException("Invalid or empty Fabric GameTest index");
        }
        List<TestFunction> functions = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        for (var element : index.getAsJsonArray("classes")) {
            JsonObject entry = element.getAsJsonObject();
            String name = entry.get("class_name").getAsString();
            int expected = entry.get("test_count").getAsInt();
            if (expected < 1 || !seen.add(name)) {
                throw new IllegalStateException("Duplicate or empty indexed GameTest class: " + name);
            }
            Class<?> type;
            try {
                type = Class.forName(name, false, loader);
            } catch (ClassNotFoundException failure) {
                throw new IllegalStateException("Indexed GameTest class is missing: " + name, failure);
            }
            List<TestFunction> actual = testsForClass(type);
            if (actual.size() != expected) {
                throw new IllegalStateException("GameTest coverage mismatch for " + name + ": indexed "
                        + expected + ", registered " + actual.size());
            }
            functions.addAll(actual);
        }
        if (functions.size() != index.get("total_tests").getAsInt()) {
            throw new IllegalStateException("Total GameTest coverage does not match the source index");
        }
        return List.copyOf(functions);
    }

    public static List<TestFunction> testsForClass(Class<?> type) {
        Objects.requireNonNull(type, "GameTest class");
        List<TestFunction> functions = new ArrayList<>();
        Arrays.stream(type.getDeclaredMethods()).sorted(java.util.Comparator.comparing(Method::getName))
                .filter(method -> method.isAnnotationPresent(GameTest.class))
                .map(GameTestBridge::testFunction).forEach(functions::add);
        return List.copyOf(functions);
    }

    private static TestFunction testFunction(Method method) {
        if (method.getReturnType() != void.class || method.getParameterCount() != 1
                || method.getParameterTypes()[0] != GameTestHelper.class || !method.trySetAccessible()) {
            throw new IllegalStateException("GameTest must be an accessible void method(GameTestHelper): " + method);
        }
        GameTest metadata = method.getAnnotation(GameTest.class);
        Class<?> declaringClass = method.getDeclaringClass();
        String className = declaringClass.getSimpleName().toLowerCase(Locale.ROOT);
        PrefixGameTestTemplate prefix = method.getAnnotation(PrefixGameTestTemplate.class);
        if (prefix == null) prefix = declaringClass.getAnnotation(PrefixGameTestTemplate.class);
        String namePrefix = prefix == null || prefix.value() ? className + "." : "";
        String testName = namePrefix + method.getName().toLowerCase(Locale.ROOT);
        GameTestHolder holder = declaringClass.getAnnotation(GameTestHolder.class);
        String namespace = !metadata.templateNamespace().isEmpty() ? metadata.templateNamespace()
                : holder == null ? "minecraft" : holder.value();
        String template = metadata.template().isEmpty() ? testName : namePrefix + metadata.template();
        String structure = ResourceLocation.fromNamespaceAndPath(namespace, template).toString();
        Consumer<GameTestHelper> body = helper -> invoke(method, helper);
        return new TestFunction(metadata.batch(), testName, structure,
                StructureUtils.getRotationForRotationSteps(metadata.rotationSteps()), metadata.timeoutTicks(),
                metadata.setupTicks(), metadata.required(), metadata.manualOnly(), metadata.attempts(),
                metadata.requiredSuccesses(), metadata.skyAccess(), body);
    }

    private static void invoke(Method method, GameTestHelper helper) {
        try {
            // All 908 current tests are static, many deliberately have private
            // constructors. Only an actual instance test needs a constructor.
            Object receiver = Modifier.isStatic(method.getModifiers()) ? null
                    : method.getDeclaringClass().getConstructor().newInstance();
            method.invoke(receiver, helper);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("GameTest failed: " + method, cause);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot invoke original GameTest: " + method, failure);
        }
    }
}
