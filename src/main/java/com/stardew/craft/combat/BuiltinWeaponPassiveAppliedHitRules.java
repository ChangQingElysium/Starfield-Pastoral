package com.stardew.craft.combat;

import com.stardew.craft.combat.network.CrystalDaggerBurstPayload;
import com.stardew.craft.combat.skill.CrystalDaggerLayerTracker;
import com.stardew.craft.combat.skill.GalaxyDaggerMarkTracker;
import com.stardew.craft.combat.skill.InfinityDaggerMarkTracker;
import com.stardew.craft.combat.skill.DragonBreathTracker;
import com.stardew.craft.combat.skill.IridiumNeedleCritTracker;
import com.stardew.craft.combat.skill.ObsidianResonanceTracker;
import com.stardew.craft.combat.skill.OssifiedMarkTracker;
import com.stardew.craft.combat.skill.SingularityTracker;
import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.StartrailTracker;
import com.stardew.craft.combat.skill.TideMarkTracker;
import com.stardew.craft.combat.skill.WeaponDamageSnapshot;
import com.stardew.craft.combat.skill.WeaponSkillDamage;
import com.stardew.craft.combat.skill.YetiToothEffects;
import com.stardew.craft.combat.skill.YetiToothMarkTracker;
import com.stardew.craft.combat.skill.handler.IridiumNeedleFrenzySkillHandler;
import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.item.weapon.IStardewWeapon;
import com.stardew.craft.player.PlayerStardewDataAPI;
import java.util.Objects;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** Applied-hit rules owned by built-in weapon resources and entity marks. */
final class BuiltinWeaponPassiveAppliedHitRules {
    private static final long DEFAULT_CHILD_CONTEXT_TICKS = 5L;

    private BuiltinWeaponPassiveAppliedHitRules() {
    }

    static void applyIridiumNeedle(ResolvedWeaponHit hit) {
        if (!hit.dealtPositiveDamage()
                || !(hit.attacker() instanceof ServerPlayer player)
                || !hit.weaponIdentity().builtIn()
                || !"iridium_needle".equals(
                        hit.weaponIdentity().logicId()
                )) {
            return;
        }
        IridiumNeedleCritTracker.recordHit(player);
        if (!IridiumNeedleFrenzySkillHandler.isActive(
                player,
                hit.gameTick()
        ) || !hit.damageOutcome().isCrit()) {
            return;
        }
        CombatHealing.heal(
                player,
                IridiumNeedleFrenzySkillHandler.CRITICAL_HEAL_AMOUNT
        );
        PlayerStardewDataAPI.restoreEnergy(
                player,
                IridiumNeedleFrenzySkillHandler.CRITICAL_ENERGY_RESTORE
        );
        MobEffect vulnerable = Objects.requireNonNull(
                ModMobEffects.VULNERABLE.get(),
                "vulnerable"
        );
        Holder<MobEffect> vulnerableHolder = Holder.direct(vulnerable);
        hit.target().addEffect(new MobEffectInstance(
                vulnerableHolder,
                IridiumNeedleFrenzySkillHandler
                        .CRITICAL_VULNERABLE_DURATION_TICKS,
                IridiumNeedleFrenzySkillHandler
                        .CRITICAL_VULNERABLE_AMPLIFIER,
                false,
                true,
                true
        ));
    }

    static void addAppliedWeaponResources(ResolvedWeaponHit hit) {
        if (!hit.dealtPositiveDamage()
                || !(hit.attacker() instanceof ServerPlayer player)
                || !hit.weaponIdentity().builtIn()) {
            return;
        }
        String weaponId = hit.weaponIdentity().logicId();
        int stacks = hit.damageOutcome().isCrit() ? 3 : 1;
        if ("dragontooth_cutlass".equals(weaponId)
                && "normal".equals(
                        hit.authoredSkillContext().getSkillId()
                )) {
            DragonBreathTracker.addStacks(player, stacks);
        } else if ("galaxy_sword".equals(weaponId)) {
            StartrailTracker.addStacks(player, stacks);
        }
    }

    static void consumeObsidianResonance(ResolvedWeaponHit hit) {
        if (!hit.dealtPositiveDamage()
                || !"normal".equals(
                        hit.authoredSkillContext().getSkillId()
                )
                || !(hit.attacker() instanceof ServerPlayer player)
                || !hit.weaponIdentity().builtIn()
                || !"obsidian_edge".equals(
                        hit.weaponIdentity().logicId()
                )
                || !ObsidianResonanceTracker.consumeCharge(
                        player,
                        hit.gameTick()
                )) {
            return;
        }
        if (!hit.target().isAlive()) {
            return;
        }
        applyChildDamage(
                hit,
                ObsidianResonanceTracker.createBonusContext(
                        hit.damageOutcome().isCrit()
                ),
                ObsidianResonanceTracker.HIT_CONTEXT_LIFETIME_TICKS
        );
    }

