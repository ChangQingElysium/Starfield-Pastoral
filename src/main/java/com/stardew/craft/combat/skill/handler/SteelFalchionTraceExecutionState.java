package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.network.SteelFalchionLineBurstPayload;
import com.stardew.craft.combat.network.SteelFalchionLineCreatePayload;
import com.stardew.craft.combat.network.SteelFalchionLinePointPayload;
import com.stardew.craft.combat.network.SteelFalchionLinePulsePayload;
import com.stardew.craft.combat.network.SteelFalchionTracePayload;
import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.WeaponDamageSnapshot;
import com.stardew.craft.combat.skill.WeaponSkillDamage;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import com.stardew.craft.effect.ModMobEffects;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** One moving Slash Trace and its authored end burst. */
final class SteelFalchionTraceExecutionState
        implements SkillInstance.ExecutionState {
    private final ResourceKey<Level> dimension;
    private final int lineId;
    private final List<Vec3> points = new ArrayList<>();
    private final Set<UUID> triggeredTargets = new HashSet<>();
    private final long traceEndTick;
    private final long lineEndTick;
    private final float dotMultiplier;
    private final WeaponDamageSnapshot weaponSnapshot;
    private Vec3 lastPosition;
    private boolean burstDone;
    private boolean settled;

    SteelFalchionTraceExecutionState(
            ResourceKey<Level> dimension,
            long nowTick,
            int traceDurationTicks,
            Vec3 start,
            float dotMultiplier,
            WeaponDamageSnapshot weaponSnapshot
    ) {
        if (traceDurationTicks <= 0) {
            throw new IllegalArgumentException(
                    "Slash Trace duration must be positive"
            );
        }
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.lineId = SteelFalchionExecutionSupport.nextLineId();
        this.traceEndTick = nowTick + traceDurationTicks;
        this.lineEndTick = nowTick
                + SteelFalchionExecutionSupport.LINE_DURATION_TICKS;
        this.lastPosition = Objects.requireNonNull(start, "start");
        this.points.add(start);
        this.dotMultiplier = dotMultiplier;
        this.weaponSnapshot = weaponSnapshot;
    }

    @SuppressWarnings("null")
    void start(ServerPlayer player, int durationTicks) {
        Vec3 start = points.get(0);
        PacketDistributor.sendToPlayersInDimension(
                player.serverLevel(),
                new SteelFalchionLineCreatePayload(
                        lineId,
                        (float) start.x,
                        (float) start.y,
                        (float) start.z,
                        SteelFalchionExecutionSupport.LINE_DURATION_TICKS,
                        SteelFalchionExecutionSupport.LINE_WIDTH
                )
        );
        PacketDistributor.sendToPlayer(
                player,
                new SteelFalchionTracePayload(true, durationTicks)
        );
        player.addEffect(new MobEffectInstance(
                ModMobEffects.SPEED,
                durationTicks,
                SteelFalchionExecutionSupport.TRACE_SPEED_AMPLIFIER,
                false,
                true,
                true
        ));
        player.level().playSound(
                null,
                player.blockPosition(),
                SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS,
                0.8F,
                1.1F
        );
    }

    SkillTickResult advance(SkillExecutionContext context) {
        if (settled) {
            return SkillTickResult.COMPLETE;
        }
        if (!dimension.equals(context.player().level().dimension())) {
            return SkillTickResult.CANCEL;
        }
        if (SteelFalchionExecutionSupport.isWithinTraceWindow(
                context.nowTick(),
                traceEndTick
        )) {
            updateTrace(context.player());
        }
        if (!burstDone && context.nowTick() >= traceEndTick) {
            burst(context);
        }
        if (SteelFalchionExecutionSupport.isExpired(
                context.nowTick(),
                lineEndTick
        )) {
            settled = true;
            return SkillTickResult.COMPLETE;
        }
        handleTriggers(context.player(), context.nowTick());
        return SkillTickResult.CONTINUE;
    }

    void cancel(ServerPlayer player, boolean notifyClient) {
        if (settled) {
            return;
        }
        settled = true;
        if (notifyClient) {
            PacketDistributor.sendToPlayer(
                    player,
                    new SteelFalchionTracePayload(false, 0)
            );
        }
    }

    private void updateTrace(ServerPlayer player) {
        Vec3 current = new Vec3(
                player.getX(),
                player.getY() + 0.02D,
                player.getZ()
        );
        double distance = current.subtract(lastPosition)
                .horizontalDistance();
        if (distance < SteelFalchionExecutionSupport.TRACE_MIN_DISTANCE) {
            return;
        }
        for (Vec3 point : SteelFalchionExecutionSupport.sampleTracePoints(
                lastPosition,
                current
        )) {
            points.add(point);
            PacketDistributor.sendToPlayersInDimension(
                    player.serverLevel(),
                    new SteelFalchionLinePointPayload(
                            lineId,
                            (float) point.x,
                            (float) point.y,
                            (float) point.z
                    )
            );
        }
        lastPosition = current;
    }

    @SuppressWarnings("null")
    private void handleTriggers(ServerPlayer player, long nowTick) {
        if (points.size() < 2) {
            return;
        }
        ServerLevel level = player.serverLevel();
        AABB bounds = SteelFalchionExecutionSupport.computeBounds(points)
                .inflate(
                        SteelFalchionExecutionSupport.TRIGGER_RADIUS,
                        1.0D,
                        SteelFalchionExecutionSupport.TRIGGER_RADIUS
                );
        List<LivingEntity> targets = level.getEntitiesOfClass(
                LivingEntity.class,
                bounds,
                entity -> entity.isPickable() && entity.isAlive()
        );
        for (LivingEntity target : targets) {
            if (target == player
                    || triggeredTargets.contains(target.getUUID())) {
                continue;
            }
            float triggerRadius = Math.max(
                    SteelFalchionExecutionSupport.LINE_WIDTH,
                    SteelFalchionExecutionSupport.TRIGGER_RADIUS
            );
            float targetRadius = (float) Math.max(
                    0.2D,
                    target.getBbWidth() * 0.5D
            );
            float hitRadius = Math.max(triggerRadius, targetRadius);
            if (SteelFalchionExecutionSupport.distanceToPolylineSqr2D(
                    target.position(),
                    points
            ) > hitRadius * hitRadius) {
                continue;
            }
            triggeredTargets.add(target.getUUID());
            SteelFalchionDotTracker.apply(
                    player,
                    target,
                    nowTick,
                    dotMultiplier,
                    SkillContext.SkillTier.MAJOR,
                    weaponSnapshot
            );
            PacketDistributor.sendToPlayersInDimension(
                    level,
                    new SteelFalchionLinePulsePayload(lineId, 8)
            );
            level.playSound(
                    null,
                    target.blockPosition(),
                    SoundEvents.TRIDENT_HIT,
                    SoundSource.PLAYERS,
                    0.5F,
                    1.2F
            );
        }
    }

    @SuppressWarnings("null")
    private void burst(SkillExecutionContext context) {
        burstDone = true;
        if (points.size() < 2) {
            return;
        }
        ServerLevel level = context.player().serverLevel();
        PacketDistributor.sendToPlayersInDimension(
                level,
                new SteelFalchionLineBurstPayload(lineId)
        );
        AABB bounds = SteelFalchionExecutionSupport.computeBounds(points)
                .inflate(
                        SteelFalchionExecutionSupport.BURST_RADIUS,
                        1.2D,
                        SteelFalchionExecutionSupport.BURST_RADIUS
                );
        List<LivingEntity> targets = level.getEntitiesOfClass(
                LivingEntity.class,
                bounds,
                entity -> entity.isPickable()
                        && entity.isAlive()
                        && entity != context.player()
        );
        Set<UUID> damaged = new HashSet<>();
        for (LivingEntity target : targets) {
            if (!damaged.add(target.getUUID())
                    || SteelFalchionExecutionSupport
                    .distanceToPolylineSqr2D(target.position(), points)
                    > SteelFalchionExecutionSupport.BURST_RADIUS
                    * SteelFalchionExecutionSupport.BURST_RADIUS) {
                continue;
            }
            applyDamage(
                    context,
                    target,
                    SteelFalchionExecutionSupport
                            .createTraceBurstContext()
            );
        }
        level.playSound(
                null,
                context.player().blockPosition(),
                SoundEvents.PLAYER_ATTACK_STRONG,
                SoundSource.PLAYERS,
                0.75F,
                1.0F
        );
    }

    private void applyDamage(
            SkillExecutionContext context,
            LivingEntity target,
            SkillContext hitContext
    ) {
        long expireTick = context.nowTick()
                + SteelFalchionDotTracker.HIT_CONTEXT_LIFETIME_TICKS;
        if (weaponSnapshot == null) {
            WeaponSkillDamage.apply(
                    context.player(),
                    target,
                    hitContext,
                    expireTick,
                    WeaponSkillDamage.AttackGatePolicy.SKILL_DAMAGE,
                    WeaponSkillDamage.HitCooldownPolicy
                            .BYPASS_FOR_AUTHORED_SEQUENCE
            );
            return;
        }
        WeaponSkillDamage.apply(
                context.player(),
                target,
                hitContext,
                weaponSnapshot,
                expireTick,
                WeaponSkillDamage.AttackGatePolicy.SKILL_DAMAGE,
                WeaponSkillDamage.HitCooldownPolicy
                        .BYPASS_FOR_AUTHORED_SEQUENCE
        );
    }
}
