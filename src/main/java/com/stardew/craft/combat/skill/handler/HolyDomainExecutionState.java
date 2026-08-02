package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.network.HolyBladeRingPayload;
import com.stardew.craft.combat.skill.HolyBladeEffects;
import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.WeaponSkillDamage;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** One Dawn Sanctuary field and its server-authoritative pulse schedule. */
final class HolyDomainExecutionState
        implements SkillInstance.ExecutionState {
    private final float maxRadius;
    private final long endTick;
    private long nextPulseTick;
    private boolean settled;
    private boolean advancing;

    HolyDomainExecutionState(
            long nowTick,
            int durationTicks,
            float maxRadius
    ) {
        if (durationTicks <= 0 || maxRadius <= 0.0F) {
            throw new IllegalArgumentException(
                    "Dawn Sanctuary duration and radius must be positive"
            );
        }
        this.maxRadius = maxRadius;
        this.endTick = nowTick + durationTicks;
        this.nextPulseTick = nowTick;
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

        advancing = true;
        try {
            if (shouldPulse(context.nowTick(), nextPulseTick)) {
                nextPulseTick += HolyDomainSkillHandler.PULSE_INTERVAL_TICKS;
                pulse(context);
            }
            return SkillTickResult.CONTINUE;
        } finally {
            advancing = false;
        }
    }

    void cancel() {
        settled = true;
    }

    static boolean isExpired(long nowTick, long endTick) {
        return nowTick >= endTick;
    }

    static boolean shouldPulse(long nowTick, long nextPulseTick) {
        return nowTick >= nextPulseTick;
    }

    @SuppressWarnings("null")
    private void pulse(SkillExecutionContext executionContext) {
        ServerLevel level = executionContext.player().serverLevel();
        Vec3 center = executionContext.player().position();
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                executionContext.player(),
                new HolyBladeRingPayload(
                        (float) center.x,
                        (float) center.y,
                        (float) center.z,
                        maxRadius,
                        HolyDomainSkillHandler.RING_DURATION_TICKS
                )
        );
        List<LivingEntity> targets = getTargetsInRadius(
                level,
                center,
                maxRadius,
                executionContext.player()
        );

        for (LivingEntity target : targets) {
            SkillContext context = SkillContext.builder()
                    .skillId("holy_domain")
                    .tier(SkillContext.SkillTier.MAJOR)
                    .damageMultiplier(
                            HolyDomainSkillHandler.PULSE_DAMAGE_MULTIPLIER
                    )
                    .build();
            WeaponSkillDamage.apply(
                    executionContext.player(),
                    target,
                    context,
                    executionContext.weaponSnapshot(),
                    executionContext.nowTick()
                            + HolyDomainSkillHandler
                            .HIT_CONTEXT_LIFETIME_TICKS,
                    WeaponSkillDamage.AttackGatePolicy.SKILL_DAMAGE,
                    WeaponSkillDamage.HitCooldownPolicy
                            .BYPASS_FOR_AUTHORED_SEQUENCE
            );
            HolyBladeEffects.playDomainPulse(level, target);
        }

        HolyBladeEffects.playHeal(
                executionContext.player(),
                HolyDomainSkillHandler.HEAL_AMOUNT
        );
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
}
