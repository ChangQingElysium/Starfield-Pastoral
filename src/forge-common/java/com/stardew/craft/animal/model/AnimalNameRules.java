package com.stardew.craft.animal.model;

/** Shared server-side contract for player-visible managed-animal names. */
public final class AnimalNameRules {
    public static final int MAX_LENGTH = 48;

    private AnimalNameRules() {
    }

    public static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    public static boolean isValidExplicitName(String value) {
        String normalized = normalize(value);
        return !normalized.isEmpty()
                && normalized.length() <= MAX_LENGTH;
    }

    public static boolean isValidOptionalName(String value) {
        String normalized = normalize(value);
        return normalized.isEmpty()
                || normalized.length() <= MAX_LENGTH;
    }
}
