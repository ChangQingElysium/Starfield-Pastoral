package com.stardew.craft.platform.fabric.gametest;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GlobalTestReporter;
import net.minecraft.gametest.framework.RetryOptions;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;

class FabricSmokeTestReporterTest {
    @TempDir Path directory;

    @Test
    void successAndFailureAreWrittenImmediatelyWithOriginalErrorAndNoTestMutation() throws Exception {
        List<Log> logs = new ArrayList<>(); Path file = directory.resolve("evidence/tests.jsonl");
        var reporter = new FabricSmokeTestReporter(logger(logs), file); var passed = test();
        var place = GameTestInfo.class.getDeclaredMethod("setStructureBlockPos", BlockPos.class); place.setAccessible(true);
        place.invoke(passed, new BlockPos(7, 8, 9)); reporter.onTestSuccess(passed);
        assertFalse(passed.isDone()); assertFalse(passed.hasStarted()); assertNull(passed.getError());
        var failed = test(); var original = new AssertionError("original assertion"); failed.fail(original);
        reporter.onTestFailed(failed); assertSame(original, failed.getError()); assertTrue(failed.hasFailed());
        var rows = Files.readAllLines(file).stream().map(value -> JsonParser.parseString(value).getAsJsonObject()).toList();
        assertEquals(List.of("SESSION", "PASS", "FAIL"), rows.stream().map(value -> value.get("status").getAsString()).toList());
        assertEquals("7, 8, 9", rows.get(1).get("position").getAsString()); assertEquals("unplaced", rows.get(2).get("position").getAsString());
        assertEquals(rows.getFirst().get("run_id"), rows.getLast().get("run_id"));
        assertEquals(passed.getTestName(), rows.get(1).get("test_name").getAsString());
        assertTrue(rows.get(2).get("error_stack").getAsString().contains("original assertion"));
        var failure = logs.stream().filter(log -> log.level.equals("error")).findFirst().orElseThrow();
        assertSame(original, failure.arguments[failure.arguments.length - 1]);
        assertTrue(logs.stream().anyMatch(log -> log.level.equals("info") && log.arguments[0].toString().contains("PASS")));
    }

    @Test
    void evidenceIoFailureLogsTheWriteErrorWithoutThrowingOrReplacingTheTestFailure() throws Exception {
        Path blocker = directory.resolve("file"); Files.writeString(blocker, "existing"); List<Log> logs = new ArrayList<>();
        var reporter = new FabricSmokeTestReporter(logger(logs), blocker.resolve("tests.jsonl"));
        var test = test(); var original = new IllegalStateException("test failure"); test.fail(original);
        assertDoesNotThrow(() -> reporter.onTestFailed(test)); assertSame(original, test.getError());
        assertTrue(logs.stream().filter(log -> log.level.equals("error")).anyMatch(log -> log.arguments[log.arguments.length - 1] instanceof java.io.IOException));
    }

    @Test
    void absentPropertyKeepsTheReporterAndRepeatedRegistrationInstallsOnlyOnce() throws Exception {
        var delegate = GlobalTestReporter.class.getDeclaredField("DELEGATE"); delegate.setAccessible(true); Object previous = delegate.get(null);
        var installed = FabricSmokeTestReporter.class.getDeclaredField("installed"); installed.setAccessible(true); boolean wasInstalled = installed.getBoolean(null);
        String property = System.getProperty(FabricSmokeTestReporter.PROPERTY);
        try {
            installed.setBoolean(null, false); System.clearProperty(FabricSmokeTestReporter.PROPERTY);
            FabricSmokeTestReporter.installIfRequested(); assertSame(previous, delegate.get(null));
            Path first = directory.resolve("first.jsonl"); Path second = directory.resolve("second.jsonl");
            System.setProperty(FabricSmokeTestReporter.PROPERTY, first.toString()); FabricSmokeTestReporter.installIfRequested();
            Object reporter = delegate.get(null); assertInstanceOf(FabricSmokeTestReporter.class, reporter);
            System.setProperty(FabricSmokeTestReporter.PROPERTY, second.toString()); FabricSmokeTestReporter.installIfRequested();
            assertSame(reporter, delegate.get(null)); assertFalse(Files.exists(second));
            GlobalTestReporter.onTestSuccess(test()); assertEquals(2, Files.readAllLines(first).size());
        } finally {
            delegate.set(null, previous); installed.setBoolean(null, wasInstalled);
            if (property == null) System.clearProperty(FabricSmokeTestReporter.PROPERTY); else System.setProperty(FabricSmokeTestReporter.PROPERTY, property);
        }
    }

    private static GameTestInfo test() {
        return new GameTestInfo(GameTestBridge.testsForClass(Suite.class).getFirst(), Rotation.NONE, null, RetryOptions.noRetries());
    }
    private static Logger logger(List<Log> records) {
        return (Logger) Proxy.newProxyInstance(Logger.class.getClassLoader(), new Class<?>[] { Logger.class }, (proxy, method, args) -> {
            if (List.of("info", "warn", "error").contains(method.getName())) {
                Object[] flattened = args != null && args.length == 2 && args[1] instanceof Object[] values
                        ? java.util.stream.Stream.concat(java.util.stream.Stream.of(args[0]), java.util.Arrays.stream(values)).toArray() : args;
                records.add(new Log(method.getName(), flattened));
            }
            if (method.getReturnType() == boolean.class) return true;
            if (method.getName().equals("getName")) return "smoke-test";
            return null;
        });
    }
    private record Log(String level, Object[] arguments) {}
    private static final class Suite { @GameTest public static void originalTest(GameTestHelper helper) {} }
}
