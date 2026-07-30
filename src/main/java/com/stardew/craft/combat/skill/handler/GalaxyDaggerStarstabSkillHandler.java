package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.GalaxyDaggerThrustTracker;
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
 * Server-authoritative lifecycle for Galaxy Dagger's original Star Trail Pierce.
 */
public final class GalaxyDaggerStarstabSkillHandler
        implements RuntimeWeaponSkillHandler {
    public static final double INITIAL_TARGET_RANGE = 3.5D;
    public static final int INITIAL_ANIMATION_TICKS = 6;

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
        if (WeaponSkillRuntime.hasActive(
                context.player().getUUID(),
                context.skillId()
        ) || GalaxyDaggerThrustTracker.isActive(
                context.player().getUUID()
        )) {
            return SkillValidation.reject(
                    SkillValidation.RejectionReason.INVALID_STATE
            );
        }
        return findInitialTarget(context) == null
                ? SkillValidation.reject(
                        SkillValidation.RejectionReason.INVALID_STATE
                )
                : SkillValidation.accept();
    }

    @Override
    public void begin(SkillExecutionContext context, SkillInstance instance) {
        LivingEntity target = findInitialTarget(context);
        if (target == null) {
            throw new IllegalStateException(
                    "Star Trail Pierce target disappeared after validation"
            );
        }

        String weaponId = context.weaponId().getPath();
        String skillId = context.skillData().getId();
        instance.setTargetEntityIds(List.of(target.getId()));

        WeaponSkillCooldowns.setCooldown(
                context.player(),
                weaponId,
                skillId,
                context.nowTick(),
                context.skillData().getCooldown() * 20
        );
        GalaxyDaggerThrustTracker.start(
                context.player(),
                context.nowTick(),
                target,
                weaponId,
                skillId,
                context.skillData().getDamagePercent() / 100.0F
        );

        // Preserve the original activation notification order. Each strike
        // sends its own four-tick animation from the thrust tracker.
        WeaponSkillAnimationLock.setLock(
                context.player(),
                context.nowTick(),
                INITIAL_ANIMATION_TICKS
        );
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
        if (!GalaxyDaggerThrustTracker.isActive(
                context.player().getUUID()
        )) {
            return SkillTickResult.COMPLETE;
        }
        if (!context.player().isAlive()
                || context.player().isRemoved()
                || !GalaxyDaggerThrustTracker.isBoundToCurrentDimension(
                        context.player()
                )) {
            return SkillTickResult.CANCEL;
        }

        GalaxyDaggerThrustTracker.tick(
                context.player(),
                context.nowTick()
        );
        return GalaxyDaggerThrustTracker.isActive(
                context.player().getUUID()
        )
                ? SkillTickResult.CONTINUE
                : SkillTickResult.COMPLETE;
    }

    @Override
    public void finish(
            SkillExecutionContext context,
            SkillInstance instance,
            SkillInstance.EndReason reason
    ) {
        GalaxyDaggerThrustTracker.removePlayer(
                context.player().getUUID()
        );
    }

    private static LivingEntity findInitialTarget(
            SkillExecutionContext context
    ) {
        return SkillTargeting.findNearestTargetInFront(
                context.player(),
                INITIAL_TARGET_RANGE
        );
    }
}
