package com.stardew.craft.client;

import java.util.Set;

/** Client-only snapshot of festivals whose world-level {@code available_when} conditions pass. */
public final class ClientFestivalAvailability {
    private static volatile State state =
            new State(false, Set.of());

    private ClientFestivalAvailability() {
    }

    public static boolean allows(String festivalId) {
        State current = state;
        return !current.synced()
                || current.festivalIds().contains(festivalId);
    }

    public static void replace(Set<String> replacement) {
        state = new State(
                true,
                replacement == null ? Set.of() : replacement);
    }

    public static void clear() {
        state = new State(false, Set.of());
    }

    private record State(boolean synced, Set<String> festivalIds) {
        private State {
            festivalIds = Set.copyOf(festivalIds);
        }
    }
}
