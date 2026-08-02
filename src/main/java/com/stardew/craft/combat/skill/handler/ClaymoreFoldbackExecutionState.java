package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.WeaponDamageSnapshot;
import com.stardew.craft.combat.skill.WeaponSkillAnimationDispatcher;
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

/** One Claymore Foldback execution's delayed return strike. */
final class ClaymoreFoldbackExecutionState
        implements SkillInstance.ExecutionState {
    private final long fireTick;
    private final UUID targetId;

    ClaymoreFoldbackExecutionState(
            long nowTick,
            int delayTicks,
            UUID targetId
    ) {
        this.fireTick = returnFireTick(nowTick, delayTicks);
        this.targetId = targetId;
    }

    SkillTickResult advance(SkillExecutionContext context) {
        if (shouldWait(fireTick, context.nowTick())) {
            return SkillTickResult.CONTINUE;
        }
        if (!(context.player().level() instanceof ServerLevel level)) {
            return SkillTickResult.COMPLETE;
        }

        LivingEntity target = resolveTarget(
                context.player(),
                level,
                targetId
        );
        if (target == null) {
            return SkillTickResult.COMPLETE;
        }

        WeaponDamageSnapshot weaponSnapshot = context.weaponSnapshot();
        WeaponSkillDamage.apply(
                context.player(),
                target,
                createReturnContext(),
                weaponSnapshot,
                context.nowTick()
                        + ClaymoreFoldbackSkillHandler
                                .HIT_CONTEXT_LIFETIME_TICKS,
                WeaponSkillDamage.AttackGatePolicy.RESPECT_AT_IMPACT,
                WeaponSkillDamage.HitCooldownPolicy
                        .BYPASS_FOR_AUTHORED_SEQUENCE
        );

        WeaponSkillAnimationDispatcher.sendSkillAnim(
                context.player(),
                context.weaponId().getPath(),
                "claymore_foldback_return",
                ClaymoreFoldbackSkillHandler.RETURN_ANIMATION_TICKS
        );
        return SkillTickResult.COMPLETE;
    }

    static long returnFireTick(long nowTick, int delayTicks) {
        return nowTick + Math.max(1, delayTicks);
    }

    static boolean shouldWait(long fireTick, long nowTick) {
        return nowTick < fireTick;
    }

    static boolean canReuseStoredTarget(
            boolean caster,
            boolean alive,
            boolean pickable
    ) {
        return !caster && alive && pickable;
    }

    static SkillContext createReturnContext() {
        return SkillContext.builder()
                .skillId("claymore_foldback_return")
                .tier(SkillContext.SkillTier.MINOR)
                .damageMultiplier(
                        ClaymoreFoldbackSkillHandler
                                .RETURN_DAMAGE_MULTIPLIER
                )
                .build();
    }

    private static LivingEntity resolveTarget(
            ServerPlayer player,
            ServerLevel level,
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
                ClaymoreFoldbackSkillHandler.RETURN_TARGET_RANGE
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
