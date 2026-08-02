package com.stardew.craft.combat;

import com.stardew.craft.combat.network.BurglarShankLootPayload;
import com.stardew.craft.combat.network.CarvingKnifeThrustStrikePayload;
import com.stardew.craft.combat.network.IridiumNeedleThrustStrikePayload;
import com.stardew.craft.combat.network.TemplarJudgementImpactPayload;
import com.stardew.craft.combat.equipment.EquipmentMobEffectHandler;
import com.stardew.craft.combat.equipment.EquipmentNegativeStatusProtection;
import com.stardew.craft.combat.skill.BoneFractureTracker;
import com.stardew.craft.combat.skill.DarkSwordEffects;
import com.stardew.craft.combat.skill.HolyBladeDodgeTracker;
import com.stardew.craft.combat.skill.HolyBladeEffects;
import com.stardew.craft.combat.skill.YetiToothEffects;
import com.stardew.craft.combat.skill.YetiToothMarkTracker;
import com.stardew.craft.combat.skill.YetiFreezeTracker;
import com.stardew.craft.combat.skill.WickedKrisPoisonTracker;
import com.stardew.craft.combat.skill.handler.DarkSwordBloodDebtSkillHandler;
import com.stardew.craft.combat.skill.handler.DarkSwordBloodMoonSkillHandler;
import com.stardew.craft.combat.skill.handler.ElfBladeLeafSkillHandler;
import com.stardew.craft.combat.skill.handler.BoneFractureSkillHandler;
import com.stardew.craft.combat.skill.handler.CarvingThrustSkillHandler;
import com.stardew.craft.combat.skill.handler.ClaymoreFoldbackSkillHandler;
import com.stardew.craft.combat.skill.handler.DwarfDaggerThrustSkillHandler;
import com.stardew.craft.combat.skill.handler.DwarfRuneGuardSkillHandler;
import com.stardew.craft.combat.skill.handler.DesperatePlunderSkillHandler;
import com.stardew.craft.combat.skill.handler.DragonBreathJudgementSkillHandler;
import com.stardew.craft.combat.skill.handler.DragonBreathThrustSkillHandler;
import com.stardew.craft.combat.skill.handler.DragontoothShivStabSkillHandler;
import com.stardew.craft.combat.skill.handler.FemurSlamSkillHandler;
import com.stardew.craft.combat.skill.handler.FishcatchThrustSkillHandler;
import com.stardew.craft.combat.skill.handler.ForestBlessingSkillHandler;
import com.stardew.craft.combat.skill.handler.GalaxyDaggerStarstabSkillHandler;
import com.stardew.craft.combat.skill.handler.GalaxyDaggerStarleapSkillHandler;
import com.stardew.craft.combat.skill.handler.HolySmiteSkillHandler;
import com.stardew.craft.combat.skill.handler.InfinityDaggerSingularityStabSkillHandler;
import com.stardew.craft.combat.skill.handler.InsectDashSkillHandler;
import com.stardew.craft.combat.skill.handler.InfinityDaggerSingularityBackstabSkillHandler;
import com.stardew.craft.combat.skill.handler.ShadowDaggerExecuteSkillHandler;
import com.stardew.craft.combat.skill.handler.SingularityEvolveSkillHandler;
import com.stardew.craft.combat.skill.handler.StartrailRiftSkillHandler;
import com.stardew.craft.combat.skill.handler.TemperedBilletSkillHandler;
import com.stardew.craft.combat.skill.handler.TemperedQuenchSkillHandler;
import com.stardew.craft.combat.skill.handler.TetanusStrikeSkillHandler;
import com.stardew.craft.combat.skill.handler.TideReelSkillHandler;
import com.stardew.craft.combat.skill.handler.WindSpireThrustSkillHandler;
import com.stardew.craft.combat.skill.handler.WickedKrisNestBurstSkillHandler;
import com.stardew.craft.combat.skill.handler.WickedKrisVenomRippleSkillHandler;
import com.stardew.craft.combat.skill.handler.YetiToothSpineSkillHandler;
import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.player.PlayerStardewDataAPI;
import java.util.Objects;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

/** Applied-hit rules selected by an authored built-in skill id. */
final class BuiltinSkillAppliedHitRules {
    private BuiltinSkillAppliedHitRules() {
    }

