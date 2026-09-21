package com.stardew.craft.inventory;

import java.util.Arrays;
import java.util.Optional;

/** The five original-game backpack trash-can tiers. */
public enum TrashCanTier {
    BASIC(0, "", 0, "", 0, 0),
    COPPER(1, "stardewcraft:copper_trash_can_upgrade", 1_000, "stardewcraft:copper_bar", 5, 15),
    STEEL(2, "stardewcraft:steel_trash_can_upgrade", 2_500, "stardewcraft:iron_bar", 5, 30),
    GOLD(3, "stardewcraft:gold_trash_can_upgrade", 5_000, "stardewcraft:gold_bar", 5, 45),
    IRIDIUM(4, "stardewcraft:iridium_trash_can_upgrade", 12_500, "stardewcraft:iridium_bar", 5, 60);

    public static final int MIN_LEVEL = 0;
    public static final int MAX_LEVEL = 4;

    private final int level;
    private final String upgradeItemId;
    private final int price;
    private final String barItemId;
    private final int barCount;
    private final int reclaimPercent;

    TrashCanTier(int level, String upgradeItemId, int price, String barItemId, int barCount, int reclaimPercent) {
        this.level = level;
        this.upgradeItemId = upgradeItemId;
        this.price = price;
        this.barItemId = barItemId;
        this.barCount = barCount;
        this.reclaimPercent = reclaimPercent;
    }

    public int level() {
        return level;
    }

    public String upgradeItemId() {
        return upgradeItemId;
    }

    public int price() {
        return price;
    }

    public String barItemId() {
        return barItemId;
    }

    public int barCount() {
        return barCount;
    }

    public int reclaimPercent() {
        return reclaimPercent;
    }

    public static int clampLevel(int level) {
        return Math.max(MIN_LEVEL, Math.min(MAX_LEVEL, level));
    }

    public static TrashCanTier forLevel(int level) {
        return values()[clampLevel(level)];
    }

    public static Optional<TrashCanTier> fromUpgradeItemId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(tier -> tier.level > 0 && tier.upgradeItemId.equals(itemId))
                .findFirst();
    }

    public Optional<TrashCanTier> next() {
        return level >= MAX_LEVEL ? Optional.empty() : Optional.of(values()[level + 1]);
    }
}
