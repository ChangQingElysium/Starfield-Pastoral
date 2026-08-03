package com.stardew.craft.cutscene.server;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FestivalCutsceneRecoveryContractTest {
    @Test
    void serverTimeoutAndDisconnectBothAdvanceWaitingFestivalState() throws IOException {
        String tracker = source("cutscene/server/ServerCutsceneTracker.java");
        String interactionLock = source("cutscene/server/CutsceneInteractionLock.java");

        assertTrue(tracker.contains("failActiveEvent(player, \"playback timeout\")"));
        assertTrue(tracker.contains("ActiveFestivalHandlers\n                .onCutsceneUnavailable(player, eventId)"));
        assertTrue(interactionLock.contains(
                "failActiveEvent(player, \"player disconnected\")"));
    }

    @Test
    void clientPlaybackFailureSendsAnAbortInsteadOfLeavingTheServerWaiting() throws IOException {
        String eventPlayer = source("cutscene/runtime/EventPlayer.java");

        assertTrue(eventPlayer.contains("abortFailedPlayback(exception)"));
        assertTrue(eventPlayer.contains("new com.stardew.craft.cutscene.network.AbortCutscenePayload"));
    }

    @Test
    void festivalCutscenesAlwaysUseTheRecoveringStartPath() throws IOException {
        for (String service : List.of(
                "festival/EggFestivalService.java",
                "festival/FlowerDanceService.java",
                "festival/LuauFestivalService.java",
                "festival/MoonlightJelliesFestivalService.java",
                "festival/FestivalOfIceService.java",
                "festival/WinterStarFestivalService.java")) {
            String source = source(service);
            assertTrue(source.contains("ActiveFestivalHandlers.startCutsceneOrRecover"), service);
            assertFalse(source.contains("ServerCutsceneTracker.startEvent"), service);
        }
    }

    private static String source(String relativePath) throws IOException {
        Path projectDir = Path.of(System.getProperty("stardewcraft.projectDir"));
        return Files.readString(projectDir.resolve("src/main/java/com/stardew/craft").resolve(relativePath));
    }
}
