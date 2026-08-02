package com.stardew.craft.combat;

import com.stardew.craft.item.weapon.IStardewWeapon;

/** Evaluated-hit rules shared by all weapon definitions. */
final class CommonWeaponEvaluatedHitRules {
    private CommonWeaponEvaluatedHitRules() {
    }

    static void bindAppliedHitFrame(EvaluatedWeaponHit hit) {
        float knockbackStrength = 0.0F;
        if (hit.skillContext().usesDefaultKnockback() && hit.successful()) {
            knockbackStrength = calculateKnockbackStrength(
                    WeaponStats.fromItemStack(hit.weapon())
            );
            if (hit.equipmentStats() != null) {
                knockbackStrength *= 1.0F
                        + hit.equipmentStats().getKnockbackBonus();
            }
        }

        String displaySkillId = hit.outcome().getSkillId();
        if (hit.bloodMoonActive()
                && (displaySkillId == null
                || "normal".equals(displaySkillId))) {
            displaySkillId = "dark_sword_blood_moon";
        } else if ("normal".equals(displaySkillId)) {
            displaySkillId = null;
        }
        if (displaySkillId == null
                && hit.outcome().isCrit()
                && hit.weapon().getItem() instanceof IStardewWeapon weapon
                && "iridium_needle".equals(weapon.getWeaponId())) {
            displaySkillId = "iridium_needle_thrust";
        }

        DamageNumberContextStore.bind(
                hit.attacker(),
                hit.target(),
                hit.source(),
                displaySkillId,
                hit.outcome().isCrit() && !hit.sweepTarget(),
                hit.weaponSnapshot(),
                hit.weaponIdentity(),
                hit.skillContext(),
                hit.outcome(),
                hit.primaryTarget(),
                hit.sweepTarget(),
                hit.inStardewDimension(),
                hit.target().isAlive() && hit.target().getHealth() > 0.0F,
                knockbackStrength,
                hit.gameTick() + 2L
        );
    }

    private static float calculateKnockbackStrength(WeaponStats stats) {
        return StardewWeaponKnockbackRules.minecraftStrength(
                stats.getWeaponType(),
                stats.getKnockback()
        );
    }
}
