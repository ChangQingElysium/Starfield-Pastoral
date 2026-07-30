package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.CombatHealing;
import com.stardew.craft.combat.skill.SingularityEvolveTracker;
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
import com.stardew.craft.player.PlayerStardewDataAPI;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Server-authoritative lifecycle for Infinity Blade's original Singularity
 * Evolution.
 */
public final class SingularityEvolveSkillHandler
        implements RuntimeWeaponSkillHandler {
    public static final int HIT_SINGULARITY_RESTORE = 4;
    public static final float HIT_ENERGY_RESTORE = 10.0F;
    public static final float HIT_HEALTH_RESTORE = 5.0F;
    public static final int ANIMATION_TICKS = 8;

    @Override
    public SkillValidation validate(SkillExecutionContext context) {
        if (WeaponSkillRuntime.hasActive(
                context.player().getUUID(),
                context.skillId()
        ) || SingularityEvolveTracker.hasState(
                context.player().getUUID()
        )) {
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
        List<LivingEntity> rewardTargets =
                findRewardTargets(context);
        instance.setTargetEntityIds(
                rewardTargets.stream()
                        .map(LivingEntity::getId)
                        .toList()
        );
        boolean evolved = evolvedForStacks(
                SingularityTracker.getStacks(context.player())
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
        SingularityEvolveTracker.start(
                context.player(),
                context.nowTick(),
                SingularityEvolveTracker.ACTIVE_DURATION_TICKS,
                SingularityEvolveTracker.EFFECT_RADIUS,
                SingularityEvolveTracker
                        .EXPLOSION_DAMAGE_MULTIPLIER,
                SingularityEvolveTracker.SLASH_DAMAGE_MULTIPLIER,
                skillId,
                evolved,
                context.weaponSnapshot()
        );

        if (grantsHitRewards(rewardTargets.size())) {
            SingularityTracker.addStacks(
                    context.player(),
                    HIT_SINGULARITY_RESTORE
            );
            PlayerStardewDataAPI.restoreEnergy(
                    context.player(),
                    HIT_ENERGY_RESTORE
            );
            CombatHealing.heal(
                    context.player(),
                    HIT_HEALTH_RESTORE
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
        return switch (SingularityEvolveTracker.tick(
                context.player(),
                context.nowTick()
        )) {
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
        SingularityEvolveTracker.cancel(context.player());
    }

    static boolean grantsHitRewards(int targetCount) {
        return targetCount > 0;
    }

    static boolean evolvedForStacks(int stacks) {
        return Math.max(0, stacks)
                >= SingularityTracker.EVOLVE_THRESHOLD;
    }

    private static List<LivingEntity> findRewardTargets(
            SkillExecutionContext context
    ) {
        Vec3 origin = context.player().position();
        double radius = SingularityEvolveTracker.EFFECT_RADIUS;
        AABB box = context.player().getBoundingBox().inflate(
                radius,
                radius * 0.75D,
                radius
        );
        return List.copyOf(
                context.player().level().getEntitiesOfClass(
                        LivingEntity.class,
                        box,
                        entity -> entity.isPickable()
                                && entity != context.player()
                                && entity.distanceToSqr(
                                        origin.x,
                                        origin.y,
                                        origin.z
                                ) <= radius * radius
                )
        );
    }
}
