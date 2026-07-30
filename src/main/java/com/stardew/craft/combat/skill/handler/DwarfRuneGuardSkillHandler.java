package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.DwarfRuneGuardTracker;
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
import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.item.weapon.WeaponSkillData;
import com.stardew.craft.player.PlayerStardewDataAPI;
import java.util.List;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * Server-authoritative lifecycle for Dwarf Sword's original Rune Guard Slash.
 */
public final class DwarfRuneGuardSkillHandler
        implements RuntimeWeaponSkillHandler {
    public static final double TARGET_RANGE = 4.5D;
    public static final int SHELTER_DURATION_TICKS = 50;
    public static final int SHELTER_AMPLIFIER = 1;
    public static final int SLOW_DURATION_TICKS = 40;
    public static final int SLOW_AMPLIFIER = 0;
    public static final float HIT_ENERGY_RESTORE = 6.0F;
    public static final float MISS_ENERGY_RESTORE = 3.0F;
    public static final int HIT_CONTEXT_LIFETIME_TICKS = 5;
    public static final int ANIMATION_TICKS = 8;

    @Override
    public SkillValidation validate(SkillExecutionContext context) {
        if (WeaponSkillRuntime.hasActive(
                context.player().getUUID(),
                context.skillId()
        ) || DwarfRuneGuardTracker.hasState(context.player())) {
            return SkillValidation.reject(
                    SkillValidation.RejectionReason.INVALID_STATE
            );
        }
        return WeaponSkillCooldowns.isOnCooldown(
                context.player(),
                context.weaponId().getPath(),
                context.skillData().getId(),
                context.nowTick()
        )
                ? SkillValidation.reject(
                        SkillValidation.RejectionReason.COOLDOWN
                )
                : SkillValidation.accept();
    }

    @Override
    public void begin(
            SkillExecutionContext context,
            SkillInstance instance
    ) {
        LivingEntity target = SkillTargeting.findTargetEntity(
                context.player(),
                TARGET_RANGE
        );
        if (target != null) {
            instance.setTargetEntityIds(List.of(target.getId()));
        }

        context.player().addEffect(new MobEffectInstance(
                ModMobEffects.SHELTER,
                SHELTER_DURATION_TICKS,
                SHELTER_AMPLIFIER,
                false,
                true,
                true
        ));
        DwarfRuneGuardTracker.start(
                context.player(),
                context.nowTick(),
                SHELTER_DURATION_TICKS
        );

        String weaponId = context.weaponId().getPath();
        String skillId = context.skillData().getId();
        WeaponSkillCooldowns.setCooldown(
                context.player(),
                weaponId,
                skillId,
                context.nowTick(),
                context.skillData().getCooldown() * 20
        );

        if (target != null) {
            WeaponSkillDamage.apply(
                    context.player(),
                    target,
                    createHitContext(context.skillData()),
                    context.weaponSnapshot(),
                    context.nowTick() + HIT_CONTEXT_LIFETIME_TICKS
            );
            target.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN,
                    SLOW_DURATION_TICKS,
                    SLOW_AMPLIFIER,
                    false,
                    true,
                    true
            ));
        }
        PlayerStardewDataAPI.restoreEnergy(
                context.player(),
                energyRestoreForTarget(target != null)
        );

        // Preserve the authored notification and action-lock order.
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
        return switch (DwarfRuneGuardTracker.tick(
                context.player(),
                context.nowTick()
        )) {
            case ACTIVE -> SkillTickResult.CONTINUE;
            case EXPIRED -> SkillTickResult.COMPLETE;
            case INVALIDATED -> SkillTickResult.CANCEL;
        };
    }

    @Override
    public void finish(
            SkillExecutionContext context,
            SkillInstance instance,
            SkillInstance.EndReason reason
    ) {
        DwarfRuneGuardTracker.stop(context.player());
    }

    static float energyRestoreForTarget(boolean hasTarget) {
        return hasTarget ? HIT_ENERGY_RESTORE : MISS_ENERGY_RESTORE;
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
