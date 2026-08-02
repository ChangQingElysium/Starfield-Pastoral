package com.stardew.craft.combat.equipment;

import com.stardew.craft.combat.WeaponType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CrossDimensionAttributeRulesTest {
    @Test
    void defenseMapsToMinecraftArmorWithoutChangingTheStardewValue() {
        assertEquals(0.0D, CrossDimensionAttributeRules.minecraftArmor(-1.0f));
        assertEquals(5.0D, CrossDimensionAttributeRules.minecraftArmor(5.0f));
    }

    @Test
    void attackKeepsStardewsThreeDamagePerLevelRule() {
        assertEquals(0.0D, CrossDimensionAttributeRules.minecraftAttackDamage(-1.0f));
        assertEquals(6.0D, CrossDimensionAttributeRules.minecraftAttackDamage(2.0f));
    }

    @Test
    void nativeAttackMultiplierCarriesCombatProfessionsWithoutFlatteningThem() {
        assertEquals(
                0.0D,
                CrossDimensionAttributeRules.minecraftAttackMultiplier(
                        0.0F,
                        false,
                        false
                )
        );
        assertEquals(
                0.3915D,
                CrossDimensionAttributeRules.minecraftAttackMultiplier(
                        0.10F,
                        true,
                        true
                ),
                0.000001D
        );
    }

    @Test
    void luckLevelAndDailyLuckBothReachMinecraftLuck() {
        assertEquals(2.0D, CrossDimensionAttributeRules.minecraftLuck(1.0f, 0.10D));
        assertEquals(0.0D, CrossDimensionAttributeRules.minecraftLuck(1.0f, -0.10D));
    }

    @Test
    void permanentMaximumHealthGrowthUsesTheExistingHealthScale() {
        assertEquals(
                0.0D,
                CrossDimensionAttributeRules.minecraftMaximumHealthBonus(100)
        );
        assertEquals(
                5.0D,
                CrossDimensionAttributeRules.minecraftMaximumHealthBonus(125)
        );
        assertEquals(
                0.0D,
                CrossDimensionAttributeRules.minecraftMaximumHealthBonus(75)
        );
    }

    @Test
    void nativeCriticalProjectionKeepsStardewChanceSemantics() {
        assertEquals(
                0.022F,
                CrossDimensionAttributeRules.minecraftCriticalChance(
                        0.02F,
                        0.0F,
                        0.10F,
                        0.0F
                ),
                0.000001F
        );
        assertEquals(
                0.0275F,
                CrossDimensionAttributeRules.minecraftCriticalChance(
                        0.02F,
                        0.0F,
                        0.10F,
                        10.0F
                ),
                0.000001F
        );
        assertEquals(
                1.0F,
                CrossDimensionAttributeRules.minecraftCriticalChance(
                        2.0F,
                        0.0F,
                        0.0F,
                        0.0F
                )
        );
        assertEquals(
                0.19215F,
                CrossDimensionAttributeRules.minecraftCriticalChance(
                        0.02F,
                        0.0F,
                        0.10F,
                        0.10F,
                        true,
                        2.0F
                ),
                0.000001F
        );
    }

    @Test
    void nativeCriticalPowerAndKnockbackPreserveMinecraftBaselines() {
        assertEquals(
                1.98F,
                CrossDimensionAttributeRules.minecraftCriticalMultiplier(
                        1.5F,
                        20.0F,
                        0.10F
                ),
                0.000001F
        );
        assertEquals(
                0.10D,
                CrossDimensionAttributeRules.minecraftAttackKnockback(0.10F),
                0.000001D
        );
        assertEquals(
                3.96F,
                CrossDimensionAttributeRules.minecraftCriticalMultiplier(
                        1.5F,
                        20.0F,
                        0.10F,
                        true
                ),
                0.000001F
        );
        assertEquals(
                0.0D,
                CrossDimensionAttributeRules.minecraftAttackKnockback(-1.0F)
        );
    }

    @Test
    void weaponSpeedUsesTheDynamicWeaponStatsForBuiltInAndPublicWeapons() {
        assertEquals(
                0.964102564D,
                CrossDimensionAttributeRules.weaponAttackSpeedCorrection(
                        1.6D,
                        1.0D,
                        WeaponType.SWORD,
                        0
                ),
                0.000001D
        );
        assertEquals(
                18.4D,
                CrossDimensionAttributeRules.weaponAttackSpeedCorrection(
                        1.6D,
                        1.0D,
                        WeaponType.SWORD,
                        5
                ),
                0.000001D
        );
        assertEquals(
                18.4D,
                CrossDimensionAttributeRules.weaponAttackSpeedCorrection(
                        1.6D,
                        1.0D,
                        WeaponType.SWORD,
                        10
                ),
                0.000001D
        );
        assertEquals(
                4.0D,
                CrossDimensionAttributeRules.weaponAttackSpeedCorrection(
                        1.0D,
                        1.0D,
                        WeaponType.DAGGER,
                        0
                ),
                0.000001D
        );
        assertEquals(
                -4.290598291D,
                CrossDimensionAttributeRules.weaponAttackSpeedCorrection(
                        9.0D,
                        1.5D,
                        WeaponType.SWORD,
                        0
                ),
                0.000001D
        );
    }
}
