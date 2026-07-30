package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.EternalCollapseTracker;
import com.stardew.craft.combat.skill.SingularityTracker;
import com.stardew.craft.combat.skill.WeaponSkillAnimationDispatcher;
import com.stardew.craft.combat.skill.WeaponSkillAnimationLock;
import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.runtime.RuntimeWeaponSkillHandler;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import com.stardew.craft.combat.skill.runtime.SkillValidation;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Server-authoritative lifecycle for Infinity Blade's original Eternal Collapse.
 */
public final class EternalCollapseSkillHandler
        implements RuntimeWeaponSkillHandler {
    public static final int ACTIVE_DURATION_TICKS = 70;
    public static final int BASE_STRIKE_COUNT = 6;
    public static final int STACKS_PER_EXTRA_STRIKE = 5;
    public static final int MAXIMUM_EXTRA_STRIKES = 4;
    public static final float CRITICAL_CHANCE_PER_EXTRA_STRIKE = 0.05F;
    public static final int FINAL_STRIKE_STACK_THRESHOLD = 16;
    public static final float FINAL_STRIKE_DAMAGE_MULTIPLIER = 3.0F;
    public static final double EFFECT_RADIUS = 4.0D;
    public static final int ANIMATION_TICKS = 12;

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
        ) || EternalCollapseTracker.hasState(
                context.player().getUUID()
        )) {
            return SkillValidation.reject(
                    SkillValidation.RejectionReason.INVALID_STATE
            );
        }
        return SkillValidation.accept();
    }

    @Override
    public void begin(SkillExecutionContext context, SkillInstance instance) {
        int consumedStacks =
                SingularityTracker.consumeAll(context.player());
        int extraStrikes = extraStrikesForStacks(consumedStacks);
        float critBonus =
                criticalChanceBonusForStacks(consumedStacks);
        boolean finalStrike =
                hasFinalStrikeForStacks(consumedStacks);

        String weaponId = context.weaponId().getPath();
        String skillId = context.skillData().getId();
        WeaponSkillCooldowns.setCooldown(
                context.player(),
                weaponId,
                skillId,
                context.nowTick(),
                context.skillData().getCooldown() * 20
        );

        List<LivingEntity> initialTargets = snapshotTargetsInRadius(
                context,
                EFFECT_RADIUS
        );
        instance.setTargetEntityIds(
                initialTargets.stream()
                        .map(LivingEntity::getId)
                        .toList()
        );
        Vec3 center = chooseCollapseCenter(
                context.player().position(),
                initialTargets
        );
        EternalCollapseTracker.start(
                context.player(),
                center,
                context.nowTick(),
                ACTIVE_DURATION_TICKS,
                BASE_STRIKE_COUNT + extraStrikes,
                EFFECT_RADIUS,
                context.skillData().getDamagePercent() / 100.0F,
                critBonus,
                finalStrike,
                FINAL_STRIKE_DAMAGE_MULTIPLIER,
                skillId,
                context.weaponSnapshot()
        );

        // Preserve the original order after resource commitment and field VFX.
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
        if (!EternalCollapseTracker.hasState(
                context.player().getUUID()
        )) {
            return SkillTickResult.COMPLETE;
        }
        EternalCollapseTracker.Status status =
                EternalCollapseTracker.tick(
                        context.player(),
                        context.nowTick()
                );
        return switch (status) {
            case ACTIVE -> SkillTickResult.CONTINUE;
            case COMPLETED -> SkillTickResult.COMPLETE;
            case INVALIDATED -> SkillTickResult.CANCEL;
        };
    }

    @Override
    public void finish(
            SkillExecutionContext context,
            SkillInstance instance,
            SkillInstance.EndReason reason
    ) {
        EternalCollapseTracker.cancel(context.player());
    }

    static int extraStrikesForStacks(int stacks) {
        return Math.min(
                MAXIMUM_EXTRA_STRIKES,
                Math.max(0, stacks) / STACKS_PER_EXTRA_STRIKE
        );
    }

    static int totalStrikeCountForStacks(int stacks) {
        return BASE_STRIKE_COUNT + extraStrikesForStacks(stacks);
    }

    static float criticalChanceBonusForStacks(int stacks) {
        return extraStrikesForStacks(stacks)
                * CRITICAL_CHANCE_PER_EXTRA_STRIKE;
    }

    static boolean hasFinalStrikeForStacks(int stacks) {
        return stacks >= FINAL_STRIKE_STACK_THRESHOLD;
    }

    private static List<LivingEntity> snapshotTargetsInRadius(
            SkillExecutionContext context,
            double range
    ) {
        Vec3 origin = context.player().position();
        AABB bounds = context.player().getBoundingBox()
                .inflate(range, range * 0.75D, range);
        return List.copyOf(
                context.player().level().getEntitiesOfClass(
                        LivingEntity.class,
                        bounds,
                        entity -> entity.isPickable()
                                && entity != context.player()
                                && entity.distanceToSqr(
                                        origin.x,
                                        origin.y,
                                        origin.z
                                ) <= range * range
                )
        );
    }

    private static Vec3 chooseCollapseCenter(
            Vec3 fallback,
            List<LivingEntity> targets
    ) {
        LivingEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (LivingEntity target : targets) {
            double distance =
                    target.position().distanceToSqr(fallback);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = target;
            }
        }
        return nearest == null ? fallback : nearest.position();
    }
}
