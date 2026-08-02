package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.WeaponDamageSnapshot;
import com.stardew.craft.combat.skill.WeaponSkillDamage;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** One Quench initial hit and its optional delayed blast. */
final class TemperedQuenchExecutionState
        implements SkillInstance.ExecutionState {
    enum TickAction {
        CONTINUE,
        FIRE_AND_CONTINUE,
        COMPLETE,
        CANCEL
    }

    record TickPlan(
            TickAction action,
            PendingBlast blast
    ) {}

    record PendingBlast(
            long triggerTick,
            UUID targetId,
            WeaponDamageSnapshot weaponSnapshot
    ) {}

    private final ResourceKey<Level> dimension;
    private PendingBlast pendingBlast;
    private boolean cancelled;

    TemperedQuenchExecutionState(ResourceKey<Level> dimension) {
        this.dimension = Objects.requireNonNull(dimension, "dimension");
    }

    boolean arm(
            UUID targetId,
            ResourceKey<Level> currentDimension,
            long nowTick,
            int delayTicks,
            WeaponDamageSnapshot weaponSnapshot
    ) {
        if (cancelled
                || !dimension.equals(currentDimension)
                || targetId == null
                || weaponSnapshot == null) {
            return false;
        }
        pendingBlast = new PendingBlast(
                nowTick + Math.max(1, delayTicks),
                targetId,
                weaponSnapshot
        );
        return true;
    }

    TickPlan prepareTick(
            long nowTick,
            ResourceKey<Level> currentDimension
    ) {
        if (cancelled || !dimension.equals(currentDimension)) {
            return new TickPlan(TickAction.CANCEL, null);
        }
        PendingBlast blast = pendingBlast;
        if (blast == null) {
            return new TickPlan(TickAction.COMPLETE, null);
        }
        if (!shouldTrigger(nowTick, blast.triggerTick())) {
            return new TickPlan(TickAction.CONTINUE, null);
        }
        pendingBlast = null;
        return new TickPlan(TickAction.FIRE_AND_CONTINUE, blast);
    }

    SkillTickResult advance(SkillExecutionContext context) {
        TickPlan plan = prepareTick(
                context.nowTick(),
                context.player().level().dimension()
        );
        return switch (plan.action()) {
            case CONTINUE -> SkillTickResult.CONTINUE;
            case COMPLETE -> SkillTickResult.COMPLETE;
            case CANCEL -> SkillTickResult.CANCEL;
            case FIRE_AND_CONTINUE -> {
                fire(context, plan.blast());
                yield SkillTickResult.CONTINUE;
            }
        };
    }

    void cancel() {
        cancelled = true;
        pendingBlast = null;
    }

    static boolean shouldTrigger(long nowTick, long triggerTick) {
        return nowTick >= triggerTick;
    }

    static SkillContext createBlastContext() {
        return SkillContext.builder()
                .skillId("tempered_quench_blast")
                .tier(SkillContext.SkillTier.MINOR)
                .damageMultiplier(
                        TemperedQuenchSkillHandler.BLAST_DAMAGE_MULTIPLIER
                )
                .build();
    }

    @SuppressWarnings("null")
    private static void fire(
            SkillExecutionContext context,
            PendingBlast blast
    ) {
        if (!(context.player().level() instanceof ServerLevel level)) {
            return;
        }
        Entity entity = level.getEntity(blast.targetId());
        if (!(entity instanceof LivingEntity target) || !target.isAlive()) {
            return;
        }
        explode(
                context,
                level,
                target,
                blast.weaponSnapshot()
        );
    }

    @SuppressWarnings("null")
    private static void explode(
            SkillExecutionContext context,
            ServerLevel level,
            LivingEntity target,
            WeaponDamageSnapshot weaponSnapshot
    ) {
        Vec3 center = target.position();

        level.playSound(null, center.x, center.y, center.z,
                SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.9f, 0.9f);
        level.playSound(null, center.x, center.y, center.z,
                SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.8f, 1.1f);
        level.playSound(null, center.x, center.y, center.z,
                SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.7f, 0.9f);

        level.sendParticles(ParticleTypes.FLAME,
                center.x, center.y + 0.2, center.z,
                18, 0.8, 0.2, 0.8, 0.02);
        level.sendParticles(ParticleTypes.LAVA,
                center.x, center.y + 0.15, center.z,
                8, 0.5, 0.15, 0.5, 0.01);
        level.sendParticles(ParticleTypes.SMOKE,
                center.x, center.y + 0.1, center.z,
                10, 0.7, 0.1, 0.7, 0.02);

        WeaponSkillDamage.apply(
                context.player(),
                target,
                createBlastContext(),
                weaponSnapshot,
                context.nowTick()
                        + TemperedQuenchSkillHandler
                                .BLAST_HIT_CONTEXT_LIFETIME_TICKS,
                WeaponSkillDamage.AttackGatePolicy.SKILL_DAMAGE,
                WeaponSkillDamage.HitCooldownPolicy
                        .BYPASS_FOR_AUTHORED_SEQUENCE
        );

    }
}
