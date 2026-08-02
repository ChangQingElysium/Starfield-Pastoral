package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.network.BrokenTridentThrustStrikePayload;
import com.stardew.craft.combat.skill.BrokenTridentCatchTracker;
import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.WeaponDamageSnapshot;
import com.stardew.craft.combat.skill.WeaponSkillDamage;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** One Fish Catch Thrust execution; Fish Catch itself remains cross-execution. */
final class FishcatchThrustExecutionState
        implements SkillInstance.ExecutionState {
    private long nextTick;
    private int remainingStrikes = FishcatchThrustSkillHandler.STRIKE_COUNT;
    private UUID targetId;
    private UUID pendingTargetId;
    private boolean pendingFishCatchActive;
    private Phase phase = Phase.STRIKING;

    FishcatchThrustExecutionState(long nowTick, UUID targetId) {
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

        strike(context, target);
        remainingStrikes--;
        nextTick = context.nowTick()
                + FishcatchThrustSkillHandler.STRIKE_INTERVAL_TICKS;
        targetId = target.getUUID();
        return SkillTickResult.CONTINUE;
    }

    private SkillTickResult settleAfterObservedTick() {
        phase = Phase.SETTLED;
        return SkillTickResult.CONTINUE;
    }

    private void strike(
            SkillExecutionContext executionContext,
            LivingEntity target
    ) {
        ServerPlayer player = executionContext.player();
        boolean fishCatchActive = BrokenTridentCatchTracker.isActive(
                player,
                executionContext.nowTick()
        );
        float multiplier = damageMultiplier(
                executionContext.skillData().getDamagePercent() / 100.0F,
                fishCatchActive
        );
        SkillContext hitContext = SkillContext.builder()
                .skillId(executionContext.skillData().getId())
                .tier(SkillContext.SkillTier.MINOR)
                .damageMultiplier(multiplier)
                .build();
        WeaponDamageSnapshot weaponSnapshot =
                executionContext.weaponSnapshot();
        beginStrike(target.getUUID(), fishCatchActive);
        try {
            WeaponSkillDamage.apply(
                    player,
                    target,
                    hitContext,
                    weaponSnapshot,
                    executionContext.nowTick()
                            + FishcatchThrustSkillHandler
                                    .HIT_CONTEXT_LIFETIME_TICKS,
                    WeaponSkillDamage.AttackGatePolicy.RESPECT_AT_IMPACT,
                    WeaponSkillDamage.HitCooldownPolicy
                            .BYPASS_FOR_AUTHORED_SEQUENCE
            );
        } finally {
            endStrike();
        }
    }

    boolean recordAppliedHit(
            ServerPlayer player,
            LivingEntity target,
            long nowTick
    ) {
        if (pendingTargetId == null
                || !pendingTargetId.equals(target.getUUID())) {
            return false;
        }
        PacketDistributor.sendToPlayer(
                player,
                new BrokenTridentThrustStrikePayload()
        );
        if (!pendingFishCatchActive
                && BrokenTridentCatchTracker.hasFishInInventory(player)) {
            BrokenTridentCatchTracker.start(
                    player,
                    nowTick,
                    FishcatchThrustSkillHandler.FISH_CATCH_DURATION_TICKS
            );
            applyFishCatchSlow(target);
        } else if (pendingFishCatchActive) {
            applyFishCatchSlow(target);
        }
        return true;
    }

    private void beginStrike(UUID targetId, boolean fishCatchActive) {
        pendingTargetId = targetId;
        pendingFishCatchActive = fishCatchActive;
    }

    private void endStrike() {
        pendingTargetId = null;
        pendingFishCatchActive = false;
    }

    static float damageMultiplier(
            float baseDamageMultiplier,
            boolean fishCatchActive
    ) {
        return baseDamageMultiplier
                + (fishCatchActive
                        ? FishcatchThrustSkillHandler.FISH_CATCH_DAMAGE_BONUS
                        : 0.0F);
    }

    private static void applyFishCatchSlow(LivingEntity target) {
        target.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN,
                FishcatchThrustSkillHandler.FISH_CATCH_DURATION_TICKS,
                FishcatchThrustSkillHandler.FISH_CATCH_SLOW_AMPLIFIER,
                false,
                true,
                true
        ));
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
                FishcatchThrustSkillHandler.REACQUIRE_RANGE
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
