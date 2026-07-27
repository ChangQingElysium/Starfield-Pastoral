package com.stardew.craft.shop;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopPurchaseRequestTrackerTest {
    @AfterEach
    void clear() {
        ShopPurchaseRequestTracker.clearForTests();
    }

    @Test
    void duplicateRequestIsRejectedForTheSamePlayer() {
        UUID player = UUID.randomUUID();
        UUID request = UUID.randomUUID();

        assertTrue(ShopPurchaseRequestTracker.tryBegin(
                player, request));
        assertFalse(ShopPurchaseRequestTracker.tryBegin(
                player, request));
        assertTrue(ShopPurchaseRequestTracker.tryBegin(
                UUID.randomUUID(), request));
    }

    @Test
    void oldestRequestExpiresAtThePerPlayerBound() {
        UUID player = UUID.randomUUID();
        ArrayList<UUID> requests = new ArrayList<>();
        for (int index = 0;
             index <= ShopPurchaseRequestTracker
                     .MAX_REQUESTS_PER_PLAYER;
             index++) {
            UUID request = UUID.randomUUID();
            requests.add(request);
            assertTrue(ShopPurchaseRequestTracker.tryBegin(
                    player, request));
        }

        assertTrue(ShopPurchaseRequestTracker.tryBegin(
                player, requests.getFirst()));
        assertFalse(ShopPurchaseRequestTracker.tryBegin(
                player, requests.getLast()));
    }
}
