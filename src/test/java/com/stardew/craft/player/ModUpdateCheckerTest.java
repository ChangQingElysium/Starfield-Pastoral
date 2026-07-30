package com.stardew.craft.player;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModUpdateCheckerTest {
    @Test
    void selectsNewestPublishedCompatibleVersionByDate() {
        String response = """
                [
                  {
                    "version_number": "0.5.2",
                    "date_published": "2026-07-22T17:27:46Z",
                    "status": "listed"
                  },
                  {
                    "version_number": "0.5.3",
                    "date_published": "2026-07-27T05:50:17Z",
                    "status": "listed"
                  },
                  {
                    "version_number": "9.0.0",
                    "date_published": "2026-07-29T05:50:17Z",
                    "status": "archived"
                  }
                ]
                """;

        var status = ModUpdateChecker.parseResponse("0.5.2", 200, response);

        assertEquals("0.5.3", status.latestVersion());
        assertEquals(ModUpdateChecker.State.OUTDATED, status.state());
        assertTrue(status.isOutdated());
    }

    @Test
    void recognizesCurrentAndDevelopmentVersions() {
        String response = """
                [{
                  "version_number": "0.5.3",
                  "date_published": "2026-07-27T05:50:17Z",
                  "status": "listed"
                }]
                """;

        assertEquals(
                ModUpdateChecker.State.UP_TO_DATE,
                ModUpdateChecker.parseResponse("v0.5.3", 200, response).state());
        var ahead = ModUpdateChecker.parseResponse("0.5.4-dev", 200, response);
        assertEquals(ModUpdateChecker.State.AHEAD, ahead.state());
        assertFalse(ahead.isOutdated());
    }

    @Test
    void failedOrMalformedResponsesDegradeWithoutAnUpdateWarning() {
        var failed = ModUpdateChecker.parseResponse("0.5.3", 503, "unavailable");
        var malformed = ModUpdateChecker.parseResponse("0.5.3", 200, "{}");

        assertEquals(ModUpdateChecker.State.UNAVAILABLE, failed.state());
        assertEquals(ModUpdateChecker.State.UNAVAILABLE, malformed.state());
        assertFalse(failed.isOutdated());
        assertFalse(malformed.isOutdated());
    }
}
