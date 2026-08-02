package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.WeaponSkillAnimationDispatcher;
import com.stardew.craft.combat.skill.WeaponSkillAnimationLock;
import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.runtime.RuntimeWeaponSkillHandler;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTargeting;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import com.stardew.craft.combat.skill.runtime.SkillValidation;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;

/**
 * Server-authoritative lifecycle for Iridium Needle's original Triple Needle Slash.
 */
public final class IridiumNeedleThrustSkillHandler implements RuntimeWeaponSkillHandler {
    public static final double INITIAL_TARGET_RANGE = 2.5;
    public static final int ANIMATION_TICKS = 18;
    public static final int STRIKE_COUNT = 3;
    public static final int STRIKE_INTERVAL_TICKS = 3;
    public static final double RETARGET_RANGE = 2.5;
    public static final int HIT_CONTEXT_LIFETIME_TICKS = 5;

    @Override
    public SkillValidation validate(SkillExecutionContext context) {
        if (WeaponSkillCooldowns.isOnCooldown(
                context.player(),
                context.weaponId().getPath(),
                context.skillData().getId(),
                context.nowTick()
        )) {
            return SkillValidation.reject(SkillValidation.RejectionReason.COOLDOWN);
        }
        if (WeaponSkillRuntime.hasActive(
                context.player().getUUID(),
                context.skillId()
        )) {
            return SkillValidation.reject(
                    SkillValidation.RejectionReason.INVALID_STATE
            );
        }
        return findInitialTarget(context) == null
                ? SkillValidation.reject(SkillValidation.RejectionReason.INVALID_STATE)
                : SkillValidation.accept();
    }

    @Override
    public void begin(SkillExecutionContext context, SkillInstance instance) {
        LivingEntity target = findInitialTarget(context);
        if (target == null) {
            throw new IllegalStateException(
                    "Triple Needle Slash target disappeared after validation"
            );
        }

        String weaponId = context.weaponId().getPath();
        String skillId = context.skillData().getId();
        instance.setTargetEntityIds(List.of(target.getId()));
        instance.initializeExecutionState(
                new IridiumNeedleThrustExecutionState(
                        context.nowTick(),
                        target.getUUID()
                )
        );

        WeaponSkillRuntime.commitCooldown(
                context,
                instance,
                context.skillData().getCooldown() * 20
        );
        WeaponSkillAnimationLock.setLock(
                context.player(),
                context.nowTick(),
                ANIMATION_TICKS
        );
        WeaponSkillAnimationDispatcher.sendSkillAnim(
                context.player(),
                weaponId,
                skillId,
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
        return instance.requireExecutionState(
                IridiumNeedleThrustExecutionState.class
        ).advance(context);
    }

    private static LivingEntity findInitialTarget(SkillExecutionContext context) {
        return SkillTargeting.findNearestTargetInFront(
                context.player(),
                INITIAL_TARGET_RANGE
        );
    }
}
