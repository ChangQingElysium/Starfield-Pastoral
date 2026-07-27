package com.stardew.craft.client;

import java.util.HashMap;
import java.util.Map;

/** Short-lived, server-authored text details for the currently opened animal query. */
public final class AnimalQueryClientDetails {
    private static final Map<Long, Details> DETAILS = new HashMap<>();

    private AnimalQueryClientDetails() {
    }

    public static void put(long animalId, String parentName) {
        DETAILS.put(
                animalId,
                new Details(parentName == null ? "" : parentName));
    }

    public static Details get(long animalId) {
        return DETAILS.get(animalId);
    }

    public static void remove(long animalId) {
        DETAILS.remove(animalId);
    }

    public record Details(String parentName) {
    }
}
