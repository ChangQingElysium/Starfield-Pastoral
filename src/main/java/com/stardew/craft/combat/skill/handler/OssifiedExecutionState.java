package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.network.OssifiedExecutionCirclePayload;
import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.WeaponSkillDamage;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import com.stardew.craft.combat.skill.runtime.WeaponSkillMovementArbiter;
import java.util.List;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

/** One Ossified Execution circle and its server-authoritative pulse schedule. */
final class OssifiedExecutionState
        implements SkillInstance.ExecutionState {
    private ServerLevel presentationLevel;
    private int casterId;
    private final long castTick;
    private final Vec3 center;
    private final float radius;
    private final ResourceKey<Level> dimension;
    private final long endTick;
    private final int durationTicks;
    private long nextDamageTick;
    private boolean settled;
    private boolean advancing;

    OssifiedExecutionState(
            Vec3 center,
            float radius,
            ResourceKey<Level> dimension,
            long nowTick,
            int durationTicks
    ) {
        if (radius <= 0.0F || durationTicks <= 0) {
            throw new IllegalArgumentException(
                    "Ossified Execution radius and duration must be positive"
            );
        }
        this.castTick = nowTick;
        this.center = center;
        this.radius = radius;
        this.dimension = dimension;
        this.endTick = nowTick + durationTicks;
        this.durationTicks = durationTicks;
        this.nextDamageTick = nowTick;
    }

    @SuppressWarnings("null")
    void activate(SkillExecutionContext context) {
        ServerLevel level = context.player().serverLevel();
        presentationLevel = level;
        casterId = context.player().getId();
        sendPhase(OssifiedExecutionCirclePayload.START);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.BONE_BLOCK_PLACE, SoundSource.PLAYERS, 0.65f, 0.7f);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 0.3f, 1.2f);
    }

    private void sendPhase(int phase) {
        if (presentationLevel == null) return;
        PacketDistributor.sendToPlayersInDimension(presentationLevel, new OssifiedExecutionCirclePayload(
                casterId, castTick, phase, center.x, center.y, center.z, radius, durationTicks));
    }

    SkillTickResult advance(SkillExecutionContext context) {
        if (settled) {
            return SkillTickResult.COMPLETE;
        }
        if (advancing) {
            return SkillTickResult.CONTINUE;
        }
        if (isExpired(context.nowTick(), endTick)) {
            settled = true;
            return SkillTickResult.COMPLETE;
        }
        if (!dimension.equals(context.player().level().dimension())) {
            settled = true;
            return SkillTickResult.CANCEL;
        }

        advancing = true;
        try {
            ServerLevel level = context.player().serverLevel();
            List<LivingEntity> targets = getTargetsInRadius(
                    level,
                    center,
                    radius,
                    context.player()
            );
            pullTargets(targets, center, radius);
            if (shouldPulse(context.nowTick(), nextDamageTick)) {
                nextDamageTick += OssifiedExecutionSkillHandler
                        .DAMAGE_INTERVAL_TICKS;
                sendPhase(OssifiedExecutionCirclePayload.PULSE);
                pulse(context, targets);
            }
            return SkillTickResult.CONTINUE;
        } finally {
            advancing = false;
        }
    }

    float critDamageBonus(
            long nowTick,
            ResourceKey<Level> casterDimension,
            LivingEntity target
    ) {
        if (settled
                || isExpired(nowTick, endTick)
                || !dimension.equals(casterDimension)
                || !dimension.equals(target.level().dimension())
                || target.distanceToSqr(center.x, center.y, center.z)
                > radius * radius) {
            return 0.0F;
        }
        return OssifiedExecutionSkillHandler.CRIT_DAMAGE_BONUS;
    }

    boolean isActive(
            long nowTick,
            ResourceKey<Level> casterDimension
    ) {
        return !settled
                && !isExpired(nowTick, endTick)
                && dimension.equals(casterDimension);
    }

    void cancel() {
        sendPhase(OssifiedExecutionCirclePayload.END);
        presentationLevel = null;
        settled = true;
    }

    static SkillContext createPulseContext() {
        return SkillContext.builder()
                .skillId("ossified_execution_dot")
                .tier(SkillContext.SkillTier.MAJOR)
                .damageMultiplier(
                        OssifiedExecutionSkillHandler.PULSE_DAMAGE_MULTIPLIER
                )
                .build();
    }

    static boolean isExpired(long nowTick, long endTick) {
        return nowTick >= endTick;
    }

    static boolean shouldPulse(long nowTick, long nextDamageTick) {
        return nowTick >= nextDamageTick;
    }

    @SuppressWarnings("null")
    private static void pulse(
            SkillExecutionContext context,
            List<LivingEntity> targets
    ) {
        for (LivingEntity target : targets) {
            WeaponSkillDamage.apply(
                    context.player(),
                    target,
                    createPulseContext(),
                    context.weaponSnapshot(),
                    context.nowTick()
                            + OssifiedExecutionSkillHandler
                            .HIT_CONTEXT_LIFETIME_TICKS,
                    WeaponSkillDamage.AttackGatePolicy.SKILL_DAMAGE,
                    WeaponSkillDamage.HitCooldownPolicy
                            .BYPASS_FOR_AUTHORED_SEQUENCE
            );
        }
    }

    private static List<LivingEntity> getTargetsInRadius(
            ServerLevel level,
            Vec3 center,
            float radius,
            Player owner
    ) {
        AABB bounds = new AABB(
                center.x - radius,
                center.y - radius * 0.6D,
                center.z - radius,
                center.x + radius,
                center.y + radius * 0.6D,
                center.z + radius
        );
        return level.getEntitiesOfClass(
                LivingEntity.class,
                bounds,
                entity -> entity.isPickable()
                        && entity.isAlive()
                        && entity != owner
        );
    }

    @SuppressWarnings("null")
    private static void pullTargets(
            List<LivingEntity> targets,
            Vec3 center,
            float radius
    ) {
        for (LivingEntity target : targets) {
            Vec3 toCenter = new Vec3(
                    center.x - target.getX(),
                    0.0D,
                    center.z - target.getZ()
            );
            double distance = toCenter.length();
            if (distance < OssifiedExecutionSkillHandler
                    .MINIMUM_PULL_DISTANCE || distance > radius) {
                continue;
            }
            Vec3 direction = toCenter.normalize();
            double strength = OssifiedExecutionSkillHandler
                    .BASE_PULL_STRENGTH
                    + (1.0D - distance / radius)
                    * OssifiedExecutionSkillHandler.INNER_PULL_BONUS;
            Vec3 pull = new Vec3(
                    direction.x * strength,
                    0.0D,
                    direction.z * strength
            );
            WeaponSkillMovementArbiter.revokeCurrentIfPlayer(target);
            target.setDeltaMovement(target.getDeltaMovement().add(pull));
        }
    }
}