    static void emitObsidianResonancePresentation(ResolvedWeaponHit hit) {
        if (!"obsidian_resonance".equals(hit.skillId())
                || !hit.dealtPositiveDamage()) {
            return;
        }
        LegacyWeaponHitPresentation.emitObsidianResonance(hit.target());
    }

    static void triggerCrystalBurst(ResolvedWeaponHit hit) {
        if (!hit.dealtPositiveDamage()
                || !(hit.attacker() instanceof ServerPlayer player)
                || !hit.weaponIdentity().builtIn()
                || !"crystal_dagger".equals(
                        hit.weaponIdentity().logicId()
                )) {
            return;
        }
        if ("normal".equals(
                hit.authoredSkillContext().getSkillId()
        )) {
            CrystalDaggerLayerTracker.addStack(player, hit.gameTick());
        }
        if (!CrystalDaggerLayerTracker.shouldBurst(
                player,
                hit.gameTick()
        )) {
            return;
        }
        CrystalDaggerLayerTracker.consumeBurst(player);
        if (!hit.target().isAlive()) {
            return;
        }
        applyChildDamage(
                hit,
                SkillContext.builder()
                        .skillId("crystal_dagger_burst")
                        .tier(SkillContext.SkillTier.MINOR)
                        .damageMultiplier(0.80F)
                        .build(),
                DEFAULT_CHILD_CONTEXT_TICKS
        );
    }

    static void emitCrystalDaggerBurstPresentation(ResolvedWeaponHit hit) {
        if (!"crystal_dagger_burst".equals(hit.skillId())
                || !hit.dealtPositiveDamage()
                || !(hit.attacker() instanceof ServerPlayer player)) {
            return;
        }
        PacketDistributor.sendToPlayer(
                player,
                new CrystalDaggerBurstPayload()
        );
    }

    static void triggerEvolvedSingularityFollowup(ResolvedWeaponHit hit) {
        if (!hit.dealtPositiveDamage()
                || !(hit.attacker() instanceof ServerPlayer player)
                || !hit.weaponIdentity().builtIn()
                || !"infinity_blade".equals(
                        hit.weaponIdentity().logicId()
                )) {
            return;
        }
        SingularityTracker.addStacks(
                player,
                hit.damageOutcome().isCrit() ? 3 : 1
        );
        if (!"normal".equals(
                        hit.authoredSkillContext().getSkillId()
                )
                || !SingularityTracker.isEvolved(player)
                || !hit.target().isAlive()) {
            return;
        }
        applyChildDamage(
                hit,
                SkillContext.builder()
                        .skillId("singularity_followup")
                        .tier(SkillContext.SkillTier.MINOR)
                        .damageMultiplier(0.30F)
                        .build(),
                DEFAULT_CHILD_CONTEXT_TICKS
        );
    }

    static void applyOssifiedCriticalMark(ResolvedWeaponHit hit) {
        String skillId = hit.skillId();
        boolean extraDamage = "ossified_mark_bonus".equals(skillId)
                || "ossified_execution_dot".equals(skillId);
        if (extraDamage
                || !hit.dealtPositiveDamage()
                || !hit.damageOutcome().isCrit()
                || !(hit.weapon().getItem() instanceof IStardewWeapon weapon)
                || !"ossified_blade".equals(weapon.getWeaponId())
                || !OssifiedMarkTracker.consumeBonusIfEligible(
                        hit.target(),
                        hit.attacker(),
                        hit.gameTick()
                )) {
            return;
        }
        applyChildDamage(
                hit,
                SkillContext.builder()
                        .skillId("ossified_mark_bonus")
                        .tier(SkillContext.SkillTier.MINOR)
                        .damageMultiplier(1.0F)
                        .build(),
                DEFAULT_CHILD_CONTEXT_TICKS
        );
        LegacyWeaponHitPresentation.emitOssifiedMarkBonus(hit.target());
    }

