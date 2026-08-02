package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.GalaxyDaggerMarkTracker;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Server-authoritative lifecycle for Galaxy Dagger's original Star Trail Pierce.
 */
public final class GalaxyDaggerStarstabSkillHandler
        implements RuntimeWeaponSkillHandler {
    public static final double INITIAL_TARGET_RANGE = 3.5D;
    public static final int INITIAL_ANIMATION_TICKS = 6;
    public static final int STRIKE_COUNT = 3;
    public static final int STRIKE_INTERVAL_TICKS = 2;
    public static final int MARK_DURATION_TICKS = 60;
    public static final int STRIKE_ANIMATION_TICKS = 4;
    public static final int HIT_CONTEXT_LIFETIME_TICKS = 5;
    public static final double RETARGET_RANGE = 3.5D;

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
        instance.initializeExecutionState(
                new GalaxyDaggerThrustExecutionState(
                        context.nowTick(),
                        target.getUUID()
                )
        );

        WeaponSkillRuntime.commitCooldown(
                context,
                instance,
                context.skillData().getCooldown() * 20
        );
        // Preserve the original activation notification order. Each strike
        // sends its own four-tick animation from the execution state.
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
        return instance.requireExecutionState(
                GalaxyDaggerThrustExecutionState.class
        ).advance(context);
    }

    /** Applies the mark only for this cast's exact positive final strike. */
    public static boolean applyFinalStrikeMark(
            ServerPlayer player,
            LivingEntity target,
            long nowTick
    ) {
        if (player == null || target == null) {
            return false;
        }
        boolean claimed = WeaponSkillRuntime.activeExecutionState(
                player.getUUID(),
                BuiltinWeaponSkillHandlers.GALAXY_DAGGER_STARSTAB,
                GalaxyDaggerThrustExecutionState.class
        ).map(state -> state.consumeFinalStrikeCandidate(
                target.getUUID(),
                target.isAlive()
        )).orElse(false);
        if (!claimed) {
            return false;
        }
        GalaxyDaggerMarkTracker.apply(
                target,
                player,
                nowTick,
                MARK_DURATION_TICKS
        );
        return true;
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
