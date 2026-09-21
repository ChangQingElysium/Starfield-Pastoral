package com.stardew.craft.shop;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;

/**
 * Bounded server-side replay guard for the optional shop purchase protocol.
 *
 * <p>Entries intentionally survive reconnects during the server process so a
 * retried request cannot charge the same player twice. Both dimensions are
 * bounded to prevent untrusted clients from growing server memory.
 */
public final class ShopPurchaseRequestTracker {
    static final int MAX_TRACKED_PLAYERS = 512;
    static final int MAX_REQUESTS_PER_PLAYER = 256;

    private static final LinkedHashMap<UUID, LinkedHashSet<UUID>>
            REQUESTS = new LinkedHashMap<>(16, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(
                        Map.Entry<UUID, LinkedHashSet<UUID>> eldest
                ) {
                    return size() > MAX_TRACKED_PLAYERS;
                }
            };

    private ShopPurchaseRequestTracker() {
    }

    /**
     * Claims one request ID. Returns false when it was already claimed.
     */
    public static synchronized boolean tryBegin(
            UUID playerId,
            UUID requestId
    ) {
        if (playerId == null || requestId == null) {
            return false;
        }
        LinkedHashSet<UUID> requests =
                REQUESTS.computeIfAbsent(
                        playerId, ignored -> new LinkedHashSet<>());
        if (!requests.add(requestId)) {
            return false;
        }
        while (requests.size() > MAX_REQUESTS_PER_PLAYER) {
            Iterator<UUID> iterator = requests.iterator();
            iterator.next();
            iterator.remove();
        }
        return true;
    }

    static synchronized void clearForTests() {
        REQUESTS.clear();
    }
}
