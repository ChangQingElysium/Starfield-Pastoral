package com.stardew.craft.platform.fabric.gametest;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringReader;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;

class GameTestBridgeTest {
    @Test
    void staticTestsWithPrivateConstructorsKeepMetadataAndInvokeTheOriginalBody() {
        var functions = GameTestBridge.testsForClass(StaticSuite.class);
        assertEquals(1, functions.size());
        var function = functions.getFirst();
        assertEquals("originalassertion", function.testName());
        assertEquals("fixture:empty", function.structureName());
        assertEquals("fixture_batch", function.batchName());
        assertEquals(Rotation.CLOCKWISE_90, function.rotation());
        assertEquals(240, function.maxTicks());
        assertEquals(7, function.setupTicks());
        assertFalse(function.required());
        assertTrue(function.manualOnly());
        assertEquals(4, function.maxAttempts());
        assertEquals(2, function.requiredSuccesses());
        assertTrue(function.skyAccess());
        int previous = StaticSuite.executions.get();
        GameTestHelper helper = new GameTestHelper(null);
        function.run(helper);
        assertEquals(previous + 1, StaticSuite.executions.get());
        assertSame(helper, StaticSuite.lastHelper);
    }

    @Test
    void methodNamespaceAndPrefixOverrideTheClassWhileVanillaDefaultsRemainIntact() {
        var method = GameTestBridge.testsForClass(OverrideSuite.class).getFirst();
        assertEquals("overridesuite.methodwins", method.testName());
        assertEquals("method_ns:overridesuite.room", method.structureName());
        var defaults = GameTestBridge.testsForClass(DefaultSuite.class).getFirst();
        assertEquals("defaultsuite.vanilladefaults", defaults.testName());
        assertEquals("minecraft:defaultsuite.vanilladefaults", defaults.structureName());
        assertEquals(100, defaults.maxTicks());
        assertEquals("defaultBatch", defaults.batchName());
        assertTrue(defaults.required());
        assertFalse(defaults.skyAccess());
        assertFalse(defaults.manualOnly());
        assertEquals(1, defaults.maxAttempts());
        assertEquals(1, defaults.requiredSuccesses());
    }

    @Test
    void originalAssertionFailuresAreNotWrappedOrConvertedToSuccess() {
        var function = GameTestBridge.testsForClass(FailingSuite.class).getFirst();
        AssertionError failure = assertThrows(AssertionError.class, () -> function.run(null));
        assertSame(FailingSuite.FAILURE, failure);
    }

    @Test
    void runtimeIndexFailsIfConversionDropsAMethodAClassOrTheTotal() {
        String name = StaticSuite.class.getName();
        var functions = GameTestBridge.indexedTests(new StringReader(index(name, 1, 1)), getClass().getClassLoader());
        assertEquals(1, functions.size());
        assertThrows(IllegalStateException.class, () -> GameTestBridge.indexedTests(
                new StringReader(index(name, 2, 2)), getClass().getClassLoader()));
        assertThrows(IllegalStateException.class, () -> GameTestBridge.indexedTests(
                new StringReader(index(name, 1, 2)), getClass().getClassLoader()));
        assertThrows(IllegalStateException.class, () -> GameTestBridge.indexedTests(
                new StringReader(index("missing.GameTests", 1, 1)), getClass().getClassLoader()));
    }

    @Test
    void invalidTestSignaturesFailBeforeAWorldIsStarted() {
        assertThrows(IllegalStateException.class, () -> GameTestBridge.testsForClass(InvalidSuite.class));
    }

    private static String index(String name, int count, int total) {
        return "{\"schema_version\":1,\"total_tests\":" + total + ",\"classes\":[{\"class_name\":\""
                + name + "\",\"test_count\":" + count + "}]}";
    }

    @GameTestHolder("fixture")
    @PrefixGameTestTemplate(false)
    private static final class StaticSuite {
        static final AtomicInteger executions = new AtomicInteger();
        static GameTestHelper lastHelper;
        private StaticSuite() { throw new AssertionError("Static test constructors must never run"); }

        @GameTest(template = "empty", batch = "fixture_batch", rotationSteps = 1, timeoutTicks = 240,
                setupTicks = 7, required = false, manualOnly = true, attempts = 4,
                requiredSuccesses = 2, skyAccess = true)
        public static void originalAssertion(GameTestHelper helper) {
            lastHelper = helper;
            executions.incrementAndGet();
        }
    }

    @GameTestHolder("class_ns")
    @PrefixGameTestTemplate(false)
    private static final class OverrideSuite {
        @GameTest(templateNamespace = "method_ns", template = "room")
        @PrefixGameTestTemplate(true)
        public static void methodWins(GameTestHelper helper) {}
    }

    private static final class DefaultSuite {
        @GameTest
        public static void vanillaDefaults(GameTestHelper helper) {}
    }

    private static final class FailingSuite {
        static final AssertionError FAILURE = new AssertionError("original assertion");
        @GameTest
        public static void originalFailure(GameTestHelper helper) { throw FAILURE; }
    }

    private static final class InvalidSuite {
        @GameTest
        public static void invalid(String replacedHelper) {}
    }
}
