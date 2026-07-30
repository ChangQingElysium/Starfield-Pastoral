package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.network.LavaKatanaReverbPayload;
import com.stardew.craft.combat.skill.LavaKatanaMarkTracker;
import com.stardew.craft.combat.skill.LavaKatanaReverbTracker;
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
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server-authoritative lifecycle for Lava Katana's original Molten Reverb.
 */
public final class LavaKatanaReverbSkillHandler
        implements RuntimeWeaponSkillHandler {
    public static final float ENERGY_COST = 12.0F;

    @Override
    public SkillValidation validate(SkillExecutionContext context) {
        if (WeaponSkillRuntime.hasActive(
            context.player().getUUID(),
            context.skillId()
        ) || LavaKatanaReverbTracker.hasState(context.player())) {
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
        if (!context.player().getAbilities().instabuild
            && !PlayerStardewDataAPI.consumeEnergy(
                context.player(),
                ENERGY_COST
            )) {
            throw new IllegalStateException(
                "Validated Molten Reverb energy payment failed"
            );
        }

        if (plan.fallbackTarget() != null) {
            LivingEntity target = plan.fallbackTarget();
            instance.setTargetEntityIds(List.of(target.getId()));
            LavaKatanaMarkTracker.apply(
                target,
                context.player(),
                context.nowTick(),
                LavaKatanaMarkTracker.MARK_DURATION_TICKS,
                context.weaponSnapshot()
            );
        } else {
            instance.setTargetEntityIds(
                plan.markedTargets().stream()
                    .map(LivingEntity::getId)
                    .toList()
            );
            for (LivingEntity target : plan.markedTargets()) {
                LavaKatanaMarkTracker.ensureHeatAtLeast(
                    target,
                    context.player(),
                    context.nowTick(),
                    LavaKatanaReverbTracker.MINIMUM_HEAT
                );
            }
        }

        LavaKatanaReverbTracker.start(
            context.player(),
            context.nowTick(),
            LavaKatanaReverbTracker.ACTIVE_DURATION_TICKS,
            context.weaponSnapshot()
        );
        PacketDistributor.sendToPlayer(
            context.player(),
            new LavaKatanaReverbPayload(
                true,
                LavaKatanaReverbTracker.ACTIVE_DURATION_TICKS
            )
        );
        WeaponSkillCooldowns.setCooldown(
            context.player(),
            context.weaponId().getPath(),
            context.skillData().getId(),
            context.nowTick(),
            context.skillData().getCooldown() * 20
        );
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
        return switch (LavaKatanaReverbTracker.tick(
            context.player(),
            context.nowTick()
        )) {
            case ACTIVE -> SkillTickResult.CONTINUE;
            case COMPLETED -> SkillTickResult.COMPLETE;
            case INVALIDATED -> SkillTickResult.CANCEL;
        };
    }

    @Override
    public void finish(
        SkillExecutionContext context,
        SkillInstance instance,
        SkillInstance.EndReason reason
    ) {
        LavaKatanaReverbTracker.cancel(context.player());
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
            LavaKatanaReverbTracker.findMarkedTargetsInRange(
                level,
                context.player(),
                context.nowTick(),
                LavaKatanaReverbTracker.TARGET_RANGE
            );
        if (hasCastTarget(marked.size(), false)) {
            return new CastPlan(List.copyOf(marked), null);
        }
        LivingEntity fallback = SkillTargeting.findTargetEntity(
            context.player(),
            LavaKatanaReverbTracker.TARGET_RANGE
        );
        return hasCastTarget(0, fallback != null)
            ? new CastPlan(List.of(), fallback)
            : null;
    }

    private record CastPlan(
        List<LivingEntity> markedTargets,
        LivingEntity fallbackTarget
    ) {}
}
