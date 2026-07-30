package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.CombatHealing;
import com.stardew.craft.combat.network.DarkSwordBloodDebtPayload;
import com.stardew.craft.combat.skill.DarkSwordBloodDebtTracker;
import com.stardew.craft.combat.skill.DarkSwordEffects;
import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.WeaponSkillAnimationDispatcher;
import com.stardew.craft.combat.skill.WeaponSkillAnimationLock;
import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.WeaponSkillDamage;
import com.stardew.craft.combat.skill.runtime.RuntimeWeaponSkillHandler;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTargeting;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import com.stardew.craft.combat.skill.runtime.SkillValidation;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import com.stardew.craft.item.weapon.WeaponSkillData;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server-authoritative lifecycle for Dark Sword's original Blood Debt.
 */
public final class DarkSwordBloodDebtSkillHandler implements RuntimeWeaponSkillHandler {
    public static final double TARGET_RANGE = 4.0;
    public static final float CURRENT_HEALTH_COST_RATIO = 0.06F;
    public static final float MINIMUM_HEALTH_COST = 1.0F;
    public static final float MINIMUM_REMAINING_HEALTH = 1.0F;
    public static final int HIT_CONTEXT_LIFETIME_TICKS = 5;
    public static final int ANIMATION_TICKS = 8;

    @Override
    public SkillValidation validate(SkillExecutionContext context) {
        if (WeaponSkillCooldowns.isOnCooldown(
                context.player(),
                context.weaponId().getPath(),
                context.skillData().getId(),
                context.nowTick()
        )) {
            return SkillValidation.reject(
                    SkillValidation.RejectionReason.COOLDOWN
            );
        }
        return WeaponSkillRuntime.hasActive(
                context.player().getUUID(),
                context.skillId()
        ) || DarkSwordBloodDebtTracker.isTracked(context.player())
                ? SkillValidation.reject(
                        SkillValidation.RejectionReason.INVALID_STATE
                )
                : SkillValidation.accept();
    }

    @Override
    public void begin(SkillExecutionContext context, SkillInstance instance) {
        float cost = healthCost(CombatHealing.currentHealth(context.player()));
        CombatHealing.spendNonlethal(
                context.player(),
                cost,
                MINIMUM_REMAINING_HEALTH
        );

        String weaponId = context.weaponId().getPath();
        String skillId = context.skillData().getId();
        DarkSwordBloodDebtTracker.start(
                context.player(),
                context.nowTick(),
                DarkSwordBloodDebtTracker.ACTIVE_DURATION_TICKS
        );
        DarkSwordEffects.playBloodDebtCast(context.player());
        PacketDistributor.sendToPlayer(
                context.player(),
                new DarkSwordBloodDebtPayload(
                        true,
                        DarkSwordBloodDebtTracker.ACTIVE_DURATION_TICKS
                )
        );

        LivingEntity target = SkillTargeting.findTargetEntity(
                context.player(),
                TARGET_RANGE
        );
        if (target != null) {
            instance.setTargetEntityIds(List.of(target.getId()));
            WeaponSkillDamage.apply(
                    context.player(),
                    target,
                    createHitContext(context.skillData()),
                    context.weaponSnapshot(),
                    context.nowTick() + HIT_CONTEXT_LIFETIME_TICKS
            );
        }

        WeaponSkillAnimationDispatcher.sendSkillAnim(
                context.player(),
                weaponId,
                skillId,
                ANIMATION_TICKS
        );
        WeaponSkillAnimationLock.setLock(
                context.player(),
                context.nowTick(),
                ANIMATION_TICKS
        );
    }

    @Override
    public boolean completesImmediately() {
        return false;
    }

    @Override
    public SkillTickResult tick(
            SkillExecutionContext context,
            SkillInstance instance
    ) {
        DarkSwordBloodDebtTracker.tick(
                context.player(),
                context.nowTick()
        );
        return DarkSwordBloodDebtTracker.isTracked(context.player())
                ? SkillTickResult.CONTINUE
                : SkillTickResult.COMPLETE;
    }

    @Override
    public void finish(
            SkillExecutionContext context,
            SkillInstance instance,
            SkillInstance.EndReason reason
    ) {
        boolean stateWasActive =
                DarkSwordBloodDebtTracker.isTracked(context.player());
        DarkSwordBloodDebtTracker.cancel(context.player());
        if (shouldCommitCooldown(reason, stateWasActive)) {
            WeaponSkillCooldowns.setCooldown(
                    context.player(),
                    context.weaponId().getPath(),
                    context.skillData().getId(),
                    context.player().level().getGameTime(),
                    context.skillData().getCooldown() * 20
            );
        }
        if (reason != SkillInstance.EndReason.CASTER_UNAVAILABLE) {
            PacketDistributor.sendToPlayer(
                    context.player(),
                    new DarkSwordBloodDebtPayload(false, 0)
            );
        }
    }

    static boolean shouldCommitCooldown(
            SkillInstance.EndReason reason,
            boolean stateWasActive
    ) {
        return stateWasActive
                || reason == SkillInstance.EndReason.COMPLETED;
    }

    static float healthCost(float currentHealth) {
        return Math.max(
                MINIMUM_HEALTH_COST,
                Math.max(0.0F, currentHealth) * CURRENT_HEALTH_COST_RATIO
        );
    }

    static SkillContext createHitContext(WeaponSkillData skillData) {
        return SkillContext.builder()
                .skillId(skillData.getId())
                .tier(SkillContext.SkillTier.MINOR)
                .damageMultiplier(
                        skillData.getDamagePercent() / 100.0F
                )
                .build();
    }

}
