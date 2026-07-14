package com.stardew.craft.client;

import java.util.Set;

/** Client-only snapshot of festivals whose world-level {@code available_when} conditions pass. */
public final class ClientFestivalAvailability {
    private static volatile Set<String> festivalIds = Set.of();
    private static volatile boolean synced;

    private ClientFestivalAvailability() {
    }

    public static boolean allows(String festivalId) {
        return !synced || festivalIds.contains(festivalId);
    }

    public static void replace(Set<String> replacement) {
        festivalIds = replacement == null ? Set.of() : Set.copyOf(replacement);
        synced = true;
    }

    public static void clear() {
        festivalIds = Set.of();
        synced = false;
    }
}
