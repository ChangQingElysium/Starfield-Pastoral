package com.stardew.craft.gametest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

/** Opt-in: deliberately stalls one headless server tick to exercise the real diagnostic thread. */
@GameTestHolder("stardewcraft_stall_diagnostics")
@PrefixGameTestTemplate(false)
public final class ServerStallDiagnosticsGameTests {
    @GameTest(templateNamespace = "stardewcraft_stall_diagnostics", template = "ring_utilities", timeoutTicks = 100)
    public static void blockedServerStillProducesAStack(GameTestHelper helper) throws InterruptedException {
        CountDownLatch captured = new CountDownLatch(1);
        AtomicReference<String> message = new AtomicReference<>();
        AbstractAppender appender = new AbstractAppender("StallDiagnosticTest", null, null, true, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                String text = event.getMessage().getFormattedMessage();
                if (text.startsWith("[SERVER_STALL] No tick progress")) {
                    message.set(text);
                    captured.countDown();
                }
            }
        };
        var logger = (org.apache.logging.log4j.core.Logger) LogManager.getRootLogger();
        appender.start();
        logger.addAppender(appender);
        try {
            // The callback runs on the real server thread. A server-scheduled diagnostic would never run here.
            helper.assertTrue(captured.await(16, TimeUnit.SECONDS), "No stack captured while server was blocked");
            helper.assertTrue(message.get().contains("blockedServerStillProducesAStack"),
                    "Diagnostic did not capture the blocking server frame");
            helper.assertTrue(message.get().contains("sample=1/3"), "First stall sample was not bounded correctly");
            helper.succeed();
        } finally {
            logger.removeAppender(appender);
            appender.stop();
        }
    }
}
