package com.stardew.craft.combat.equipment;

/**
 * Pure Stardew ring rules shared by event integration and unit tests.
 */
public final class CombatRingRules {
    public static final int YOBA_PROTECTION_DURATION_TICKS = 100;
    private static final int BASE_INVULNERABILITY_TICKS = 24;
    private static final int PROTECTION_RING_BONUS_TICKS = 8;

    private CombatRingRules() {
    }

    /**
     * Ring.cs: 0.1 + LuckLevel / 100.
     */
    public static float warriorTriggerChance(float luckLevel) {
        return clampChance(0.10f + luckLevel / 100.0f);
    }

    /**
     * Farmer.cs: (0.9 - health / 100) / (3 - LuckLevel / 10),
     * with an extra 0.2 chance at 15 health or below.
     */
    public static float yobaProtectionChance(int currentHealth, float luckLevel) {
        float denominator = 3.0f - luckLevel / 10.0f;
        if (denominator <= 0.0f) {
            return 1.0f;
        }
        float chance = (0.9f - Math.max(0, currentHealth) / 100.0f) / denominator;
        if (currentHealth <= 15) {
            chance += 0.20f;
        }
        return clampChance(chance);
    }

    /**
     * Farmer.cs uses 1200 ms, extended by 400 ms per Protection Ring.
     */
    public static int invulnerabilityTicks(int protectionRingCount) {
        return BASE_INVULNERABILITY_TICKS
                + Math.max(0, protectionRingCount) * PROTECTION_RING_BONUS_TICKS;
    }

    /**
     * Other dimensions retain Minecraft's own base window and only map the
     * Protection Ring's extension onto it.
     */
    public static int minecraftInvulnerabilityTicks(
            int minecraftBaseTicks,
            int protectionRingCount
    ) {
        return Math.max(0, minecraftBaseTicks)
                + Math.max(0, protectionRingCount) * PROTECTION_RING_BONUS_TICKS;
    }

    /**
     * Ring.cs Phoenix Ring recovery: maxHealth * 0.5 + equipped ring count.
     */
    public static int phoenixReviveHealth(int maxHealth, int phoenixRingCount) {
        int safeMaxHealth = Math.max(1, maxHealth);
        return Math.max(1, Math.min(
                safeMaxHealth,
                (int) (safeMaxHealth * 0.5f + Math.max(0, phoenixRingCount))
        ));
    }

    /**
     * Farmer.cs Thorns Ring reflection. Low-damage hits average the raw hit and
     * damage after defense; each equipped ring repeats the reflected damage.
     */
    public static int thornsDamage(
            int damageBeforeDefense,
            int damageTaken,
            int thornsRingCount
    ) {
        int count = Math.max(0, thornsRingCount);
        if (count == 0) {
            return 0;
        }
        int reflectedPerRing = Math.max(1, damageBeforeDefense);
        if (damageTaken < 10) {
            reflectedPerRing = (int) Math.ceil(
                    (reflectedPerRing + Math.max(1, damageTaken)) / 2.0
            );
        }
        return reflectedPerRing * count;
    }

    /**
     * MeleeWeapon.cs reduces swing duration by the accumulated weapon-speed
     * multiplier. Convert that duration reduction to an attack-rate bonus.
     */
    public static float weaponSpeedToAttackRateBonus(float weaponSpeedMultiplier) {
        float clamped = Math.max(0.0f, Math.min(0.95f, weaponSpeedMultiplier));
        return 1.0f / (1.0f - clamped) - 1.0f;
    }

    /**
     * Ring of Yoba uses a 100-health scale. Other dimensions retain their own
     * health pools, so project the current health percentage onto that scale.
     */
    public static int healthOnStardewScale(float currentHealth, float maximumHealth) {
        if (maximumHealth <= 0.0f) {
            return 100;
        }
        float ratio = Math.max(0.0f, Math.min(1.0f, currentHealth / maximumHealth));
        return Math.round(ratio * 100.0f);
    }

    /**
     * Phoenix Ring's +1 health per ring is mapped through the existing 5:1
     * health-display boundary, while the half-health recovery stays relative.
     */
    public static float phoenixMinecraftHealth(
            float maximumHealth,
            int phoenixRingCount,
            float healthRatio
    ) {
        float safeMaximum = Math.max(1.0f, maximumHealth);
        float safeRatio = Math.max(1.0f, healthRatio);
        return Math.min(
                safeMaximum,
                safeMaximum * 0.5f + Math.max(0, phoenixRingCount) / safeRatio
        );
    }

    private static float clampChance(float chance) {
        return Math.max(0.0f, Math.min(1.0f, chance));
    }
}
