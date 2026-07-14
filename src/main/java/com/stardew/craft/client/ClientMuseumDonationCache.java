package com.stardew.craft.client;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Client-side cache for museum donation status.
 */
public final class ClientMuseumDonationCache {
    private static final Set<String> DONATED = new HashSet<>();
    private static boolean synced;

    private ClientMuseumDonationCache() {
    }

    public static void setDonated(Collection<String> donatedIds) {
        DONATED.clear();
        if (donatedIds != null) {
            DONATED.addAll(donatedIds);
        }
        synced = true;
    }

    public static boolean isDonated(String itemId) {
        return DONATED.contains(itemId);
    }

    public static boolean isEmpty() {
        return DONATED.isEmpty();
    }

    public static boolean isSynced() {
        return synced;
    }

    public static void clear() {
        DONATED.clear();
        synced = false;
    }
}
