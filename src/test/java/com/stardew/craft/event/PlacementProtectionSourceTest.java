package com.stardew.craft.event;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlacementProtectionSourceTest {

    @Test
    void protectedPlacementUsesEventCancellationInsteadOfManualRollback()
            throws Exception {
        String farmProtection = source("FarmAreaProtectionEvents.java");
        String pondProtection = source("FishPondProtectionEvents.java");

        assertTrue(farmProtection.contains("event.setUseItem(TriState.FALSE)"));
        assertFalse(farmProtection.contains(
                "event.getBlockSnapshot().restore()"));
        assertFalse(farmProtection.contains("snapshot.restore()"));
        assertFalse(farmProtection.contains(
                "level.destroyBlock(pos, true, player)"));
        assertFalse(pondProtection.contains(
                "event.getBlockSnapshot().restore()"));
        assertFalse(pondProtection.contains("snapshot.restore()"));
    }

    private static String source(String fileName) throws Exception {
        return Files.readString(Path.of(
                System.getProperty("stardewcraft.projectDir"),
                "src/main/java/com/stardew/craft/event",
                fileName));
    }
}
