package com.stardew.craft.combat;

/**
 * Pure boundary rules for converting a Minecraft damage event into the
 * Stardew damage range consumed by {@link DamagePipeline}.
 */
public final class IncomingDamageResolver {
    private IncomingDamageResolver() {
    }

    public static DamageRange resolveRange(
            float rawDamage,
            float originalDamage,
            DamageRequest.SourceKind sourceKind,
            float monsterDamage,
            float stardewMaximumHealth,
            float minecraftMaximumHealth
    ) {
        float safeRawDamage = Math.max(0.0f, rawDamage);
        if (sourceKind == DamageRequest.SourceKind.MONSTER_ATTACK) {
            float authoritativeDamage = monsterDamage > 0.0f
                    ? monsterDamage
                    : safeRawDamage;
            if (originalDamage > 0.0f) {
                authoritativeDamage *= safeRawDamage / originalDamage;
            }
            return stardewMonsterRange(authoritativeDamage);
        }
        if (sourceKind == DamageRequest.SourceKind.ENVIRONMENT) {
            float safeMinecraftHealth = Math.max(1.0f, minecraftMaximumHealth);
            float mapped = safeRawDamage
                    * (Math.max(1.0f, stardewMaximumHealth) / safeMinecraftHealth);
            return new DamageRange(mapped, mapped);
        }
        return new DamageRange(safeRawDamage, safeRawDamage);
    }

    /**
     * Farmer.takeDamage adds an integer in
     * [min(-1,-damage/8), max(1,damage/8)) before defense.
     */
    static DamageRange stardewMonsterRange(float damage) {
        int baseDamage = Math.max(1, (int) damage);
        int minimumDelta = Math.min(-1, -baseDamage / 8);
        int maximumDeltaExclusive = Math.max(1, baseDamage / 8);
        return new DamageRange(
                baseDamage + minimumDelta,
                baseDamage + maximumDeltaExclusive - 1
        );
    }

    public record DamageRange(float minimum, float maximum) {
        public DamageRange {
            if (!Float.isFinite(minimum) || !Float.isFinite(maximum)) {
                throw new IllegalArgumentException("Incoming damage range must be finite");
            }
            if (minimum < 0.0f || maximum < minimum) {
                throw new IllegalArgumentException("Invalid incoming damage range");
            }
        }
    }
}