    static void applyBurglarShank(ResolvedWeaponHit hit) {
        if (!"burglar_shank".equals(hit.skillId())
                || !hit.dealtPositiveDamage()
                || !(hit.attacker() instanceof ServerPlayer player)) {
            return;
        }
        LivingEntity target = hit.target();
        if (hit.killedByAttacker()) {
            BurglarLootHooks.fireBurglarKill(target, player);
            if (hit.inStardewDimension()) {
                PlayerStardewDataAPI.addMoney(player, 10);
                PacketDistributor.sendToPlayer(
                        player,
                        new BurglarShankLootPayload()
                );
            }
        } else {
            target.addEffect(new MobEffectInstance(
                    ModMobEffects.WEAK_POINT,
                    60,
                    1,
                    false,
                    true,
                    true
            ));
        }
    }

    static void applyTetanusStrike(ResolvedWeaponHit hit) {
        if (!"tetanus_strike".equals(hit.skillId())
                || !hit.dealtPositiveDamage()) {
            return;
        }
        hit.target().addEffect(new MobEffectInstance(
                ModMobEffects.VULNERABLE,
                TetanusStrikeSkillHandler.VULNERABLE_DURATION_TICKS,
                TetanusStrikeSkillHandler.VULNERABLE_AMPLIFIER
        ));
    }

    static void applyBoneFracture(ResolvedWeaponHit hit) {
        if (!"bone_fracture".equals(hit.skillId())
                || !hit.dealtPositiveDamage()
                || !(hit.target().level() instanceof ServerLevel level)) {
            return;
        }
        EquipmentNegativeStatusProtection.Decision protection =
                EquipmentNegativeStatusProtection.decide(
                        hit.target(),
                        BoneFractureSkillHandler.DEBUFF_DURATION_TICKS
                );
        if (protection.resisted()) {
            return;
        }
        int duration = protection.durationTicks();
        EquipmentMobEffectHandler.addPreAdjustedEffect(
                hit.target(),
                new MobEffectInstance(
                        MobEffects.WEAKNESS,
                        duration,
                        BoneFractureSkillHandler.WEAKNESS_AMPLIFIER,
                        false,
                        true,
                        true
                )
        );
        EquipmentMobEffectHandler.addPreAdjustedEffect(
                hit.target(),
                new MobEffectInstance(
                        MobEffects.MOVEMENT_SLOWDOWN,
                        duration,
                        BoneFractureSkillHandler.SLOWNESS_AMPLIFIER,
                        false,
                        true,
                        true
                )
        );
        BoneFractureTracker.apply(
                level,
                hit.target(),
                hit.gameTick(),
                duration
        );
    }

    static void recordDesperatePlunderOutcome(ResolvedWeaponHit hit) {
        if (!"desperate_plunder".equals(hit.skillId())
                || !hit.dealtPositiveDamage()
                || !(hit.attacker() instanceof ServerPlayer player)) {
            return;
        }
        DesperatePlunderSkillHandler.recordAppliedHit(
                player,
                hit.target(),
                hit.killedByAttacker()
        );
    }

