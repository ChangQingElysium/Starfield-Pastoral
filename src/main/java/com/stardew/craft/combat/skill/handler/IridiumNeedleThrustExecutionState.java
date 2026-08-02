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

/** One Triple Needle Slash execution. */
final class IridiumNeedleThrustExecutionState
        implements SkillInstance.ExecutionState {
    private long nextTick;
    private int remainingStrikes =
            IridiumNeedleThrustSkillHandler.STRIKE_COUNT;
    private UUID targetId;
    private Phase phase = Phase.STRIKING;

    IridiumNeedleThrustExecutionState(long nowTick, UUID targetId) {
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
        if (target == null || remainingStrikes <= 0) {
            return settleAfterObservedTick();
        }

        strike(
                context,
                target,
                isGuaranteedCritStrike(remainingStrikes)
        );
        remainingStrikes--;
        nextTick = nextStrikeTick(context.nowTick());
        targetId = target.getUUID();
        return SkillTickResult.CONTINUE;
    }

    private SkillTickResult settleAfterObservedTick() {
        phase = Phase.SETTLED;
        return SkillTickResult.CONTINUE;
    }

    static boolean isGuaranteedCritStrike(int remainingStrikes) {
        return remainingStrikes <= 1;
    }

    static long nextStrikeTick(long nowTick) {
        return nowTick
                + IridiumNeedleThrustSkillHandler.STRIKE_INTERVAL_TICKS;
    }

    private static void strike(
            SkillExecutionContext executionContext,
            LivingEntity target,
            boolean guaranteedCrit
    ) {
        SkillContext hitContext = SkillContext.builder()
                .skillId(executionContext.skillData().getId())
                .tier(SkillContext.SkillTier.MINOR)
                .damageMultiplier(
                        executionContext.skillData().getDamagePercent()
                                / 100.0F
                )
                .guaranteedCrit(guaranteedCrit)
                .build();
        WeaponDamageSnapshot weaponSnapshot =
                executionContext.weaponSnapshot();
        WeaponSkillDamage.apply(
                executionContext.player(),
                target,
                hitContext,
                weaponSnapshot,
                executionContext.nowTick()
                        + IridiumNeedleThrustSkillHandler
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
            if (entity instanceof LivingEntity living && living.isAlive()) {
                return living;
            }
        }
        return findTargetInFront(
                player,
                IridiumNeedleThrustSkillHandler.RETARGET_RANGE
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
