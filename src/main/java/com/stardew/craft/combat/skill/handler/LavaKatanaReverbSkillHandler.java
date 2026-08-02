package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.network.LavaKatanaReverbPayload;
import com.stardew.craft.combat.skill.LavaKatanaMarkTracker;
import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.runtime.RuntimeWeaponSkillHandler;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTargeting;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import com.stardew.craft.combat.skill.runtime.SkillValidation;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.player.PlayerStardewDataAPI;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server-authoritative lifecycle for Lava Katana's original Molten Reverb.
 */
public final class LavaKatanaReverbSkillHandler
        implements RuntimeWeaponSkillHandler {
    public static final float ENERGY_COST = 12.0F;
    public static final int ACTIVE_DURATION_TICKS = 80;
    public static final double TARGET_RANGE = 8.0D;
    public static final int MINIMUM_HEAT = 5;

    @Override
    public SkillValidation validate(SkillExecutionContext context) {
        if (WeaponSkillRuntime.hasActive(
            context.player().getUUID(),
            context.skillId()
        )) {
            return SkillValidation.reject(
                SkillValidation.RejectionReason.INVALID_STATE
            );
        }
        if (WeaponSkillCooldowns.isOnCooldown(
            context.player(),
            context.weaponId().getPath(),
            context.skillData().getId(),
            context.nowTick()
        )) {
            return SkillValidation.reject(
                SkillValidation.RejectionReason.COOLDOWN
            );
        }
        if (!canPayEnergy(context) || resolveCastPlan(context) == null) {
            return SkillValidation.reject(
                SkillValidation.RejectionReason.INVALID_STATE
            );
        }
        return SkillValidation.accept();
    }

    @Override
    public void begin(
        SkillExecutionContext context,
        SkillInstance instance
    ) {
        CastPlan plan = resolveCastPlan(context);
        if (plan == null) {
            throw new IllegalStateException(
                "Validated Molten Reverb targets are no longer available"
            );
        }
        if (!canPayEnergy(context)) {
            throw new IllegalStateException(
                "Validated Molten Reverb energy is no longer available"
            );
        }
        if (!WeaponSkillRuntime.consumeEnergyDuringBegin(
            context,
            instance,
            ENERGY_COST
        )) {
            throw new IllegalStateException(
                "Validated Molten Reverb energy payment failed"
            );
        }

        if (plan.fallbackTarget() != null) {
            LivingEntity target = plan.fallbackTarget();
            instance.setTargetEntityIds(List.of(target.getId()));
        } else {
            instance.setTargetEntityIds(
                plan.markedTargets().stream()
                    .map(LivingEntity::getId)
                    .toList()
            );
        }

        LavaKatanaReverbExecutionState executionState =
            new LavaKatanaReverbExecutionState(
                context.player().level().dimension(),
                context.nowTick(),
                ACTIVE_DURATION_TICKS
            );
        instance.initializeExecutionState(executionState);
        WeaponSkillRuntime.commitCooldown(
            context,
            instance,
            context.skillData().getCooldown() * 20
        );
        instance.registerCommittedEffect(() -> {
            if (plan.fallbackTarget() != null) {
                LavaKatanaMarkTracker.apply(
                        plan.fallbackTarget(),
                        context.player(),
                        context.nowTick(),
                        LavaKatanaMarkTracker.MARK_DURATION_TICKS,
                        context.weaponSnapshot()
                );
            } else {
                for (LivingEntity target : plan.markedTargets()) {
                    LavaKatanaMarkTracker.ensureHeatAtLeast(
                            target,
                            context.player(),
                            context.nowTick(),
                            MINIMUM_HEAT
                    );
                }
            }
            PacketDistributor.sendToPlayer(
                    context.player(),
                    new LavaKatanaReverbPayload(
                            true,
                            ACTIVE_DURATION_TICKS
                    )
            );
        });
    }

    public static boolean isActive(
        ServerPlayer player,
        long nowTick
    ) {
        if (player == null) {
            return false;
        }
        return WeaponSkillRuntime.activeExecutionState(
            player.getUUID(),
            BuiltinWeaponSkillHandlers.LAVA_KATANA_REVERB,
            LavaKatanaReverbExecutionState.class
        ).filter(state -> state.isActive(
            nowTick,
            player.level().dimension()
        )).isPresent();
    }

    @Override
    public boolean completesImmediately() {
        return false;
    }

    @Override
    public SkillTickResult tick(
        SkillExecutionContext context,
        SkillInstance instance
    ) {
        return instance.requireExecutionState(
            LavaKatanaReverbExecutionState.class
        ).advance(context);
    }

    @Override
    public void finish(
        SkillExecutionContext context,
        SkillInstance instance,
        SkillInstance.EndReason reason
    ) {
        instance.executionState(LavaKatanaReverbExecutionState.class)
            .ifPresent(LavaKatanaReverbExecutionState::cancel);
        if (shouldNotifyOnFinish(reason)) {
            PacketDistributor.sendToPlayer(
                context.player(),
                new LavaKatanaReverbPayload(false, 0)
            );
        }
    }

    static boolean canPayEnergy(
        float currentEnergy,
        boolean creativeMode,
        boolean freeEnergyBlessing
    ) {
        return creativeMode
            || freeEnergyBlessing
            || currentEnergy >= ENERGY_COST;
    }

    static boolean hasCastTarget(
        int markedTargetCount,
        boolean hasFallbackTarget
    ) {
        return markedTargetCount > 0 || hasFallbackTarget;
    }

    static boolean shouldNotifyOnFinish(
        SkillInstance.EndReason reason
    ) {
        return reason != SkillInstance.EndReason.CASTER_UNAVAILABLE;
    }

    private static boolean canPayEnergy(
        SkillExecutionContext context
    ) {
        return canPayEnergy(
            PlayerStardewDataAPI.getEnergy(context.player()),
            context.player().getAbilities().instabuild,
            context.player().hasEffect(
                ModMobEffects.STATUE_OF_BLESSINGS_2
            )
        );
    }

    private static CastPlan resolveCastPlan(
        SkillExecutionContext context
    ) {
        if (!(context.player().level() instanceof ServerLevel level)) {
            return null;
        }
        List<LivingEntity> marked =
            findMarkedTargetsInRange(
                level,
                context.player(),
                context.nowTick(),
                TARGET_RANGE
            );
        if (hasCastTarget(marked.size(), false)) {
            return new CastPlan(List.copyOf(marked), null);
        }
        LivingEntity fallback = SkillTargeting.findTargetEntity(
            context.player(),
            TARGET_RANGE
        );
        return hasCastTarget(0, fallback != null)
            ? new CastPlan(List.of(), fallback)
            : null;
    }

    private static List<LivingEntity> findMarkedTargetsInRange(
        ServerLevel level,
        Player owner,
        long nowTick,
        double range
    ) {
        AABB bounds = new AABB(
            owner.getX() - range,
            owner.getY() - range,
            owner.getZ() - range,
            owner.getX() + range,
            owner.getY() + range,
            owner.getZ() + range
        );
        return level.getEntitiesOfClass(
            LivingEntity.class,
            bounds,
            entity -> entity.isPickable()
                && entity.isAlive()
                && entity != owner
                && LavaKatanaMarkTracker.isMarkedBy(
                    entity,
                    owner,
                    nowTick
                )
        );
    }

    private record CastPlan(
        List<LivingEntity> markedTargets,
        LivingEntity fallbackTarget
    ) {}
}
