package com.stardew.craft.combat.equipment;

/**
 * Explicit numeric projections from Stardew attributes to Minecraft's native
 * attribute model outside the Stardew dimensions.
 */
public final class CrossDimensionAttributeRules {
    private static final double DAILY_LUCK_TO_MINECRAFT_LUCK = 10.0D;

    private CrossDimensionAttributeRules() {
    }

    public static double minecraftArmor(float stardewDefense) {
        return Math.max(0.0D, stardewDefense);
    }

    public static double minecraftAttackDamage(float stardewAttack) {
        return Math.max(0.0D, stardewAttack) * 3.0D;
    }

    public static double minecraftLuck(float luckLevel, double dailyLuck) {
        return luckLevel + dailyLuck * DAILY_LUCK_TO_MINECRAFT_LUCK;
    }
}
