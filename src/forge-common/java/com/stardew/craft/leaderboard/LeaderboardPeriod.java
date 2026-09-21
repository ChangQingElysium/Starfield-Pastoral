package com.stardew.craft.leaderboard;

import java.util.Arrays;
import java.util.Optional;

public enum LeaderboardPeriod {
    TOTAL("total", "stardewcraft.leaderboard.period.total", "stardewcraft.leaderboard.period.desc.total"),
    SEASON("season", "stardewcraft.leaderboard.period.season", "stardewcraft.leaderboard.period.desc.season"),
    WEEK("week", "stardewcraft.leaderboard.period.week", "stardewcraft.leaderboard.period.desc.week"),
    DAY("day", "stardewcraft.leaderboard.period.day", "stardewcraft.leaderboard.period.desc.day");

    private final String id;
    private final String titleKey;
    private final String descriptionKey;

    LeaderboardPeriod(String id, String titleKey, String descriptionKey) {
        this.id = id;
        this.titleKey = titleKey;
        this.descriptionKey = descriptionKey;
    }

    public String id() {
        return id;
    }

    public String titleKey() {
        return titleKey;
    }

    public String descriptionKey() {
        return descriptionKey;
    }

    public boolean isTotal() {
        return this == TOTAL;
    }

    public static Optional<LeaderboardPeriod> fromId(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(period -> period.id.equals(id)).findFirst();
    }
}