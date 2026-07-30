package com.stardew.craft.combat.skill;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SteelFalchionTickOwnershipContractTest {
    @Test
    void duplicateRuntimeAndGlobalTicksAreIdempotent()
            throws IOException {
        Path relative = Path.of(
                "src",
                "main",
                "java",
                "com",
                "stardew",
                "craft",
                "combat",
                "skill",
                "SteelFalchionLineTracker.java"
        );
        Path source = locate(relative);
        String contents = Files.readString(source);
        int state = contents.indexOf(
                "private static final class PlayerState"
        );
        int tick = contents.indexOf(
                "public static void tick(ServerPlayer player, long nowTick)"
        );
        int nextMethod = contents.indexOf(
                "private static LineState createLine(",
                tick
        );

        assertTrue(state >= 0 && tick > state && nextMethod > tick);
        assertTrue(
                contents.substring(state, tick).contains(
                        "lastProcessedTick = Long.MIN_VALUE"
                )
        );
        String tickMethod = contents.substring(tick, nextMethod);
        assertTrue(tickMethod.contains(
                "if (state.lastProcessedTick == nowTick)"
        ));
        assertTrue(tickMethod.contains(
                "state.lastProcessedTick = nowTick;"
        ));
    }

    private static Path locate(Path relative) throws IOException {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relative);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IOException("Cannot locate " + relative);
    }
}
