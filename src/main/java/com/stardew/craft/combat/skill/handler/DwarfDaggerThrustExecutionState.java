package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.network.DwarfDaggerThrustPayload;
import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.WeaponDamageSnapshot;
import com.stardew.craft.combat.skill.WeaponSkillDamage;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import com.stardew.craft.combat.skill.runtime.WeaponSkillMovementArbiter;
import com.stardew.craft.combat.skill.runtime.WeaponSkillMovementControl;
import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.time.StardewTimePauseService;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** One Rune Thrust execution, advanced only from Runtime's ServerTick.Post. */
final class DwarfDaggerThrustExecutionState
        implements SkillInstance.ExecutionState,
        WeaponSkillMovementArbiter.Owner {
    private final Vec3 end;
    private final Vec3 step;
    private final long endTick;
    private final Set<UUID> hitTargets = new HashSet<>();
    private final Set<UUID> appliedTargets = new HashSet<>();
    private Vec3 lastPos;
    private boolean bonusApplied;
    private WeaponSkillMovementArbiter.Lease movementLease;

    DwarfDaggerThrustExecutionState(
            ServerPlayer player,
            long nowTick,
            Vec3 end,
            int durationTicks
    ) {
        if (durationTicks <= 0) {
            throw new IllegalArgumentException(
                    "Rune Thrust duration must be positive"
            );
        }
        Vec3 start = player.position();
        Vec3 difference = end.subtract(start);
        this.end = end;
        this.step = new Vec3(
                difference.x / durationTicks,
                0.0,
                difference.z / durationTicks
        );
        this.endTick = nowTick + durationTicks;
        this.lastPos = start;
    }

    void start(ServerPlayer player, int durationTicks) {
        if (WeaponSkillMovementControl.isLocked(
                player,
                player.serverLevel().getGameTime()
        )) {
            throw new IllegalStateException(
                    "Rune Thrust cannot start while movement is locked"
            );
        }
        movementLease = WeaponSkillMovementArbiter.claim(player, this);
        sendClientState(player, true, durationTicks, end);
    }

    SkillTickResult result(long nowTick) {
        if (!isWithinExecutionWindow(nowTick, endTick)) {
            return SkillTickResult.COMPLETE;
        }
        return WeaponSkillMovementArbiter.owns(movementLease)
                ? SkillTickResult.CONTINUE
                : SkillTickResult.CANCEL;
    }

    SkillTickResult advance(
            SkillExecutionContext context
    ) {
        SkillTickResult status = result(context.nowTick());
        if (status != SkillTickResult.CONTINUE) {
            return status;
        }
        ServerPlayer player = context.player();
        ServerLevel level = player.serverLevel();
        if (WeaponSkillMovementControl.isLocked(
                player,
                context.nowTick()
        )) {
            return SkillTickResult.CANCEL;
        }
        if (StardewTimePauseService.shouldPauseLevel(level)) {
            return SkillTickResult.CONTINUE;
        }
        if (!isWithinExecutionWindow(context.nowTick(), endTick)) {
            return SkillTickResult.COMPLETE;
        }

        Vec3 current = player.position();
        Vec3 desired = current.add(step);
        if (shouldSnapToEnd(context.nowTick(), endTick)) {
            desired = end;
        }
        desired = new Vec3(desired.x, player.getY(), desired.z);

        Vec3 safe = findSafePosition(
                player,
                adjustForCollision(player, desired)
        );
        if (safe == null) {
            return SkillTickResult.CANCEL;
        }

        Vec3 from = lastPos != null ? lastPos : current;
        applyHits(context, level, player, from, safe);

        Vec3 desiredVelocity = safe.subtract(current);
        Vec3 currentVelocity = player.getDeltaMovement();
        Vec3 nextVelocity = currentVelocity.add(
                desiredVelocity.subtract(currentVelocity).scale(0.6)
        );
        player.setDeltaMovement(
                nextVelocity.x,
                currentVelocity.y,
                nextVelocity.z
        );
        player.hasImpulse = true;
        player.move(MoverType.SELF, player.getDeltaMovement());
        player.fallDistance = 0.0F;

        Vec3 afterMove = player.position();
        if (afterMove.subtract(current).horizontalDistanceSqr() < 1.0E-4) {
            player.teleportTo(safe.x, safe.y, safe.z);
            player.fallDistance = 0.0F;
            afterMove = player.position();
        }

        lastPos = afterMove;
        spawnTrail(level, player.position());
        return SkillTickResult.CONTINUE;
    }

    void finish(ServerPlayer player) {
        if (WeaponSkillMovementArbiter.release(movementLease)) {
            sendClientState(player, false, 0, null);
        }
    }

    @Override
    public void onMovementRevoked(ServerPlayer player) {
        sendClientState(player, false, 0, null);
    }

    static boolean isWithinExecutionWindow(long nowTick, long endTick) {
        return nowTick <= endTick;
    }

    static boolean shouldSnapToEnd(long nowTick, long endTick) {
        return nowTick + 1L >= endTick;
    }

    static boolean shouldApplyHitBonus(
            boolean positiveAppliedHit,
            boolean bonusAlreadyApplied
    ) {
        return positiveAppliedHit && !bonusAlreadyApplied;
    }

    private void applyHits(
            SkillExecutionContext executionContext,
            ServerLevel level,
            ServerPlayer player,
            Vec3 start,
            Vec3 end
    ) {
        List<LivingEntity> targets = findTargetsAlongPath(
                level,
                player,
                start,
                end,
                DwarfDaggerThrustSkillHandler.HIT_RADIUS
        );
        if (targets.isEmpty()) {
            return;
        }

        WeaponDamageSnapshot weaponSnapshot =
                executionContext.weaponSnapshot();
        for (LivingEntity target : targets) {
            if (!hitTargets.add(target.getUUID())) {
                continue;
            }
            SkillContext hitContext = SkillContext.builder()
                    .skillId(executionContext.skillData().getId())
                    .tier(SkillContext.SkillTier.MINOR)
                    .damageMultiplier(
                            executionContext.skillData().getDamagePercent()
                                    / 100.0F
                    )
                    .build();
            WeaponSkillDamage.apply(
                    player,
                    target,
                    hitContext,
                    weaponSnapshot,
                    executionContext.nowTick()
                            + DwarfDaggerThrustSkillHandler
                                    .HIT_CONTEXT_LIFETIME_TICKS,
                    WeaponSkillDamage.AttackGatePolicy.RESPECT_AT_IMPACT,
                    WeaponSkillDamage.HitCooldownPolicy
                            .BYPASS_FOR_AUTHORED_SEQUENCE
            );
        }
    }

    boolean recordAppliedHit(
            ServerPlayer player,
            LivingEntity target,
            long nowTick,
            String weaponId,
            String skillId
    ) {
        boolean newPositiveHit = hitTargets.contains(target.getUUID())
                && appliedTargets.add(target.getUUID());
        if (!newPositiveHit) {
            return false;
        }
        target.addEffect(new MobEffectInstance(
                ModMobEffects.WEAK_POINT,
                DwarfDaggerThrustSkillHandler.WEAK_POINT_DURATION_TICKS,
                DwarfDaggerThrustSkillHandler.WEAK_POINT_AMPLIFIER,
                false,
                true,
                true
        ));
        if (shouldApplyHitBonus(true, bonusApplied)) {
            bonusApplied = true;
            player.addEffect(new MobEffectInstance(
                    MobEffects.DAMAGE_RESISTANCE,
                    DwarfDaggerThrustSkillHandler
                            .RESISTANCE_DURATION_TICKS,
                    DwarfDaggerThrustSkillHandler.RESISTANCE_AMPLIFIER,
                    false,
                    true,
                    true
            ));
            PlayerStardewDataAPI.restoreEnergy(
                    player,
                    DwarfDaggerThrustSkillHandler.ENERGY_RESTORE
            );
            if (DwarfDaggerRushSkillHandler.isActive(
                    player,
                    nowTick
            )) {
                WeaponSkillRuntime.clearCooldown(
                        player,
                        weaponId,
                        skillId,
                        nowTick
                );
            }
        }
        return true;
    }

    private static List<LivingEntity> findTargetsAlongPath(
            ServerLevel level,
            Player player,
            Vec3 start,
            Vec3 end,
            double radius
    ) {
        Vec3 min = new Vec3(
                Math.min(start.x, end.x),
                Math.min(start.y, end.y),
                Math.min(start.z, end.z)
        );
        Vec3 max = new Vec3(
                Math.max(start.x, end.x),
                Math.max(start.y, end.y),
                Math.max(start.z, end.z)
        );
        AABB box = new AABB(min, max).inflate(
                radius,
                radius * 0.75,
                radius
        );
        List<LivingEntity> targets = level.getEntitiesOfClass(
                LivingEntity.class,
                box,
                entity -> entity.isPickable() && entity != player
        );
        targets.removeIf(entity ->
                distanceToSegmentSqr(
                        entity.position(),
                        start,
                        end
                ) > radius * radius
        );
        return targets;
    }

    private static double distanceToSegmentSqr(
            Vec3 point,
            Vec3 start,
            Vec3 end
    ) {
        Vec3 segment = end.subtract(start);
        Vec3 offset = point.subtract(start);
        double segmentLengthSqr = segment.lengthSqr();
        if (segmentLengthSqr <= 1.0E-6) {
            return offset.lengthSqr();
        }
        double interpolation = offset.dot(segment) / segmentLengthSqr;
        interpolation = Math.max(0.0, Math.min(1.0, interpolation));
        Vec3 closest = start.add(segment.scale(interpolation));
        return point.distanceToSqr(closest);
    }

    private static Vec3 adjustForCollision(
            ServerPlayer player,
            Vec3 desired
    ) {
        Vec3 start = player.position();
        Vec3 look = desired.subtract(start);
        if (look.lengthSqr() < 1.0E-6) {
            return desired;
        }
        Vec3 direction = new Vec3(look.x, 0.0, look.z).normalize();
        HitResult hit = player.level().clip(new ClipContext(
                start.add(0.0, player.getBbHeight() * 0.5, 0.0),
                desired.add(0.0, player.getBbHeight() * 0.5, 0.0),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));
        if (hit.getType() != HitResult.Type.MISS) {
            return hit.getLocation().subtract(direction.scale(0.4));
        }
        return desired;
    }

    private static Vec3 findSafePosition(Player player, Vec3 desired) {
        AABB box = player.getBoundingBox().move(
                desired.x - player.getX(),
                desired.y - player.getY(),
                desired.z - player.getZ()
        );
        if (player.level().noCollision(player, box)) {
            return desired;
        }
        Vec3 raised = desired.add(0.0, 0.25, 0.0);
        AABB raisedBox = player.getBoundingBox().move(
                raised.x - player.getX(),
                raised.y - player.getY(),
                raised.z - player.getZ()
        );
        return player.level().noCollision(player, raisedBox)
                ? raised
                : null;
    }

    private static void spawnTrail(ServerLevel level, Vec3 position) {
        double y = position.y + 0.6;
        level.sendParticles(
                ParticleTypes.ENCHANT,
                position.x,
                y,
                position.z,
                6,
                0.2,
                0.15,
                0.2,
                0.02
        );
        level.sendParticles(
                ParticleTypes.CRIT,
                position.x,
                y,
                position.z,
                4,
                0.25,
                0.15,
                0.25,
                0.03
        );
        level.sendParticles(
                ParticleTypes.ELECTRIC_SPARK,
                position.x,
                y,
                position.z,
                3,
                0.2,
                0.1,
                0.2,
                0.03
        );
    }

    private static void sendClientState(
            ServerPlayer player,
            boolean active,
            int durationTicks,
            Vec3 end
    ) {
        double endX = end != null ? end.x : 0.0;
        double endY = end != null ? end.y : 0.0;
        double endZ = end != null ? end.z : 0.0;
        PacketDistributor.sendToPlayer(
                player,
                new DwarfDaggerThrustPayload(
                        active,
                        durationTicks,
                        endX,
                        endY,
                        endZ
                )
        );
    }
}
