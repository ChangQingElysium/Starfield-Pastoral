package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.network.SteelFalchionLineCreatePayload;
import com.stardew.craft.combat.network.SteelFalchionLinePointPayload;
import com.stardew.craft.combat.network.SteelFalchionLinePulsePayload;
import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.WeaponDamageSnapshot;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import com.stardew.craft.effect.ModMobEffects;
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

/** One fixed Swift Etch line; detached DOTs are handed to their own tracker. */
final class SteelFalchionLineExecutionState
        implements SkillInstance.ExecutionState {
    private final ResourceKey<Level> dimension;
    private final int lineId;
    private final List<Vec3> points;
    private final long endTick;
    private final float dotMultiplier;
    private final WeaponDamageSnapshot weaponSnapshot;
    private final Set<UUID> triggeredTargets = new HashSet<>();
    private boolean speedTriggered;
    private boolean settled;

    SteelFalchionLineExecutionState(
            ResourceKey<Level> dimension,
            long nowTick,
            Vec3 center,
            float yawDegrees,
            float dotMultiplier,
            WeaponDamageSnapshot weaponSnapshot
    ) {
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.lineId = SteelFalchionExecutionSupport.nextLineId();
        this.points = SteelFalchionExecutionSupport.createMinorLinePoints(
                Objects.requireNonNull(center, "center"),
                yawDegrees
        );
        this.endTick = nowTick
                + SteelFalchionExecutionSupport.LINE_DURATION_TICKS;
        this.dotMultiplier = dotMultiplier;
        this.weaponSnapshot = weaponSnapshot;
    }

    @SuppressWarnings("null")
    void start(ServerPlayer player) {
        Vec3 start = points.get(0);
        Vec3 end = points.get(1);
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
        PacketDistributor.sendToPlayersInDimension(
                player.serverLevel(),
                new SteelFalchionLinePointPayload(
                        lineId,
                        (float) end.x,
                        (float) end.y,
                        (float) end.z
                )
        );
        player.serverLevel().playSound(
                null,
                player.blockPosition(),
                SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS,
                0.7F,
                1.35F
        );
    }

    SkillTickResult advance(SkillExecutionContext context) {
        if (settled) {
            return SkillTickResult.COMPLETE;
        }
        if (!dimension.equals(context.player().level().dimension())) {
            settled = true;
            return SkillTickResult.CANCEL;
        }
        if (SteelFalchionExecutionSupport.isExpired(
                context.nowTick(),
                endTick
        )) {
            settled = true;
            return SkillTickResult.COMPLETE;
        }
        handleTriggers(context.player(), context.nowTick());
        return SkillTickResult.CONTINUE;
    }

    void cancel() {
        settled = true;
    }

    @SuppressWarnings("null")
    private void handleTriggers(ServerPlayer player, long nowTick) {
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
                    SkillContext.SkillTier.MINOR,
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

        float speedRadius = SteelFalchionExecutionSupport.LINE_WIDTH
                + 0.05F;
        if (!speedTriggered
                && SteelFalchionExecutionSupport.distanceToPolylineSqr2D(
                        player.position(),
                        points
                ) <= speedRadius * speedRadius) {
            speedTriggered = true;
            player.addEffect(new MobEffectInstance(
                    ModMobEffects.SPEED,
                    SteelFalchionExecutionSupport.SPEED_DURATION_TICKS,
                    SteelFalchionExecutionSupport.LINE_SPEED_AMPLIFIER,
                    false,
                    true,
                    true
            ));
            level.playSound(
                    null,
                    player.blockPosition(),
                    SoundEvents.EXPERIENCE_ORB_PICKUP,
                    SoundSource.PLAYERS,
                    0.5F,
                    1.5F
            );
        }
    }
}
