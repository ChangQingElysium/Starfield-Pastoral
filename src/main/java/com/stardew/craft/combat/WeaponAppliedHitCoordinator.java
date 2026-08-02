package com.stardew.craft.combat;

/**
 * Runs the explicit, behavior-preserving order for one exact applied hit.
 *
 * <p>The coordinator contains no concrete weapon or skill ids. Common rules,
 * authored skill impacts, built-in weapon passives, and frozen presentation
 * each own their selection predicates. This order is part of the combat
 * contract because several rules synchronously emit nested child damage.</p>
 */
final class WeaponAppliedHitCoordinator {
    private WeaponAppliedHitCoordinator() {
    }

    static void apply(ResolvedWeaponHit hit) {
        if (!hit.dealtPositiveDamage()) {
            return;
        }

        BuiltinSkillAppliedHitRules.applyHolySmite(hit);
        BuiltinSkillAppliedHitRules.applyTetanusStrike(hit);
        BuiltinSkillAppliedHitRules.applyBoneFracture(hit);
        BuiltinSkillAppliedHitRules.recordDesperatePlunderOutcome(hit);
        BuiltinSkillAppliedHitRules.applyTideReel(hit);
        BuiltinSkillAppliedHitRules.applyDragontoothShivStab(hit);
        BuiltinSkillAppliedHitRules.applyGalaxyDaggerStarleap(hit);
        BuiltinSkillAppliedHitRules.applyYetiToothSpineControl(hit);
        BuiltinSkillAppliedHitRules.applyElfBladeLeafMark(hit);
        BuiltinSkillAppliedHitRules.startTemperedBilletFireRing(hit);
        BuiltinSkillAppliedHitRules.applyClaymoreFoldbackReturn(hit);
        BuiltinSkillAppliedHitRules.applyFishcatchThrust(hit);
        BuiltinSkillAppliedHitRules.applyDwarfDaggerThrust(hit);
        BuiltinSkillAppliedHitRules.applyCarvingThrust(hit);
        BuiltinSkillAppliedHitRules.applyFemurSlam(hit);
        BuiltinSkillAppliedHitRules.applyDwarfRuneGuard(hit);
        BuiltinSkillAppliedHitRules.applyForestBlessing(hit);
        BuiltinSkillAppliedHitRules.applyWindSpire(hit);
        BuiltinSkillAppliedHitRules.applyWickedVenomRipple(hit);
        BuiltinSkillAppliedHitRules.applyWickedNestBurst(hit);
        BuiltinSkillAppliedHitRules.applyShadowExecute(hit);
        BuiltinSkillAppliedHitRules.armTemperedQuench(hit);
        BuiltinSkillAppliedHitRules.applyTemperedQuenchBlast(hit);
        BuiltinSkillAppliedHitRules.applyDragonBreathThrust(hit);
        BuiltinSkillAppliedHitRules.recordDragonBreathJudgement(hit);
        BuiltinSkillAppliedHitRules.settleSingularityEvolveRewards(hit);
        BuiltinSkillAppliedHitRules.settleStartrailRiftRewards(hit);
        BuiltinSkillAppliedHitRules
                .recordInfinityDaggerSingularityBackstab(hit);
        BuiltinSkillAppliedHitRules.applyDarkSwordLifeSteal(hit);
        BuiltinWeaponPassiveAppliedHitRules.applyIridiumNeedle(hit);
        BuiltinSkillAppliedHitRules.fireElfLeaf(hit);
        BuiltinWeaponPassiveAppliedHitRules.addAppliedWeaponResources(hit);
        CommonWeaponAppliedHitRules.applyKnockback(hit);
        BuiltinSkillAppliedHitRules.applyBurglarShank(hit);
        CommonWeaponAppliedHitRules.applyVampiricEnchantment(hit);
        CommonWeaponAppliedHitRules.notifyTrinkets(hit);
        CommonWeaponAppliedHitRules.applyKillRewards(hit);
        LegacyWeaponHitPresentation.emitDamageNumber(hit);
        LegacyWeaponHitPresentation.emitGeneralSkillImpact(hit);
        BuiltinSkillAppliedHitRules.applyInsectEyeStance(hit);
        BuiltinSkillAppliedHitRules.emitInsectDash(hit);
        BuiltinSkillAppliedHitRules.applyYetiMark(hit);
        BuiltinSkillAppliedHitRules.applyLavaBrand(hit);
        BuiltinWeaponPassiveAppliedHitRules
                .emitObsidianResonancePresentation(hit);
        BuiltinWeaponPassiveAppliedHitRules
                .emitCrystalDaggerBurstPresentation(hit);
        BuiltinWeaponPassiveAppliedHitRules.consumeObsidianResonance(hit);
        BuiltinWeaponPassiveAppliedHitRules.triggerCrystalBurst(hit);
        BuiltinWeaponPassiveAppliedHitRules
                .triggerEvolvedSingularityFollowup(hit);
        BuiltinWeaponPassiveAppliedHitRules.applyOssifiedCriticalMark(hit);
        BuiltinWeaponPassiveAppliedHitRules.applyYetiFollowup(hit);
        BuiltinWeaponPassiveAppliedHitRules.applyLavaHeat(hit);
        BuiltinWeaponPassiveAppliedHitRules.applyGalaxyMark(hit);
        BuiltinWeaponPassiveAppliedHitRules.applyInfinityMark(hit);
        BuiltinSkillAppliedHitRules.applyGalaxyDaggerFinalMark(hit);
        BuiltinSkillAppliedHitRules.applyInfinityDaggerFinalMark(hit);
        BuiltinWeaponPassiveAppliedHitRules.applyTideMark(hit);
        BuiltinSkillAppliedHitRules.emitIridiumNeedleThrustStrike(hit);
        BuiltinSkillAppliedHitRules
                .emitTemplarJudgementSettlementImpact(hit);
        StardewWeaponAreaAttack.applySecondaryTargets(hit);
    }
}
