package com.stardew.craft.leaderboard;

public enum LeaderboardCategory {
    WEALTH("wealth", "stardewcraft.leaderboard.category.wealth"),
    MINING("mining", "stardewcraft.leaderboard.category.mining"),
    FISHING("fishing", "stardewcraft.leaderboard.category.fishing"),
    SHIPPING("shipping", "stardewcraft.leaderboard.category.shipping"),
    COMBAT("combat", "stardewcraft.leaderboard.category.combat"),
    SKILLS("skills", "stardewcraft.leaderboard.category.skills"),
    SOCIAL("social", "stardewcraft.leaderboard.category.social"),
    COOKING("cooking", "stardewcraft.leaderboard.category.cooking"),
    ANIMALS("animals", "stardewcraft.leaderboard.category.animals"),
    LIFE("life", "stardewcraft.leaderboard.category.life");

    private final String id;
    private final String titleKey;

    LeaderboardCategory(String id, String titleKey) {
        this.id = id;
        this.titleKey = titleKey;
    }

    public String id() {
        return id;
    }

    public String titleKey() {
        return titleKey;
    }
}
