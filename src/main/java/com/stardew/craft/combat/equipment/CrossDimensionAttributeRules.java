package com.stardew.craft.combat.equipment;

import com.stardew.craft.combat.DimensionDamageMapper;
import com.stardew.craft.combat.StardewWeaponSpeedRules;
import com.stardew.craft.combat.WeaponType;

/**
 * Explicit numeric projections from Stardew attributes to Minecraft's native
 * attribute model outside the Stardew dimensions.
 */
public final class CrossDimensionAttributeRules {
    private static final double DAILY_LUCK_TO_MINECRAFT_LUCK = 10.0D;
    private static final int BASE_STARDEW_MAX_HEALTH = 100;

    private CrossDimensionAttributeRules() {
    }

    public static double minecraftArmor(float stardewDefense) {
        return Math.max(0.0D, stardewDefense);
    }

    public static double minecraftAttackDamage(float stardewAttack) {
        return Math.max(0.0D, stardewAttack) * 3.0D;
    }

    public static double minecraftAttackMultiplier(
            float equipmentMultiplier,
            boolean fighter,
            boolean brute
    ) {
        double multiplier = Math.max(0.0D, 1.0D + equipmentMultiplier);
        if (fighter) {
            multiplier *= 1.10D;
        }
        if (brute) {
            multiplier *= 1.15D;
        }
        return multiplier - 1.0D;
    }

    public static double minecraftLuck(float luckLevel, double dailyLuck) {
        return luckLevel + dailyLuck * DAILY_LUCK_TO_MINECRAFT_LUCK;
    }

    public static double minecraftMaximumHealthBonus(int stardewMaximumHealth) {
        int permanentGrowth = Math.max(
                0,
                stardewMaximumHealth - BASE_STARDEW_MAX_HEALTH
        );
        return permanentGrowth / DimensionDamageMapper.getHealthRatio();
    }

    public static float minecraftCriticalChance(
            float baseChance,
            float flatWeaponBonus,
            float equipmentMultiplier,
            float luckLevel
    ) {
        return minecraftCriticalChance(
                baseChance,
                flatWeaponBonus,
                equipmentMultiplier,
                0.0F,
                false,
                luckLevel
        );
    }

    public static float minecraftCriticalChance(
            float baseChance,
            float flatWeaponBonus,
            float equipmentMultiplier,
            float flatPlayerBonus,
            boolean scout,
            float luckLevel
    ) {
        float chance = Math.max(0.0F, baseChance + flatWeaponBonus);
        chance *= Math.max(0.0F, 1.0F + equipmentMultiplier);
        chance += Math.max(0.0F, flatPlayerBonus);
        if (scout) {
            chance *= 1.5F;
        }
        chance += luckLevel * (chance / 40.0F);
        return Math.clamp(chance, 0.0F, 1.0F);
    }

    public static float minecraftCriticalMultiplier(
            float nativeMultiplier,
            float weaponBonusPercent,
            float equipmentMultiplier
    ) {
        return minecraftCriticalMultiplier(
                nativeMultiplier,
                weaponBonusPercent,
                equipmentMultiplier,
                false
        );
    }

    public static float minecraftCriticalMultiplier(
            float nativeMultiplier,
            float weaponBonusPercent,
            float equipmentMultiplier,
            boolean desperado
    ) {
        float weaponMultiplier = 1.0F
                + Math.max(-100.0F, weaponBonusPercent) / 100.0F;
        float equipmentPower = Math.max(
                0.0F,
                1.0F + equipmentMultiplier
        );
        float multiplier = Math.max(0.0F, nativeMultiplier)
                * weaponMultiplier
                * equipmentPower;
        return desperado ? multiplier * 2.0F : multiplier;
    }

    public static double minecraftAttackKnockback(float knockbackMultiplier) {
        return Math.max(0.0D, knockbackMultiplier);
    }

    public static double weaponAttackSpeedCorrection(
            double currentItemAttackRate,
            double addValueScale,
            WeaponType weaponType,
            int stardewSpeed
    ) {
        double targetRate = StardewWeaponSpeedRules.attacksPerSecond(
                weaponType,
                stardewSpeed,
                0.0F
        );
        if (!Double.isFinite(addValueScale)
                || Math.abs(addValueScale) < 1.0E-6D) {
            return 0.0D;
        }
        return (targetRate - currentItemAttackRate) / addValueScale;
    }

    public static double weaponRawAttackSpeedCorrection(
            double currentItemAttackRate,
            double addValueScale,
            WeaponType weaponType,
            int rawStardewSpeed
    ) {
        return weaponRawAttackSpeedCorrection(
                currentItemAttackRate,
                addValueScale,
                weaponType,
                rawStardewSpeed,
                0.0F,
                0.0F
        );
    }

    public static double weaponRawAttackSpeedCorrection(
            double currentItemAttackRate,
            double addValueScale,
            WeaponType weaponType,
            int rawStardewSpeed,
            float weaponLocalSpeedMultiplier,
            float equipmentSpeedMultiplier
    ) {
        float combinedMultiplier = weaponLocalSpeedMultiplier
                + equipmentSpeedMultiplier;
        double finalTargetRate = StardewWeaponSpeedRules
                .attacksPerSecondFromRawSpeed(
                weaponType,
                rawStardewSpeed,
                combinedMultiplier
        );
        double equipmentRateScale = 1.0D
                + CombatRingRules.weaponSpeedToAttackRateBonus(
                        equipmentSpeedMultiplier
                );
        double targetRateBeforeEquipment = finalTargetRate
                / equipmentRateScale;
        if (!Double.isFinite(addValueScale)
                || Math.abs(addValueScale) < 1.0E-6D) {
            return 0.0D;
        }
        return (targetRateBeforeEquipment - currentItemAttackRate)
                / addValueScale;
    }
}
