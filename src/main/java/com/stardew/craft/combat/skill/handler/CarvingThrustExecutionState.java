package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.WeaponDamageSnapshot;
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

/** One Carving Thrust execution with the authored observe-then-advance tail. */
final class CarvingThrustExecutionState
        implements SkillInstance.ExecutionState {
    private long nextTick;
    private int remainingStrikes = CarvingThrustSkillHandler.STRIKE_COUNT;
    private boolean bonusPending;
    private boolean bonusDone;
    private UUID targetId;
    private Phase phase = Phase.STRIKING;

    CarvingThrustExecutionState(long nowTick, UUID targetId) {
        this.nextTick = nowTick;
        this.targetId = targetId;
    }

    SkillTickResult advance(SkillExecutionContext context) {
        if (phase == Phase.SETTLED) {
            return SkillTickResult.COMPLETE;
        }
        if (context.nowTick() < nextTick) {
            return SkillTickResult.CONTINUE;
        }

        ServerPlayer player = context.player();
        if (!(player.level() instanceof ServerLevel level)) {
            return settleAfterObservedTick();
        }
        LivingEntity target = resolveTarget(level, player, targetId);
        if (target == null) {
            return settleAfterObservedTick();
        }

        if (remainingStrikes <= 0) {
            if (!bonusDone && bonusPending) {
                targetId = target.getUUID();
                strike(
                        context,
                        target,
                        CarvingThrustSkillHandler.BONUS_DAMAGE_MULTIPLIER,
                        true
                );
                nextTick = context.nowTick()
                        + CarvingThrustSkillHandler.BONUS_DELAY_TICKS;
                bonusPending = false;
                bonusDone = true;
                targetId = target.getUUID();
                return SkillTickResult.CONTINUE;
            }
            return settleAfterObservedTick();
        }

        targetId = target.getUUID();
        strike(
                context,
                target,
                CarvingThrustSkillHandler.BASE_DAMAGE_MULTIPLIER,
                false
        );
        remainingStrikes--;
        nextTick = context.nowTick()
                + CarvingThrustSkillHandler.STRIKE_INTERVAL_TICKS;
        if (remainingStrikes <= 0 && bonusPending) {
            nextTick = context.nowTick()
                    + CarvingThrustSkillHandler.BONUS_DELAY_TICKS;
        }
        targetId = target.getUUID();
        return SkillTickResult.CONTINUE;
    }

    private SkillTickResult settleAfterObservedTick() {
        phase = Phase.SETTLED;
        return SkillTickResult.CONTINUE;
    }

    private static void strike(
            SkillExecutionContext executionContext,
            LivingEntity target,
            float damageMultiplier,
            boolean bonusStrike
    ) {
        ServerPlayer player = executionContext.player();
        String skillId = bonusStrike
                ? "carving_thrust_bonus"
                : executionContext.skillData().getId();
        SkillContext hitContext = SkillContext.builder()
                .skillId(skillId)
                .tier(SkillContext.SkillTier.MINOR)
                .damageMultiplier(damageMultiplier)
                .build();
        WeaponDamageSnapshot weaponSnapshot =
                executionContext.weaponSnapshot();
        WeaponSkillDamage.apply(
                player,
                target,
                hitContext,
                weaponSnapshot,
                executionContext.nowTick()
                        + CarvingThrustSkillHandler
                                .HIT_CONTEXT_LIFETIME_TICKS,
                WeaponSkillDamage.AttackGatePolicy.RESPECT_AT_IMPACT,
                WeaponSkillDamage.HitCooldownPolicy
                        .BYPASS_FOR_AUTHORED_SEQUENCE
        );
    }

    boolean recordCriticalHit(LivingEntity target) {
        if (phase != Phase.STRIKING
                || target == null
                || !target.getUUID().equals(targetId)) {
            return false;
        }
        bonusPending = true;
        return true;
    }

    private static LivingEntity resolveTarget(
            ServerLevel level,
            ServerPlayer player,
            UUID targetId
    ) {
        if (targetId != null) {
            Entity entity = level.getEntity(targetId);
            if (entity instanceof LivingEntity living && living.isAlive()) {
                return living;
            }
        }
        return findTargetInFront(
                player,
                CarvingThrustSkillHandler.REACQUIRE_RANGE
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

    private enum Phase {
        STRIKING,
        SETTLED
    }
}
