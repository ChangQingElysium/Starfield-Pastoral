package com.stardew.craft.cutscene.network;

import java.util.Set;

/**
 * Client-side cache of events the local player has already seen.
 * Populated by {@link SyncEventSeenPayload} on login.
 */
public final class ClientEventSeenCache {

    private static volatile State state =
            new State(false, Set.of());

    private ClientEventSeenCache() {}

    public static boolean hasSeen(String eventId) {
        return state.seenEvents().contains(eventId);
    }

    public static Set<String> all() {
        return state.seenEvents();
    }

    public static void replace(Set<String> events) {
        state = new State(true, events);
    }

    /**
     * Returns true once the server has sent at least one SyncEventSeenPayload.
     * Used by EventTriggerChecker to avoid auto-firing events during the
     * brief login window when the seen cache is still empty.
     */
    public static boolean isSynced() {
        return state.syncedFromServer();
    }

    public static void reset() {
        state = new State(false, Set.of());
    }

    private record State(
            boolean syncedFromServer,
            Set<String> seenEvents
    ) {
        private State {
            seenEvents = Set.copyOf(seenEvents);
        }
    }
}
