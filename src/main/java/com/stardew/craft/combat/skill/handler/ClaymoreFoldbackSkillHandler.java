package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.WeaponSkillAnimationDispatcher;
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

/**
 * Server-authoritative extraction of Claymore's original two-hit Foldback.
 */
public final class ClaymoreFoldbackSkillHandler
        implements RuntimeWeaponSkillHandler {
    public static final double INITIAL_TARGET_RANGE = 4.0D;
    public static final int RETURN_DELAY_TICKS = 12;
    public static final int HIT_CONTEXT_LIFETIME_TICKS = 5;
    public static final int INITIAL_ANIMATION_TICKS = 12;
    public static final double RETURN_TARGET_RANGE = 4.5D;
    public static final float RETURN_DAMAGE_MULTIPLIER = 1.2F;
    public static final int SLOW_DURATION_TICKS = 40;
    public static final int SLOW_AMPLIFIER = 0;
    public static final int RETURN_ANIMATION_TICKS = 12;

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
        )
                ? SkillValidation.reject(
                        SkillValidation.RejectionReason.INVALID_STATE
                )
                : SkillValidation.accept();
    }

    @Override
    public void begin(SkillExecutionContext context, SkillInstance instance) {
        String weaponId = context.weaponId().getPath();
        String skillId = context.skillData().getId();
        WeaponSkillRuntime.commitCooldown(
                context,
                instance,
                context.skillData().getCooldown() * 20
        );

        LivingEntity target = SkillTargeting.findTargetEntity(
                context.player(),
                INITIAL_TARGET_RANGE
        );
        if (target != null) {
            instance.setTargetEntityIds(List.of(target.getId()));
            instance.registerCommittedEffect(() ->
                    attackInitialTarget(context, target)
            );
        }

        instance.initializeExecutionState(
                new ClaymoreFoldbackExecutionState(
                        context.nowTick(),
                        RETURN_DELAY_TICKS,
                        target != null ? target.getUUID() : null
                )
        );

        // Preserve the original notification order after the first strike and
        // delayed return registration. This skill never imposed an attack lock.
        WeaponSkillAnimationDispatcher.sendSkillAnim(
                context.player(),
                weaponId,
                skillId,
                INITIAL_ANIMATION_TICKS
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
        return instance.requireExecutionState(
                ClaymoreFoldbackExecutionState.class
        ).advance(context);
    }

    static SkillContext createInitialContext(WeaponSkillData skillData) {
        return SkillContext.builder()
                .skillId(skillData.getId())
                .tier(SkillContext.SkillTier.MINOR)
                .damageMultiplier(
                        skillData.getDamagePercent() / 100.0F
                )
                .build();
    }

    private static void attackInitialTarget(
            SkillExecutionContext context,
            LivingEntity target
    ) {
        WeaponSkillDamage.apply(
                context.player(),
                target,
                createInitialContext(context.skillData()),
                context.weaponSnapshot(),
                context.nowTick() + HIT_CONTEXT_LIFETIME_TICKS,
                WeaponSkillDamage.AttackGatePolicy.RESPECT_AT_IMPACT,
                WeaponSkillDamage.HitCooldownPolicy.RESPECT_VANILLA
        );
    }
}
