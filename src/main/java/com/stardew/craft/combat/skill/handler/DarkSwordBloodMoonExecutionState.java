package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.CombatHealing;
import com.stardew.craft.combat.skill.DarkSwordEffects;
import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.WeaponDamageSnapshot;
import com.stardew.craft.combat.skill.WeaponSkillDamage;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import java.util.List;
import java.util.Objects;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** One authoritative Blood Moon window and its natural finisher. */
final class DarkSwordBloodMoonExecutionState
        implements SkillInstance.ExecutionState {
    enum TickAction {
        CONTINUE,
        BURN_AND_CONTINUE,
        FINISH,
        COMPLETE,
        CANCEL
    }

    private final long endTick;
    private long nextBurnTick;
    private final int burnIntervalTicks;
    private final ResourceKey<Level> originDimension;
    private final float averageWeaponDamage;
    private final WeaponDamageSnapshot weaponSnapshot;
    private float totalBurned;
    private float totalHealed;
    private boolean settled;

    DarkSwordBloodMoonExecutionState(
            long nowTick,
            int durationTicks,
            int burnIntervalTicks,
            ResourceKey<Level> originDimension,
            float averageWeaponDamage,
            WeaponDamageSnapshot weaponSnapshot
    ) {
        if (durationTicks <= 0) {
            throw new IllegalArgumentException(
                    "Blood Moon duration must be positive"
            );
        }
        this.endTick = nowTick + durationTicks;
        this.burnIntervalTicks = Math.max(1, burnIntervalTicks);
        this.nextBurnTick = nowTick + this.burnIntervalTicks;
        this.originDimension = Objects.requireNonNull(
                originDimension,
                "originDimension"
        );
        this.averageWeaponDamage = Math.max(0.0F, averageWeaponDamage);
        this.weaponSnapshot = Objects.requireNonNull(
                weaponSnapshot,
                "weaponSnapshot"
        );
    }

    boolean isActive(
            long nowTick,
            boolean casterAvailable,
            ResourceKey<Level> currentDimension
    ) {
        return !settled
                && shouldRemainActive(
                        endTick,
                        nowTick,
                        casterAvailable,
                        isSameDimension(
                                originDimension,
                                currentDimension
                        )
                );
    }

    boolean recordLifeSteal(
            long nowTick,
            boolean casterAvailable,
            ResourceKey<Level> currentDimension,
            float healedAmount
    ) {
        if (!isActive(nowTick, casterAvailable, currentDimension)) {
            return false;
        }
        totalHealed += Math.max(0.0F, healedAmount);
        return true;
    }

    TickAction prepareTick(
            long nowTick,
            boolean casterAvailable,
            ResourceKey<Level> currentDimension
    ) {
        if (settled) {
            return TickAction.COMPLETE;
        }
        if (!casterAvailable
                || !isSameDimension(
                        originDimension,
                        currentDimension
                )) {
            settled = true;
            return TickAction.CANCEL;
        }
        if (nowTick > endTick) {
            settled = true;
            return TickAction.FINISH;
        }
        if (nowTick >= nextBurnTick) {
            nextBurnTick += burnIntervalTicks;
            return TickAction.BURN_AND_CONTINUE;
        }
        return TickAction.CONTINUE;
    }

    SkillTickResult advance(SkillExecutionContext context) {
        TickAction action = prepareTick(
                context.nowTick(),
                context.player().isAlive()
                        && !context.player().isRemoved(),
                context.player().level().dimension()
        );
        return switch (action) {
            case CONTINUE -> SkillTickResult.CONTINUE;
            case BURN_AND_CONTINUE -> {
                applyBurn(context.player());
                yield SkillTickResult.CONTINUE;
            }
            case FINISH -> {
                finishNaturally(context);
                yield SkillTickResult.COMPLETE;
            }
            case COMPLETE -> SkillTickResult.COMPLETE;
            case CANCEL -> SkillTickResult.CANCEL;
        };
    }

    void cancel() {
        settled = true;
    }

    float totalHealed() {
        return totalHealed;
    }

    private void applyBurn(ServerPlayer player) {
        float burn = burnAmount(CombatHealing.maximumHealth(player));
        float actualBurn = CombatHealing.spendNonlethal(
                player,
                burn,
                DarkSwordBloodMoonSkillHandler.MINIMUM_REMAINING_HEALTH
        );
        if (actualBurn > 0.0F) {
            totalBurned += actualBurn;
            DarkSwordEffects.playBloodMoonBurn(player);
        }
    }

    private void finishNaturally(SkillExecutionContext context) {
        float netBurn = netBurn(totalBurned, totalHealed);
        if (netBurn <= 0.0F) {
            return;
        }
        float damageMultiplier = burstDamageMultiplier(
                netBurn,
                averageWeaponDamage
        );
        if (damageMultiplier <= 0.0F
                || !(context.player().level() instanceof ServerLevel level)) {
            return;
        }

        List<LivingEntity> targets = getTargetsInRadius(
                level,
                context.player().position(),
                DarkSwordBloodMoonSkillHandler.BURST_RADIUS,
                context.player()
        );
        if (targets.isEmpty()) {
            return;
        }

        DarkSwordEffects.playBloodMoonBurst(context.player());
        for (LivingEntity target : targets) {
            WeaponSkillDamage.apply(
                    context.player(),
                    target,
                    createBurstContext(damageMultiplier),
                    weaponSnapshot,
                    context.nowTick()
                            + DarkSwordBloodMoonSkillHandler
                                    .HIT_CONTEXT_LIFETIME_TICKS,
                    WeaponSkillDamage.AttackGatePolicy.SKILL_DAMAGE,
                    WeaponSkillDamage.HitCooldownPolicy
                            .BYPASS_FOR_AUTHORED_SEQUENCE
            );
        }
    }

    static boolean shouldRemainActive(
            long endTick,
            long nowTick,
            boolean casterAvailable,
            boolean sameDimension
    ) {
        return casterAvailable
                && sameDimension
                && nowTick <= endTick;
    }

    static boolean isSameDimension(
            ResourceKey<Level> expected,
            ResourceKey<Level> actual
    ) {
        return expected.equals(actual);
    }

    static float burnAmount(float maximumHealth) {
        return Math.max(
                DarkSwordBloodMoonSkillHandler.MINIMUM_BURN_AMOUNT,
                Math.max(0.0F, maximumHealth)
                        * DarkSwordBloodMoonSkillHandler
                                .BURN_MAXIMUM_HEALTH_RATIO
        );
    }

    static float netBurn(float totalBurned, float totalHealed) {
        return Math.max(
                0.0F,
                Math.max(0.0F, totalBurned)
                        - Math.max(0.0F, totalHealed)
        );
    }

    static float burstDamageMultiplier(
            float netBurn,
            float averageWeaponDamage
    ) {
        if (netBurn <= 0.0F || averageWeaponDamage <= 0.0F) {
            return 0.0F;
        }
        return Math.max(
                DarkSwordBloodMoonSkillHandler
                        .MINIMUM_BURST_DAMAGE_MULTIPLIER,
                netBurn / averageWeaponDamage
        );
    }

    static SkillContext createBurstContext(float damageMultiplier) {
        return SkillContext.builder()
                .skillId("dark_sword_blood_moon_burst")
                .tier(SkillContext.SkillTier.MAJOR)
                .damageMultiplier(damageMultiplier)
                .build();
    }

    private static List<LivingEntity> getTargetsInRadius(
            ServerLevel level,
            Vec3 center,
            float radius,
            Player owner
    ) {
        AABB box = new AABB(
                center.x - radius,
                center.y - radius * 0.6,
                center.z - radius,
                center.x + radius,
                center.y + radius * 0.6,
                center.z + radius
        );
        return level.getEntitiesOfClass(
                LivingEntity.class,
                box,
                entity -> entity.isPickable()
                        && entity.isAlive()
                        && entity != owner
        );
    }
}
