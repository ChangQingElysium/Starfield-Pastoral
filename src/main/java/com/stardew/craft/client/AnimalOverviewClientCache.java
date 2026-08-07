package com.stardew.craft.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Client-side snapshot for Stardew Valley's V-menu animal page. */
public final class AnimalOverviewClientCache {
    public record Entry(
            long animalId,
            String animalTypeId,
            String customName,
            String displayNameKey,
            String baseType,
            String sourceType,
            int friendship,
            int petStatus,
            boolean receivedAnimalCracker,
            String textureId,
            int textureWidth,
            int textureHeight
    ) {
    }

    private static List<Entry> entries = List.of();
    private static boolean synced;

    private AnimalOverviewClientCache() {
    }

    public static List<Entry> entries() {
        return entries;
    }

    public static boolean isSynced() {
        return synced;
    }

    public static void update(List<Entry> incoming) {
        synced = true;
        if (incoming == null || incoming.isEmpty()) {
            entries = List.of();
            return;
        }

        List<Entry> copy = new ArrayList<>(incoming.size());
        for (Entry entry : incoming) {
            if (entry == null || entry.animalId() <= 0L
                    || entry.animalTypeId() == null || entry.animalTypeId().isBlank()) {
                continue;
            }
            copy.add(new Entry(
                    entry.animalId(),
                    entry.animalTypeId().trim().toLowerCase(java.util.Locale.ROOT),
                    entry.customName() == null ? "" : entry.customName(),
                    entry.displayNameKey() == null ? "" : entry.displayNameKey(),
                    entry.baseType() == null ? "" : entry.baseType(),
                    entry.sourceType() == null ? "" : entry.sourceType(),
                    Math.max(0, Math.min(1000, entry.friendship())),
                    Math.max(0, Math.min(2, entry.petStatus())),
                    entry.receivedAnimalCracker(),
                    entry.textureId() == null ? "" : entry.textureId(),
                    Math.max(0, entry.textureWidth()),
                    Math.max(0, entry.textureHeight())
            ));
        }
        copy.sort(Comparator
                .comparing(Entry::baseType, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Entry::sourceType, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Comparator.comparingInt(Entry::friendship).reversed())
                .thenComparingLong(Entry::animalId));
        entries = List.copyOf(copy);
    }

    public static void reset() {
        entries = List.of();
        synced = false;
    }
}
