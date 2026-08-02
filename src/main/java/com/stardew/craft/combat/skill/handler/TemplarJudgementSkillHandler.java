package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.runtime.RuntimeWeaponSkillHandler;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import com.stardew.craft.combat.skill.runtime.SkillValidation;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.player.PlayerStardewDataAPI;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Server-authoritative lifecycle for Templar Blade's original judgement.
 */
public final class TemplarJudgementSkillHandler
        implements RuntimeWeaponSkillHandler {
    public static final double TARGET_RADIUS = 6.0D;
    public static final float ENERGY_COST = 10.0F;
    public static final int DURATION_TICKS = 100;
    public static final float SHARE_RATIO = 0.35F;
    public static final float MAX_HEALTH_DAMAGE_CAP_RATIO = 0.25F;
    public static final float SETTLEMENT_DAMAGE_MULTIPLIER = 1.6F;
    public static final int HIT_CONTEXT_LIFETIME_TICKS = 5;

    @Override
    public SkillValidation validate(SkillExecutionContext context) {
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
        if (WeaponSkillRuntime.hasActive(
                context.player().getUUID(),
                context.skillId()
        )) {
            return SkillValidation.reject(
                    SkillValidation.RejectionReason.INVALID_STATE
            );
        }
        if (findTargets(context).isEmpty() || !canPayEnergy(context)) {
            return SkillValidation.reject(
                    SkillValidation.RejectionReason.INVALID_STATE
            );
        }
        return SkillValidation.accept();
    }

    @Override
    public void begin(SkillExecutionContext context, SkillInstance instance) {
        List<LivingEntity> targets = findTargets(context);
        if (targets.isEmpty()) {
            throw new IllegalStateException(
                    "Validated Templar Judgement targets are no longer available"
            );
        }
        if (!canPayEnergy(context)) {
            throw new IllegalStateException(
                    "Validated Templar Judgement energy is no longer available"
            );
        }
        if (!WeaponSkillRuntime.consumeEnergyDuringBegin(
                context,
                instance,
                ENERGY_COST
        )) {
            throw new IllegalStateException(
                    "Validated Templar Judgement energy payment failed"
            );
        }

        instance.setTargetEntityIds(
                targets.stream().map(LivingEntity::getId).toList()
        );
        WeaponSkillRuntime.commitCooldown(
                context,
                instance,
                context.skillData().getCooldown() * 20
        );
        TemplarJudgementExecutionState executionState =
                new TemplarJudgementExecutionState(
                        context.player().level().dimension(),
                        context.nowTick(),
                        DURATION_TICKS,
                        targets,
                        context.weaponSnapshot()
        );
        instance.initializeExecutionState(executionState);
        instance.registerCommittedEffect(() ->
                executionState.startPresentation(
                        context.player(),
                        targets,
                        DURATION_TICKS
                )
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
        return instance.requireExecutionState(
                TemplarJudgementExecutionState.class
        ).advance(context);
    }

    @Override
    public void finish(
            SkillExecutionContext context,
            SkillInstance instance,
            SkillInstance.EndReason reason
    ) {
        instance.executionState(TemplarJudgementExecutionState.class)
                .ifPresent(TemplarJudgementExecutionState::cancel);
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
                BuiltinWeaponSkillHandlers.TEMPLAR_JUDGEMENT,
                TemplarJudgementExecutionState.class
        ).filter(state -> state.isActive(player, nowTick)).isPresent();
    }

    public static List<LivingEntity> getMarkedTargets(
            ServerPlayer player
    ) {
        if (player == null) {
            return List.of();
        }
        return WeaponSkillRuntime.activeExecutionState(
                player.getUUID(),
                BuiltinWeaponSkillHandlers.TEMPLAR_JUDGEMENT,
                TemplarJudgementExecutionState.class
        ).map(state -> state.getMarkedTargets(player))
                .orElseGet(List::of);
    }

    public static float cappedSharedDamage(
            float incomingDamage,
            float maximumHealth
    ) {
        float total = incomingDamage * SHARE_RATIO;
        float cap = maximumHealth * MAX_HEALTH_DAMAGE_CAP_RATIO;
        return cap > 0.0F ? Math.min(total, cap) : total;
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

    private static boolean canPayEnergy(SkillExecutionContext context) {
        return canPayEnergy(
                PlayerStardewDataAPI.getEnergy(context.player()),
                context.player().getAbilities().instabuild,
                context.player().hasEffect(
                        ModMobEffects.STATUE_OF_BLESSINGS_2
                )
        );
    }

    @SuppressWarnings("null")
    private static List<LivingEntity> findTargets(
            SkillExecutionContext context
    ) {
        Vec3 origin = context.player().position();
        AABB box = context.player().getBoundingBox().inflate(
                TARGET_RADIUS,
                TARGET_RADIUS * 0.75D,
                TARGET_RADIUS
        );
        return context.player().level().getEntitiesOfClass(
                LivingEntity.class,
                box,
                entity -> entity.isPickable()
                        && entity != context.player()
                        && entity.distanceToSqr(
                                origin.x,
                                origin.y,
                                origin.z
                        ) <= TARGET_RADIUS * TARGET_RADIUS
        );
    }
}
