package com.stardew.craft.communitycenter.network;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BundleClientDataTest {
    private final BundleClientData data = BundleClientData.INSTANCE;

    @BeforeEach
    void resetClientCache() {
        data.clear();
    }

    @Test
    void starPlacementIsDeduplicatedByArea() {
        data.update(Map.of(), new boolean[7], Map.of(), true);

        data.markDisplayStarArea(2);
        data.markDisplayStarArea(2);
        data.markDisplayStarArea(-1);
        data.markDisplayStarArea(6);

        assertEquals(1, data.getDisplayStarCount());
    }

    @Test
    void progressSyncWaitsForJunimoPlacementAfterInitialLoginSync() {
        data.update(Map.of(), new boolean[7], Map.of(), true);
        boolean[] completed = new boolean[7];
        completed[0] = true;

        data.update(Map.of(), completed, Map.of(), true);
        assertEquals(0, data.getDisplayStarCount());

        data.markDisplayStarArea(0);
        assertEquals(1, data.getDisplayStarCount());
    }

    @Test
    void authoritativeSyncRepairsAnInflatedDisplayCount() {
        data.update(Map.of(), new boolean[7], Map.of(), true);
        data.markDisplayStarArea(0);
        data.markDisplayStarArea(1);
        assertEquals(2, data.getDisplayStarCount());

        data.update(Map.of(), new boolean[7], Map.of(), true);
        assertEquals(0, data.getDisplayStarCount());
    }
}