    static void applyYetiFollowup(ResolvedWeaponHit hit) {
        if (!hit.dealtPositiveDamage()
                || !(hit.attacker() instanceof ServerPlayer player)
                || !(hit.target().level() instanceof ServerLevel serverLevel)
                || !YetiToothMarkTracker.isEligibleFollowupSkill(hit.skillId())
                || !YetiToothMarkTracker.consumeIfEligible(
                        hit.target(),
                        player,
                        hit.gameTick()
                )) {
            return;
        }
        YetiToothEffects.applyFreeze(
                serverLevel,
                hit.target(),
                YetiToothMarkTracker.FREEZE_DURATION_TICKS
        );
    }

    static void applyLavaHeat(ResolvedWeaponHit hit) {
        if (!hit.dealtPositiveDamage()
                || !(hit.attacker() instanceof ServerPlayer player)
                || !(hit.weapon().getItem() instanceof IStardewWeapon weapon)
                || !"lava_katana".equals(weapon.getWeaponId())) {
            return;
        }
        String skillId = hit.skillId();
        if ("lava_katana_burn".equals(skillId)
                || "lava_katana_finisher".equals(skillId)
                || "lava_katana_brand".equals(skillId)
                || !com.stardew.craft.combat.skill.LavaKatanaMarkTracker
                        .isMarkedBy(hit.target(), player, hit.gameTick())) {
            return;
        }
        com.stardew.craft.combat.skill.LavaKatanaMarkTracker
                .addHeatIfEligible(
                        hit.target(),
                        player,
                        hit.gameTick(),
                        1
                );
    }

    static void applyGalaxyMark(ResolvedWeaponHit hit) {
        if (!hit.dealtPositiveDamage()
                || "galaxy_dagger_mark_bonus".equals(hit.skillId())
                || !(hit.attacker() instanceof ServerPlayer player)
                || !GalaxyDaggerMarkTracker.consumeIfEligible(
                        hit.target(),
                        player,
                        hit.gameTick()
                )) {
            return;
        }
        applyChildDamage(
                hit,
                SkillContext.builder()
                        .skillId("galaxy_dagger_mark_bonus")
                        .tier(SkillContext.SkillTier.MINOR)
                        .damageMultiplier(0.80F)
                        .build(),
                DEFAULT_CHILD_CONTEXT_TICKS
        );
        LegacyWeaponHitPresentation.emitGalaxyMarkBonus(hit.target());
    }

    static void applyInfinityMark(ResolvedWeaponHit hit) {
        String skillId = hit.skillId();
        if (!hit.dealtPositiveDamage()
                || "infinity_dagger_mark_bonus".equals(skillId)
                || "infinity_dagger_singularity_backstab".equals(skillId)
                || !(hit.attacker() instanceof ServerPlayer player)
                || !InfinityDaggerMarkTracker.consumeIfEligible(
                        hit.target(),
                        player,
                        hit.gameTick()
                )) {
            return;
        }
        applyChildDamage(
                hit,
                SkillContext.builder()
                        .skillId("infinity_dagger_mark_bonus")
                        .tier(SkillContext.SkillTier.MINOR)
                        .damageMultiplier(1.20F)
                        .build(),
                DEFAULT_CHILD_CONTEXT_TICKS
        );
        LegacyWeaponHitPresentation.emitInfinityMarkBonus(hit.target());
    }

    static void applyTideMark(ResolvedWeaponHit hit) {
        if (!hit.dealtPositiveDamage()
                || TideMarkTracker.BONUS_SKILL_ID.equals(hit.skillId())
                || !(hit.weapon().getItem() instanceof IStardewWeapon weapon)
                || !"neptunes_glaive".equals(weapon.getWeaponId())
                || !TideMarkTracker.isMarkedBy(
                        hit.target(),
                        hit.attacker(),
                        hit.gameTick()
                )) {
            return;
        }
        applyChildDamage(
                hit,
                TideMarkTracker.createBonusContext(),
                TideMarkTracker.HIT_CONTEXT_LIFETIME_TICKS
        );
    }

    private static void applyChildDamage(
            ResolvedWeaponHit hit,
            SkillContext context,
            long contextLifetimeTicks
    ) {
        LivingEntity target = hit.target();
        WeaponDamageSnapshot snapshot = hit.weaponSnapshot().orElseThrow();
        WeaponSkillDamage.apply(
                hit.attacker(),
                target,
                context,
                snapshot,
                hit.gameTick() + contextLifetimeTicks,
                WeaponSkillDamage.AttackGatePolicy.SKILL_DAMAGE,
                WeaponSkillDamage.HitCooldownPolicy
                        .BYPASS_FOR_AUTHORED_SEQUENCE
        );
    }
}
