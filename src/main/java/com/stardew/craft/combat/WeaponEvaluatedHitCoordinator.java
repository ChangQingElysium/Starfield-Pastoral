package com.stardew.craft.combat;

/** Explicit order for effects committed after pipeline evaluation but before health loss. */
final class WeaponEvaluatedHitCoordinator {
    private WeaponEvaluatedHitCoordinator() {
    }

    static void apply(EvaluatedWeaponHit hit) {
        BuiltinSkillEvaluatedHitRules.emitSteelSpineStrike(hit);
        CommonWeaponEvaluatedHitRules.bindAppliedHitFrame(hit);
    }
}