    static void emitTemplarJudgementSettlementImpact(
            ResolvedWeaponHit hit
    ) {
        if (!"templar_judgement".equals(hit.skillId())
                || !hit.dealtPositiveDamage()
                || !(hit.target().level() instanceof ServerLevel level)) {
            return;
        }
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                hit.target(),
                new TemplarJudgementImpactPayload(hit.target().getId())
        );
        level.playSound(
                null,
                hit.target().blockPosition(),
                SoundEvents.PLAYER_ATTACK_CRIT,
                SoundSource.PLAYERS,
                0.6F,
                0.9F
        );
    }

    static void applyTideReel(ResolvedWeaponHit hit) {
        if (!"tide_reel".equals(hit.skillId())
                || !hit.dealtPositiveDamage()
                || !(hit.attacker() instanceof ServerPlayer player)) {
            return;
        }
        TideReelSkillHandler.onAppliedHit(player, hit.target());
    }

    static void applyDragontoothShivStab(ResolvedWeaponHit hit) {
        if (!"dragontooth_shiv_stab".equals(hit.skillId())
                || !hit.dealtPositiveDamage()) {
            return;
        }
        YetiFreezeTracker.applyWithEquipmentProtection(
                hit.target(),
                hit.gameTick(),
                DragontoothShivStabSkillHandler.FREEZE_DURATION_TICKS,
                YetiFreezeTracker.PresentationPolicy.SYNC_FREEZE_OVERLAY
        );
    }

    static void applyGalaxyDaggerStarleap(ResolvedWeaponHit hit) {
        if (!"galaxy_dagger_starleap".equals(hit.skillId())
                || !hit.dealtPositiveDamage()) {
            return;
        }
        YetiFreezeTracker.applyWithEquipmentProtection(
                hit.target(),
                hit.gameTick(),
                GalaxyDaggerStarleapSkillHandler.FREEZE_DURATION_TICKS,
                YetiFreezeTracker.PresentationPolicy.SYNC_FREEZE_OVERLAY
        );
    }

    static void applyYetiToothSpineControl(ResolvedWeaponHit hit) {
        if (!"yeti_tooth_spine".equals(hit.skillId())
                || !hit.dealtPositiveDamage()) {
            return;
        }
        YetiToothSpineSkillHandler.applySpineControl(hit.target());
    }

    static void applyElfBladeLeafMark(ResolvedWeaponHit hit) {
        if (!"elf_blade_leaf".equals(hit.skillId())
                || !hit.dealtPositiveDamage()
                || !(hit.attacker() instanceof ServerPlayer player)) {
            return;
        }
        ElfBladeLeafSkillHandler.applyLeafMark(
                player,
                hit.target(),
                hit.gameTick()
        );
    }

    static void startTemperedBilletFireRing(ResolvedWeaponHit hit) {
        if (!"tempered_billet".equals(hit.skillId())
                || !hit.dealtPositiveDamage()
                || !(hit.attacker() instanceof ServerPlayer player)) {
            return;
        }
        TemperedBilletSkillHandler.startFireRing(
                player,
                hit.target(),
                hit.gameTick(),
                hit.weaponSnapshot().orElse(null)
        );
    }

    static void applyClaymoreFoldbackReturn(ResolvedWeaponHit hit) {
        if (!"claymore_foldback_return".equals(hit.skillId())
                || !hit.dealtPositiveDamage()) {
            return;
        }
        hit.target().addEffect(new MobEffectInstance(
                MobEffects.DIG_SLOWDOWN,
                ClaymoreFoldbackSkillHandler.SLOW_DURATION_TICKS,
                ClaymoreFoldbackSkillHandler.SLOW_AMPLIFIER,
                false,
                true,
                true
        ));
    }

    static void applyFishcatchThrust(ResolvedWeaponHit hit) {
        if (!"fishcatch_thrust".equals(hit.skillId())
                || !hit.dealtPositiveDamage()
                || !(hit.attacker() instanceof ServerPlayer player)) {
            return;
        }
        FishcatchThrustSkillHandler.onAppliedHit(
                player,
                hit.target(),
                hit.gameTick()
        );
    }

    static void applyDwarfDaggerThrust(ResolvedWeaponHit hit) {
        if (!"dwarf_dagger_thrust".equals(hit.skillId())
                || !hit.dealtPositiveDamage()
                || !(hit.attacker() instanceof ServerPlayer player)) {
            return;
        }
        DwarfDaggerThrustSkillHandler.onAppliedHit(
                player,
                hit.target(),
                hit.gameTick(),
                hit.weaponIdentity().logicId(),
                hit.skillId()
        );
    }

    static void applyCarvingThrust(ResolvedWeaponHit hit) {
        boolean baseStrike = "carving_thrust".equals(hit.skillId());
        boolean bonusStrike = "carving_thrust_bonus".equals(hit.skillId());
        if ((!baseStrike && !bonusStrike)
                || !hit.dealtPositiveDamage()
                || !(hit.attacker() instanceof ServerPlayer player)) {
            return;
        }
        PacketDistributor.sendToPlayer(
                player,
                new CarvingKnifeThrustStrikePayload()
        );
        if (baseStrike && hit.damageOutcome().isCrit()) {
            CarvingThrustSkillHandler.recordCriticalHit(
                    player,
                    hit.target()
            );
        }
    }

    static void applyFemurSlam(ResolvedWeaponHit hit) {
        if (!"femur_slam".equals(hit.skillId())
                || !hit.dealtPositiveDamage()
                || !(hit.attacker() instanceof ServerPlayer player)) {
            return;
        }
        FemurSlamSkillHandler.recordAppliedHit(player, hit.target());
    }

    static void applyDwarfRuneGuard(ResolvedWeaponHit hit) {
        if (!"dwarf_rune_guard".equals(hit.skillId())
                || !hit.dealtPositiveDamage()
                || !(hit.attacker() instanceof ServerPlayer player)) {
            return;
        }
        DwarfRuneGuardSkillHandler.onAppliedHit(player, hit.target());
    }

    static void applyForestBlessing(ResolvedWeaponHit hit) {
        if (!"forest_blessing".equals(hit.skillId())
                || !hit.dealtPositiveDamage()
                || !(hit.attacker() instanceof ServerPlayer player)) {
            return;
        }
        ForestBlessingSkillHandler.recordAppliedHit(player);
    }

    static void applyWindSpire(ResolvedWeaponHit hit) {
        if (!"wind_spire_thrust".equals(hit.skillId())
                || !hit.dealtPositiveDamage()
                || !(hit.attacker() instanceof ServerPlayer player)) {
            return;
        }
        WindSpireThrustSkillHandler.grantGale(player, hit.gameTick());
    }

    static void applyWickedVenomRipple(ResolvedWeaponHit hit) {
        if (!"wicked_kris_venom_ripple".equals(hit.skillId())
                || !hit.dealtPositiveDamage()
                || !(hit.attacker() instanceof ServerPlayer player)) {
            return;
        }
        WickedKrisPoisonTracker.applyPoison(
                hit.target(),
                player,
                hit.gameTick(),
                WickedKrisVenomRippleSkillHandler.POISON_DURATION_TICKS,
                WickedKrisVenomRippleSkillHandler.POISON_STACKS,
                WickedKrisVenomRippleSkillHandler.SCHEDULE_DETONATION,
                hit.weaponSnapshot().orElseThrow()
        );
        WickedKrisVenomRippleSkillHandler.recordAppliedHit(player);
    }

    static void applyWickedNestBurst(ResolvedWeaponHit hit) {
        if (!"wicked_kris_nest_burst".equals(hit.skillId())
                || !hit.dealtPositiveDamage()
                || !(hit.attacker() instanceof ServerPlayer player)) {
            return;
        }
        WickedKrisPoisonTracker.applyPoison(
                hit.target(),
                player,
                hit.gameTick(),
                WickedKrisNestBurstSkillHandler.POISON_DURATION_TICKS,
                WickedKrisNestBurstSkillHandler.POISON_STACKS,
                WickedKrisNestBurstSkillHandler.SCHEDULE_DETONATION,
                hit.weaponSnapshot().orElseThrow()
        );
    }

    static void applyShadowExecute(ResolvedWeaponHit hit) {
        if (!"shadow_dagger_execute".equals(hit.skillId())
                || !hit.dealtPositiveDamage()
                || !(hit.attacker() instanceof ServerPlayer player)) {
            return;
        }
        ShadowDaggerExecuteSkillHandler.onAppliedRootHit(
                player,
                hit.target(),
                hit.gameTick(),
                hit.weaponSnapshot().orElseThrow()
        );
    }

    static void applyTemperedQuenchBlast(ResolvedWeaponHit hit) {
        if (!"tempered_quench_blast".equals(hit.skillId())
                || !hit.dealtPositiveDamage()) {
            return;
        }
        hit.target().addEffect(new MobEffectInstance(
                ModMobEffects.VULNERABLE,
                TemperedQuenchSkillHandler.VULNERABLE_DURATION_TICKS,
                TemperedQuenchSkillHandler.VULNERABLE_AMPLIFIER,
                false,
                true,
                true
        ));
    }

    static void applyDragonBreathThrust(ResolvedWeaponHit hit) {
        if (!"dragon_breath_thrust".equals(hit.skillId())
                || !hit.dealtPositiveDamage()) {
            return;
        }
        EquipmentNegativeStatusProtection.Decision protection =
                EquipmentNegativeStatusProtection.decide(
                        hit.target(),
                        DragonBreathThrustSkillHandler
                                .VULNERABLE_DURATION_TICKS
                );
        if (protection.resisted()) {
            return;
        }
        EquipmentMobEffectHandler.addPreAdjustedEffect(
                hit.target(),
                new MobEffectInstance(
                        ModMobEffects.VULNERABLE,
                        protection.durationTicks(),
                        DragonBreathThrustSkillHandler
                                .VULNERABLE_AMPLIFIER,
                        false,
                        true,
                        true
                )
        );
        YetiFreezeTracker.applyPreAdjusted(
                hit.target(),
                hit.gameTick(),
                protection.adjustRelatedDurationTicks(
                        DragonBreathThrustSkillHandler
                                .STAGGER_DURATION_TICKS
                ),
                YetiFreezeTracker.PresentationPolicy.SERVER_ONLY_STAGGER
        );
    }

    static void recordDragonBreathJudgement(ResolvedWeaponHit hit) {
        if (!"dragon_breath_judgement".equals(hit.skillId())
                || !hit.dealtPositiveDamage()
                || !(hit.attacker() instanceof ServerPlayer player)) {
            return;
        }
        DragonBreathJudgementSkillHandler.recordAppliedHit(
                player,
                hit.target()
        );
    }

    static void settleSingularityEvolveRewards(ResolvedWeaponHit hit) {
        if (!"singularity_evolve".equals(hit.skillId())
                || !hit.dealtPositiveDamage()
                || !(hit.attacker() instanceof ServerPlayer player)) {
            return;
        }
        SingularityEvolveSkillHandler.settleAppliedHitRewards(player);
    }

    static void settleStartrailRiftRewards(ResolvedWeaponHit hit) {
        if (!"startrail_rift".equals(hit.skillId())
                || !hit.dealtPositiveDamage()
                || !(hit.attacker() instanceof ServerPlayer player)) {
            return;
        }
        StartrailRiftSkillHandler.settleAppliedHitRewards(
                player,
                hit.target()
        );
    }

    static void recordInfinityDaggerSingularityBackstab(
            ResolvedWeaponHit hit
    ) {
        if (!"infinity_dagger_singularity_backstab".equals(hit.skillId())
                || !hit.dealtPositiveDamage()
                || !(hit.attacker() instanceof ServerPlayer player)) {
            return;
        }
        InfinityDaggerSingularityBackstabSkillHandler.recordAppliedHit(
                player,
                hit.target()
        );
    }

    static void applyGalaxyDaggerFinalMark(ResolvedWeaponHit hit) {
        if (!"galaxy_dagger_starstab".equals(hit.skillId())
                || !hit.dealtPositiveDamage()
                || !(hit.attacker() instanceof ServerPlayer player)) {
            return;
        }
        GalaxyDaggerStarstabSkillHandler.applyFinalStrikeMark(
                player,
                hit.target(),
                hit.gameTick()
        );
    }

    static void applyInfinityDaggerFinalMark(ResolvedWeaponHit hit) {
        if (!"infinity_dagger_singularity_stab".equals(hit.skillId())
                || !hit.dealtPositiveDamage()
                || !(hit.attacker() instanceof ServerPlayer player)) {
            return;
        }
        InfinityDaggerSingularityStabSkillHandler.applyFinalStrikeMark(
                player,
                hit.target(),
                hit.gameTick()
        );
    }

    static void applyHolySmite(ResolvedWeaponHit hit) {
        if (!"holy_smite".equals(
                        hit.authoredSkillContext().getSkillId()
                )
                || !hit.dealtPositiveDamage()
                || !(hit.attacker() instanceof ServerPlayer player)
                || !(hit.target().level() instanceof ServerLevel level)) {
            return;
        }
        HolyBladeEffects.playSmiteHit(level, hit.target());
        HolyBladeEffects.playHeal(player, HolySmiteSkillHandler.HEAL_AMOUNT);
        HolyBladeDodgeTracker.start(
                player,
                hit.gameTick(),
                HolySmiteSkillHandler.DODGE_DURATION_TICKS,
                HolySmiteSkillHandler.DODGE_CHANCE
        );
    }

    static void armTemperedQuench(ResolvedWeaponHit hit) {
        if (!"tempered_quench".equals(
                        hit.authoredSkillContext().getSkillId()
                )
                || !hit.dealtPositiveDamage()
                || !(hit.attacker() instanceof ServerPlayer player)) {
            return;
        }
        TemperedQuenchSkillHandler.armBlast(
                player,
                hit.target(),
                hit.gameTick(),
                TemperedQuenchSkillHandler.BLAST_DELAY_TICKS,
                hit.weaponSnapshot().orElseThrow()
        );
    }

    static void applyDarkSwordLifeSteal(ResolvedWeaponHit hit) {
        if (!hit.dealtPositiveDamage()
                || !(hit.attacker() instanceof ServerPlayer player)) {
            return;
        }
        float debtRatio = DarkSwordBloodDebtSkillHandler.getLifestealRatio(
                player,
                hit.gameTick()
        );
        float moonRatio = DarkSwordBloodMoonSkillHandler.getLifestealRatio(
                player,
                hit.gameTick()
        );
        float ratio = Math.max(debtRatio, moonRatio);
        if (ratio <= 0.0F) {
            return;
        }
        int heal = Math.max(1, Math.round(hit.appliedDamage() * ratio));
        float actualHeal = CombatHealing.heal(player, heal);
        if (actualHeal > 0.0F) {
            DarkSwordBloodMoonSkillHandler.recordLifeSteal(
                    player,
                    hit.gameTick(),
                    actualHeal
            );
            DarkSwordEffects.playLifeSteal(player);
        }
    }

    static void fireElfLeaf(ResolvedWeaponHit hit) {
        if (!hit.dealtPositiveDamage()
                || !hit.target().isAlive()
                || "elf_blade_leaf".equals(
                        hit.authoredSkillContext().getSkillId()
                )
                || !(hit.attacker() instanceof ServerPlayer player)) {
            return;
        }
        ElfBladeLeafSkillHandler.fireLeafAtTarget(
                player,
                hit.target(),
                hit.gameTick()
        );
    }

    static void applyInsectEyeStance(ResolvedWeaponHit hit) {
        if (!"insect_eye_stance".equals(hit.skillId())
                || !hit.dealtPositiveDamage()
                || !(hit.target().level() instanceof ServerLevel)) {
            return;
        }
        MobEffect vulnerable = Objects.requireNonNull(
                ModMobEffects.VULNERABLE.get(),
                "vulnerable"
        );
        Holder<MobEffect> vulnerableHolder = Holder.direct(vulnerable);
        hit.target().addEffect(new MobEffectInstance(
                vulnerableHolder,
                60,
                1,
                false,
                true,
                true
        ));
        LegacyWeaponHitPresentation.emitInsectEyeImpact(hit.target());
    }

    static void emitInsectDash(ResolvedWeaponHit hit) {
        if ("insect_dash".equals(hit.skillId())
                && hit.dealtPositiveDamage()) {
            if (hit.attacker() instanceof ServerPlayer player) {
                InsectDashSkillHandler.recordAppliedHit(
                        player,
                        hit.target()
                );
            }
            LegacyWeaponHitPresentation.emitInsectDashImpact(hit.target());
        }
    }

    static void emitIridiumNeedleThrustStrike(ResolvedWeaponHit hit) {
        if (!"iridium_needle_thrust".equals(hit.skillId())
                || !hit.dealtPositiveDamage()
                || !(hit.attacker() instanceof ServerPlayer player)) {
            return;
        }
        PacketDistributor.sendToPlayer(
                player,
                new IridiumNeedleThrustStrikePayload()
        );
    }

    static void applyYetiMark(ResolvedWeaponHit hit) {
        if (!YetiToothMarkTracker.SKILL_ID.equals(hit.skillId())
                || !hit.dealtPositiveDamage()
                || !(hit.attacker() instanceof ServerPlayer player)
                || !(hit.target().level() instanceof ServerLevel)) {
            return;
        }
        int markDuration = YetiToothMarkTracker.applyWithEquipmentProtection(
                hit.target(),
                player,
                hit.gameTick(),
                YetiToothMarkTracker.MARK_DURATION_TICKS
        );
        YetiToothEffects.applyPreAdjustedSlow(
                hit.target(),
                markDuration,
                YetiToothMarkTracker.SLOW_AMPLIFIER
        );
    }

    static void applyLavaBrand(ResolvedWeaponHit hit) {
        if (!"lava_katana_brand".equals(hit.skillId())
                || !hit.dealtPositiveDamage()
                || !(hit.attacker() instanceof ServerPlayer player)
                || !(hit.target().level() instanceof ServerLevel)) {
            return;
        }
        com.stardew.craft.combat.skill.LavaKatanaMarkTracker.apply(
                hit.target(),
                player,
                hit.gameTick(),
                com.stardew.craft.combat.skill.LavaKatanaMarkTracker.MARK_DURATION_TICKS
        );
    }
}
