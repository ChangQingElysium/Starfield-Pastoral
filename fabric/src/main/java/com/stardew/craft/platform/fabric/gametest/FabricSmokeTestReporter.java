package com.stardew.craft.platform.fabric.gametest;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GlobalTestReporter;
import net.minecraft.gametest.framework.TestReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Development-only observation of completed tests; never selects or executes tests. */
public final class FabricSmokeTestReporter implements TestReporter {
    public static final String PROPERTY = "stardewcraft.fabric.smokeReport";
    private static final Logger LOGGER = LoggerFactory.getLogger("StardewCraft/FabricSmokeTests");
    private static final Gson JSON = new Gson();
    private static boolean installed;
    private final Logger logger;
    private final Path output;
    private final String runId = UUID.randomUUID().toString();

    public static synchronized void installIfRequested() {
        if (installed) return;
        String requested = System.getProperty(PROPERTY);
        if (requested == null) return;
        GlobalTestReporter.replaceWith(new FabricSmokeTestReporter(LOGGER,
                requested.isBlank() ? null : Path.of(requested).toAbsolutePath().normalize()));
        installed = true;
    }

    FabricSmokeTestReporter(Logger logger, Path output) {
        this.logger = java.util.Objects.requireNonNull(logger);
        this.output = output;
        var session = record("SESSION");
        append(session);
        logger.info("[FABRIC-SMOKE] reporter enabled run_id={} jsonl={}", runId, output);
    }

    @Override public void onTestSuccess(GameTestInfo test) { report(test, true); }
    @Override public void onTestFailed(GameTestInfo test) { report(test, false); }

    private synchronized void report(GameTestInfo test, boolean success) {
        var pos = test.getStructureBlockPos();
        String position = pos == null ? "unplaced" : pos.toShortString();
        Throwable error = test.getError();
        if (success) logger.info("[FABRIC-SMOKE] PASS test={} required={} pos={} duration_ms={} run_id={}",
                test.getTestName(), test.isRequired(), position, test.getRunTime(), runId);
        else logger.error("[FABRIC-SMOKE] FAIL test={} required={} pos={} duration_ms={} run_id={}",
                test.getTestName(), test.isRequired(), position, test.getRunTime(), runId, error);
        var event = record(success ? "PASS" : "FAIL");
        event.addProperty("test_name", test.getTestName());
        event.addProperty("structure", test.getStructureName());
        event.addProperty("required", test.isRequired());
        event.addProperty("position", position);
        event.addProperty("duration_ms", test.getRunTime());
        if (error != null) {
            event.addProperty("error_type", error.getClass().getName());
            event.addProperty("error_message", error.getMessage());
            var trace = new StringWriter(); error.printStackTrace(new PrintWriter(trace));
            event.addProperty("error_stack", trace.toString());
        }
        append(event);
    }

    private JsonObject record(String status) {
        var event = new JsonObject(); event.addProperty("status", status); event.addProperty("run_id", runId);
        event.addProperty("timestamp_utc", Instant.now().toString()); return event;
    }
    private void append(JsonObject event) {
        if (output == null) return;
        try {
            Path parent = output.toAbsolutePath().getParent(); if (parent != null) Files.createDirectories(parent);
            Files.writeString(output, JSON.toJson(event) + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException failure) {
            // Evidence-write failure must not replace the test's original result or assertion error.
            logger.error("[FABRIC-SMOKE] Could not append test evidence to " + output, failure);
        }
    }
}
