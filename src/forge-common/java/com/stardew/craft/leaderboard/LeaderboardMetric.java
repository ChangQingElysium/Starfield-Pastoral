package com.stardew.craft.leaderboard;

import java.util.Arrays;
import java.util.Optional;

public enum LeaderboardMetric {
    MONEY(LeaderboardCategory.WEALTH, "money", "stardewcraft.leaderboard.money", "stardewcraft.leaderboard.short.money", "stardewcraft.leaderboard.desc.money", "stardewcraft.leaderboard.value.money", true, false),
    MINE_DEPTH(LeaderboardCategory.MINING, "mine_depth", "stardewcraft.leaderboard.mine_depth", "stardewcraft.leaderboard.short.mine_depth", "stardewcraft.leaderboard.desc.mine_depth", "stardewcraft.leaderboard.value.mine_depth", true, false),
    MINE_BLOCKS_BROKEN(LeaderboardCategory.MINING, "mine_blocks_broken", "stardewcraft.leaderboard.mine_blocks_broken", "stardewcraft.leaderboard.short.mine_blocks_broken", "stardewcraft.leaderboard.desc.mine_blocks_broken", "stardewcraft.leaderboard.value.count", true, true),
    MINE_STONES_BROKEN(LeaderboardCategory.MINING, "mine_stones_broken", "stardewcraft.leaderboard.mine_stones_broken", "stardewcraft.leaderboard.short.mine_stones_broken", "stardewcraft.leaderboard.desc.mine_stones_broken", "stardewcraft.leaderboard.value.count", true, true),
    MINE_ORES_BROKEN(LeaderboardCategory.MINING, "mine_ores_broken", "stardewcraft.leaderboard.mine_ores_broken", "stardewcraft.leaderboard.short.mine_ores_broken", "stardewcraft.leaderboard.desc.mine_ores_broken", "stardewcraft.leaderboard.value.count", true, true),
    MINE_GEM_ORES_BROKEN(LeaderboardCategory.MINING, "mine_gem_ores_broken", "stardewcraft.leaderboard.mine_gem_ores_broken", "stardewcraft.leaderboard.short.mine_gem_ores_broken", "stardewcraft.leaderboard.desc.mine_gem_ores_broken", "stardewcraft.leaderboard.value.count", true, true),
    MINE_MINERAL_NODES_BROKEN(LeaderboardCategory.MINING, "mine_mineral_nodes_broken", "stardewcraft.leaderboard.mine_mineral_nodes_broken", "stardewcraft.leaderboard.short.mine_mineral_nodes_broken", "stardewcraft.leaderboard.desc.mine_mineral_nodes_broken", "stardewcraft.leaderboard.value.count", true, true),
    MINE_BLOCKS_BOMBED(LeaderboardCategory.MINING, "mine_blocks_bombed", "stardewcraft.leaderboard.mine_blocks_bombed", "stardewcraft.leaderboard.short.mine_blocks_bombed", "stardewcraft.leaderboard.desc.mine_blocks_bombed", "stardewcraft.leaderboard.value.count", true, true),
    FISH_CAUGHT(LeaderboardCategory.FISHING, "fish_caught", "stardewcraft.leaderboard.fish_caught", "stardewcraft.leaderboard.short.fish_caught", "stardewcraft.leaderboard.desc.fish_caught", "stardewcraft.leaderboard.value.count", true, true),
    ITEMS_SHIPPED(LeaderboardCategory.SHIPPING, "items_shipped", "stardewcraft.leaderboard.items_shipped", "stardewcraft.leaderboard.short.items_shipped", "stardewcraft.leaderboard.desc.items_shipped", "stardewcraft.leaderboard.value.count", true, true),
    SHIPPING_VALUE(LeaderboardCategory.SHIPPING, "shipping_value", "stardewcraft.leaderboard.shipping_value", "stardewcraft.leaderboard.short.shipping_value", "stardewcraft.leaderboard.desc.shipping_value", "stardewcraft.leaderboard.value.money", true, true),
    SHIPPING_VARIETY(LeaderboardCategory.SHIPPING, "shipping_variety", "stardewcraft.leaderboard.shipping_variety", "stardewcraft.leaderboard.short.shipping_variety", "stardewcraft.leaderboard.desc.shipping_variety", "stardewcraft.leaderboard.value.count", true, false),
    MONSTERS_SLAIN(LeaderboardCategory.COMBAT, "monsters_slain", "stardewcraft.leaderboard.monsters_slain", "stardewcraft.leaderboard.short.monsters_slain", "stardewcraft.leaderboard.desc.monsters_slain", "stardewcraft.leaderboard.value.count", true, true),
    SKILL_FARMING(LeaderboardCategory.SKILLS, "skill_farming", "stardewcraft.leaderboard.skill_farming", "stardewcraft.leaderboard.short.skill_farming", "stardewcraft.leaderboard.desc.skill_farming", "stardewcraft.leaderboard.value.experience", true, true),
    SKILL_FISHING(LeaderboardCategory.SKILLS, "skill_fishing", "stardewcraft.leaderboard.skill_fishing", "stardewcraft.leaderboard.short.skill_fishing", "stardewcraft.leaderboard.desc.skill_fishing", "stardewcraft.leaderboard.value.experience", true, true),
    SKILL_FORAGING(LeaderboardCategory.SKILLS, "skill_foraging", "stardewcraft.leaderboard.skill_foraging", "stardewcraft.leaderboard.short.skill_foraging", "stardewcraft.leaderboard.desc.skill_foraging", "stardewcraft.leaderboard.value.experience", true, true),
    SKILL_MINING(LeaderboardCategory.SKILLS, "skill_mining", "stardewcraft.leaderboard.skill_mining", "stardewcraft.leaderboard.short.skill_mining", "stardewcraft.leaderboard.desc.skill_mining", "stardewcraft.leaderboard.value.experience", true, true),
    SKILL_COMBAT(LeaderboardCategory.SKILLS, "skill_combat", "stardewcraft.leaderboard.skill_combat", "stardewcraft.leaderboard.short.skill_combat", "stardewcraft.leaderboard.desc.skill_combat", "stardewcraft.leaderboard.value.experience", true, true),
    GIFTS_GIVEN(LeaderboardCategory.SOCIAL, "gifts_given", "stardewcraft.leaderboard.gifts_given", "stardewcraft.leaderboard.short.gifts_given", "stardewcraft.leaderboard.desc.gifts_given", "stardewcraft.leaderboard.value.times", true, true),
    COOKING_COUNT(LeaderboardCategory.COOKING, "cooking_count", "stardewcraft.leaderboard.cooking_count", "stardewcraft.leaderboard.short.cooking_count", "stardewcraft.leaderboard.desc.cooking_count", "stardewcraft.leaderboard.value.count", true, true),
    ANIMALS_OWNED(LeaderboardCategory.ANIMALS, "animals_owned", "stardewcraft.leaderboard.animals_owned", "stardewcraft.leaderboard.short.animals_owned", "stardewcraft.leaderboard.desc.animals_owned", "stardewcraft.leaderboard.value.count", true, false),
    ANIMAL_PRODUCTS_COLLECTED(LeaderboardCategory.ANIMALS, "animal_products_collected", "stardewcraft.leaderboard.animal_products_collected", "stardewcraft.leaderboard.short.animal_products_collected", "stardewcraft.leaderboard.desc.animal_products_collected", "stardewcraft.leaderboard.value.count", true, true),
    PASS_OUTS(LeaderboardCategory.LIFE, "pass_outs", "stardewcraft.leaderboard.pass_outs", "stardewcraft.leaderboard.short.pass_outs", "stardewcraft.leaderboard.desc.pass_outs", "stardewcraft.leaderboard.value.times", true, true),
    COMBAT_DEATHS(LeaderboardCategory.LIFE, "combat_deaths", "stardewcraft.leaderboard.combat_deaths", "stardewcraft.leaderboard.short.combat_deaths", "stardewcraft.leaderboard.desc.combat_deaths", "stardewcraft.leaderboard.value.times", true, true),
    BASEMENT_SHORTS_STOLEN(LeaderboardCategory.LIFE, "basement_shorts_stolen", "stardewcraft.leaderboard.basement_shorts_stolen", "stardewcraft.leaderboard.short.basement_shorts_stolen", "stardewcraft.leaderboard.desc.basement_shorts_stolen", "stardewcraft.leaderboard.value.times", true, true),
    TRASH_CANS_CHECKED(LeaderboardCategory.LIFE, "trash_cans_checked", "stardewcraft.leaderboard.trash_cans_checked", "stardewcraft.leaderboard.short.trash_cans_checked", "stardewcraft.leaderboard.desc.trash_cans_checked", "stardewcraft.leaderboard.value.times", true, true);

    private final LeaderboardCategory category;
    private final String id;
    private final String titleKey;
    private final String shortKey;
    private final String descriptionKey;
    private final String valueKey;
    private final boolean descending;
    private final boolean periodic;

    LeaderboardMetric(LeaderboardCategory category, String id, String titleKey, String shortKey, String descriptionKey, String valueKey, boolean descending, boolean periodic) {
        this.category = category;
        this.id = id;
        this.titleKey = titleKey;
        this.shortKey = shortKey;
        this.descriptionKey = descriptionKey;
        this.valueKey = valueKey;
        this.descending = descending;
        this.periodic = periodic;
    }

    public LeaderboardCategory category() {
        return category;
    }

    public String id() {
        return id;
    }

    public String titleKey() {
        return titleKey;
    }

    public String shortKey() {
        return shortKey;
    }

    public String descriptionKey() {
        return descriptionKey;
    }

    public String valueKey() {
        return valueKey;
    }

    public boolean descending() {
        return descending;
    }

    public boolean supportsPeriod(LeaderboardPeriod period) {
        return period == null || period.isTotal() || periodic;
    }

    public static Optional<LeaderboardMetric> fromId(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(metric -> metric.id.equals(id)).findFirst();
    }
}
