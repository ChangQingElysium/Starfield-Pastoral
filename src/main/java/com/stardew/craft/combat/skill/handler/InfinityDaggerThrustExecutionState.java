package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.WeaponDamageSnapshot;
import com.stardew.craft.combat.skill.WeaponSkillAnimationDispatcher;
import com.stardew.craft.combat.skill.WeaponSkillAnimationLock;
import com.stardew.craft.combat.skill.WeaponSkillDamage;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** One Infinity Dagger Singularity Stab execution. */
final class InfinityDaggerThrustExecutionState
        implements SkillInstance.ExecutionState {
    private long nextTick;
    private int remainingStrikes =
            InfinityDaggerSingularityStabSkillHandler.STRIKE_COUNT;
    private UUID targetId;
    private UUID finalStrikeCandidateId;

    InfinityDaggerThrustExecutionState(long nowTick, UUID targetId) {
        this.nextTick = nowTick;
        this.targetId = targetId;
    }

    SkillTickResult advance(SkillExecutionContext context) {
        if (context.nowTick() < nextTick) {
            return SkillTickResult.CONTINUE;
        }
        if (!(context.player().level() instanceof ServerLevel level)) {
            return SkillTickResult.COMPLETE;
        }

        LivingEntity target = resolveTarget(
                level,
                context.player(),
                targetId
        );
        if (target == null || remainingStrikes <= 0) {
            return SkillTickResult.COMPLETE;
        }

        beginStrike(target.getUUID(), remainingStrikes == 1);
        try {
            strike(context, target);
        } finally {
            clearFinalStrikeCandidate();
        }
        remainingStrikes--;
        if (remainingStrikes <= 0) {
            return SkillTickResult.COMPLETE;
        }

        nextTick = nextStrikeTick(context.nowTick());
        targetId = target.getUUID();
        return SkillTickResult.CONTINUE;
    }

    static long nextStrikeTick(long nowTick) {
        return nowTick
                + InfinityDaggerSingularityStabSkillHandler
                        .STRIKE_INTERVAL_TICKS;
    }

    void beginStrike(UUID strikeTargetId, boolean finalStrike) {
        finalStrikeCandidateId = finalStrike ? strikeTargetId : null;
    }

    boolean consumeFinalStrikeCandidate(
            UUID appliedTargetId,
            boolean targetAlive
    ) {
        if (!targetAlive
                || finalStrikeCandidateId == null
                || !finalStrikeCandidateId.equals(appliedTargetId)) {
            return false;
        }
        finalStrikeCandidateId = null;
        return true;
    }

    void clearFinalStrikeCandidate() {
        finalStrikeCandidateId = null;
    }

    static boolean canReuseStoredTarget(
            boolean caster,
            boolean alive,
            boolean pickable
    ) {
        return !caster && alive && pickable;
    }

    static SkillContext createStrikeContext(
            String skillId,
            float damageMultiplier
    ) {
        return SkillContext.builder()
                .skillId(skillId)
                .tier(SkillContext.SkillTier.MINOR)
                .damageMultiplier(damageMultiplier)
                .guaranteedCrit(true)
                .build();
    }

    private static void strike(
            SkillExecutionContext context,
            LivingEntity target
    ) {
        WeaponSkillAnimationDispatcher.sendSkillAnim(
                context.player(),
                context.weaponId().getPath(),
                context.skillData().getId(),
                InfinityDaggerSingularityStabSkillHandler
                        .STRIKE_ANIMATION_TICKS
        );
        WeaponSkillAnimationLock.setLock(
                context.player(),
                context.nowTick(),
                InfinityDaggerSingularityStabSkillHandler
                        .STRIKE_ANIMATION_TICKS
        );
        WeaponDamageSnapshot weaponSnapshot = context.weaponSnapshot();
        WeaponSkillDamage.apply(
                context.player(),
                target,
                createStrikeContext(
                        context.skillData().getId(),
                        context.skillData().getDamagePercent() / 100.0F
                ),
                weaponSnapshot,
                context.nowTick()
                        + InfinityDaggerSingularityStabSkillHandler
                                .HIT_CONTEXT_LIFETIME_TICKS,
                WeaponSkillDamage.AttackGatePolicy.RESPECT_AT_IMPACT,
                WeaponSkillDamage.HitCooldownPolicy
                        .BYPASS_FOR_AUTHORED_SEQUENCE
        );
    }

    private static LivingEntity resolveTarget(
            ServerLevel level,
            ServerPlayer player,
            UUID targetId
    ) {
        if (targetId != null) {
            Entity entity = level.getEntity(targetId);
            if (entity instanceof LivingEntity living
                    && canReuseStoredTarget(
                            living == player,
                            living.isAlive(),
                            living.isPickable()
                    )) {
                return living;
            }
        }
        return findTargetInFront(
                player,
                InfinityDaggerSingularityStabSkillHandler.RETARGET_RANGE
        );
    }

    private static LivingEntity findTargetInFront(
            ServerPlayer player,
            double range
    ) {
        Vec3 eyePosition = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eyePosition.add(look.scale(range));
        AABB bounds = player.getBoundingBox()
                .expandTowards(look.scale(range))
                .inflate(1.0D);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(
                player.level(),
                player,
                eyePosition,
                end,
                bounds,
                entity -> entity instanceof LivingEntity
                        && entity.isPickable()
                        && entity != player
        );
        return hit != null ? (LivingEntity) hit.getEntity() : null;
    }
}
