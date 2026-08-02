package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.equipment.EquipmentMobEffectHandler;
import com.stardew.craft.combat.equipment.EquipmentNegativeStatusProtection;
import com.stardew.craft.combat.network.ObsidianCrackPayload;
import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.WeaponDamageSnapshot;
import com.stardew.craft.combat.skill.WeaponSkillDamage;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import com.stardew.craft.combat.skill.runtime.WeaponSkillMovementArbiter;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** One Obsidian Crack line and its delayed explosion. */
final class ObsidianCrackExecutionState
        implements SkillInstance.ExecutionState {
    private final long explodeTick;
    private final Vec3 start;
    private final Vec3 end;

    ObsidianCrackExecutionState(
            long nowTick,
            Vec3 start,
            Vec3 end
    ) {
        this.explodeTick = nowTick
                + ObsidianCrackSkillHandler.EXPLODE_DELAY_TICKS;
        this.start = start;
        this.end = end;
    }

    void startPresentation(
            SkillExecutionContext context,
            float yaw,
            float length
    ) {
        Vec3 center = start.add(end).scale(0.5D);
        ServerLevel level = context.player().serverLevel();
        PacketDistributor.sendToPlayersInDimension(
                level,
                new ObsidianCrackPayload(
                        (float) center.x,
                        (float) center.y,
                        (float) center.z,
                        yaw,
                        length,
                        ObsidianCrackSkillHandler.EFFECT_DURATION_TICKS
                )
        );
        level.playSound(
                null,
                center.x,
                center.y,
                center.z,
                SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS,
                0.9F,
                0.8F
        );
    }

    SkillTickResult advance(SkillExecutionContext context) {
        if (isWaitingForExplosion(context.nowTick(), explodeTick)) {
            return SkillTickResult.CONTINUE;
        }
        explode(context);
        return SkillTickResult.COMPLETE;
    }

    static boolean isWaitingForExplosion(long nowTick, long explodeTick) {
        return nowTick < explodeTick;
    }

    static SkillContext createExplosionContext(String skillId) {
        return SkillContext.builder()
                .skillId(skillId)
                .tier(SkillContext.SkillTier.MAJOR)
                .damageMultiplier(
                        ObsidianCrackSkillHandler.DAMAGE_MULTIPLIER
                )
                .build();
    }

    static double distanceToSegmentSqr2D(Vec3 point, Vec3 start, Vec3 end) {
        Vec3 fromStart = new Vec3(
                point.x - start.x,
                0.0D,
                point.z - start.z
        );
        Vec3 segment = new Vec3(
                end.x - start.x,
                0.0D,
                end.z - start.z
        );
        double segmentLengthSquared = segment.lengthSqr();
        if (segmentLengthSquared < 1.0E-6D) {
            return fromStart.lengthSqr();
        }
        double ratio = (fromStart.x * segment.x
                + fromStart.z * segment.z) / segmentLengthSquared;
        ratio = Math.max(0.0D, Math.min(1.0D, ratio));
        double closestX = start.x + segment.x * ratio;
        double closestZ = start.z + segment.z * ratio;
        double deltaX = point.x - closestX;
        double deltaZ = point.z - closestZ;
        return deltaX * deltaX + deltaZ * deltaZ;
    }

    static Vec3 nearestPointOnSegment2D(
            Vec3 point,
            Vec3 start,
            Vec3 end
    ) {
        Vec3 fromStart = new Vec3(
                point.x - start.x,
                0.0D,
                point.z - start.z
        );
        Vec3 segment = new Vec3(
                end.x - start.x,
                0.0D,
                end.z - start.z
        );
        double segmentLengthSquared = segment.lengthSqr();
        if (segmentLengthSquared < 1.0E-6D) {
            return new Vec3(start.x, start.y, start.z);
        }
        double ratio = (fromStart.x * segment.x
                + fromStart.z * segment.z) / segmentLengthSquared;
        ratio = Math.max(0.0D, Math.min(1.0D, ratio));
        return new Vec3(
                start.x + segment.x * ratio,
                start.y,
                start.z + segment.z * ratio
        );
    }

    private void explode(SkillExecutionContext context) {
        ServerPlayer player = context.player();
        ServerLevel level = player.serverLevel();
        Vec3 minimum = new Vec3(
                Math.min(start.x, end.x),
                Math.min(start.y, end.y),
                Math.min(start.z, end.z)
        );
        Vec3 maximum = new Vec3(
                Math.max(start.x, end.x),
                Math.max(start.y, end.y),
                Math.max(start.z, end.z)
        );
        AABB bounds = new AABB(minimum, maximum).inflate(
                ObsidianCrackSkillHandler.PULL_RADIUS,
                1.5D,
                ObsidianCrackSkillHandler.PULL_RADIUS
        );
        List<LivingEntity> targets = level.getEntitiesOfClass(
                LivingEntity.class,
                bounds,
                entity -> entity.isPickable() && entity != player
        );
        WeaponDamageSnapshot weaponSnapshot = context.weaponSnapshot();

        for (LivingEntity target : targets) {
            if (distanceToSegmentSqr2D(
                    target.position(),
                    start,
                    end
            ) > ObsidianCrackSkillHandler.PULL_RADIUS
                    * ObsidianCrackSkillHandler.PULL_RADIUS) {
                continue;
            }
            Vec3 nearest = nearestPointOnSegment2D(
                    target.position(),
                    start,
                    end
            );
            if (target instanceof ServerPlayer targetPlayer) {
                WeaponSkillMovementArbiter.revokeCurrent(targetPlayer);
            }
            target.teleportTo(nearest.x, target.getY(), nearest.z);

            applySlow(target);

            WeaponSkillDamage.apply(
                    player,
                    target,
                    createExplosionContext(context.skillData().getId()),
                    weaponSnapshot,
                    context.nowTick()
                            + ObsidianCrackSkillHandler
                                    .HIT_CONTEXT_LIFETIME_TICKS,
                    WeaponSkillDamage.AttackGatePolicy.SKILL_DAMAGE,
                    WeaponSkillDamage.HitCooldownPolicy
                            .BYPASS_FOR_AUTHORED_SEQUENCE
            );
        }

        level.playSound(
                null,
                player.blockPosition(),
                SoundEvents.GLASS_BREAK,
                SoundSource.PLAYERS,
                0.9F,
                0.9F
        );
        level.playSound(
                null,
                player.blockPosition(),
                SoundEvents.ANVIL_LAND,
                SoundSource.PLAYERS,
                0.6F,
                0.8F
        );
        level.playSound(
                null,
                player.blockPosition(),
                SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.PLAYERS,
                0.9F,
                0.9F
        );

        Vec3 center = start.add(end).scale(0.5D);
        level.sendParticles(
                ParticleTypes.CRIT,
                center.x,
                center.y + 0.2D,
                center.z,
                24,
                0.7D,
                0.08D,
                0.7D,
                0.1D
        );
        level.sendParticles(
                ParticleTypes.SMOKE,
                center.x,
                center.y + 0.1D,
                center.z,
                16,
                0.7D,
                0.02D,
                0.7D,
                0.03D
        );
        level.sendParticles(
                ParticleTypes.EXPLOSION,
                center.x,
                center.y + 0.15D,
                center.z,
                2,
                0.2D,
                0.0D,
                0.2D,
                0.01D
        );

        int steps = 12;
        for (int index = 0; index <= steps; index++) {
            double ratio = index / (double) steps;
            double particleX = start.x + (end.x - start.x) * ratio;
            double particleZ = start.z + (end.z - start.z) * ratio;
            level.sendParticles(
                    ParticleTypes.CRIT,
                    particleX,
                    center.y + 0.08D,
                    particleZ,
                    2,
                    0.08D,
                    0.02D,
                    0.08D,
                    0.02D
            );
            level.sendParticles(
                    ParticleTypes.SMOKE,
                    particleX,
                    center.y + 0.04D,
                    particleZ,
                    1,
                    0.06D,
                    0.01D,
                    0.06D,
                    0.01D
            );
        }
    }

    private static void applySlow(LivingEntity target) {
        EquipmentNegativeStatusProtection.Decision protection =
                EquipmentNegativeStatusProtection.decide(
                        target,
                        ObsidianCrackSkillHandler.SLOW_DURATION_TICKS
                );
        if (protection.resisted()) {
            return;
        }
        EquipmentMobEffectHandler.addPreAdjustedEffect(
                target,
                new MobEffectInstance(
                        MobEffects.MOVEMENT_SLOWDOWN,
                        protection.durationTicks(),
                        ObsidianCrackSkillHandler.SLOW_AMPLIFIER,
                        false,
                        true,
                        true
                )
        );
    }
}
