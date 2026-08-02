package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.network.DarkSwordBloodMoonPayload;
import com.stardew.craft.combat.WeaponStats;
import com.stardew.craft.combat.skill.DarkSwordEffects;
import com.stardew.craft.combat.skill.WeaponSkillAnimationDispatcher;
import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.runtime.RuntimeWeaponSkillHandler;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import com.stardew.craft.combat.skill.runtime.SkillValidation;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.player.PlayerStardewDataAPI;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server-authoritative lifecycle for Dark Sword's original Blood Moon Harvest.
 */
public final class DarkSwordBloodMoonSkillHandler
        implements RuntimeWeaponSkillHandler {
    public static final float ENERGY_COST = 10.0F;
    public static final int ACTIVE_DURATION_TICKS = 80;
    public static final int BURN_INTERVAL_TICKS = 10;
    public static final int PRESENTATION_NOTIFICATION_TICKS = 1;
    public static final float LIFESTEAL_RATIO = 0.30F;
    public static final float DAMAGE_BONUS_MULTIPLIER = 1.35F;
    public static final float BURN_MAXIMUM_HEALTH_RATIO = 0.01F;
    public static final float MINIMUM_BURN_AMOUNT = 1.0F;
    public static final float MINIMUM_REMAINING_HEALTH = 1.0F;
    public static final float BURST_RADIUS = 3.5F;
    public static final float MINIMUM_BURST_DAMAGE_MULTIPLIER = 0.1F;
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
        return canPayEnergy(context)
                ? SkillValidation.accept()
                : SkillValidation.reject(
                        SkillValidation.RejectionReason.INVALID_STATE
                );
    }

    @Override
    public void begin(SkillExecutionContext context, SkillInstance instance) {
        if (!canPayEnergy(context)) {
            throw new IllegalStateException(
                    "Validated Blood Moon energy is no longer available"
            );
        }
        if (!WeaponSkillRuntime.consumeEnergyDuringBegin(
                context,
                instance,
                ENERGY_COST
        )) {
            throw new IllegalStateException(
                    "Validated Blood Moon energy payment failed"
            );
        }

        String weaponId = context.weaponId().getPath();
        String skillId = context.skillData().getId();
        instance.initializeExecutionState(
                new DarkSwordBloodMoonExecutionState(
                        context.nowTick(),
                        ACTIVE_DURATION_TICKS,
                        BURN_INTERVAL_TICKS,
                        context.player().level().dimension(),
                        WeaponStats.fromItemStack(
                                context.weapon()
                        ).getAverageDamage(),
                        context.weaponSnapshot()
                )
        );
        instance.registerCommittedEffect(() -> {
            DarkSwordEffects.playBloodMoonStart(context.player());
            PacketDistributor.sendToPlayer(
                    context.player(),
                    new DarkSwordBloodMoonPayload(
                            true,
                            ACTIVE_DURATION_TICKS
                    )
            );
        });

        // The authored skill had no attack lock or held-item motion, but its
        // one-shot local cast effect still needs a server-authored notification.
        WeaponSkillAnimationDispatcher.sendSkillAnim(
                context.player(),
                weaponId,
                skillId,
                PRESENTATION_NOTIFICATION_TICKS
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
                DarkSwordBloodMoonExecutionState.class
        ).advance(context);
    }

    @Override
    public void finish(
            SkillExecutionContext context,
            SkillInstance instance,
            SkillInstance.EndReason reason
    ) {
        Optional<DarkSwordBloodMoonExecutionState> executionState =
                instance.executionState(
                        DarkSwordBloodMoonExecutionState.class
                );
        boolean stateWasActive = executionState.isPresent();
        executionState.ifPresent(
                DarkSwordBloodMoonExecutionState::cancel
        );
        boolean commitCooldown = shouldCommitCooldown(
                reason,
                stateWasActive
        );
        if (commitCooldown) {
            WeaponSkillRuntime.commitCooldown(
                    context,
                    instance,
                    context.skillData().getCooldown() * 20
            );
        }
        if (commitCooldown
                && reason != SkillInstance.EndReason.CASTER_UNAVAILABLE) {
            PacketDistributor.sendToPlayer(
                    context.player(),
                    new DarkSwordBloodMoonPayload(false, 0)
            );
        }
    }

    static boolean shouldCommitCooldown(
            SkillInstance.EndReason reason,
            boolean stateWasActive
    ) {
        return stateWasActive
                || reason == SkillInstance.EndReason.COMPLETED
                || reason == SkillInstance.EndReason.CASTER_UNAVAILABLE;
    }

    /** Read-only combat facade for the exact active Blood Moon execution. */
    public static boolean isActive(ServerPlayer player, long nowTick) {
        return activeState(player)
                .filter(state -> state.isActive(
                        nowTick,
                        player.isAlive() && !player.isRemoved(),
                        player.level().dimension()
                ))
                .isPresent();
    }

    public static float getDamageBonusMultiplier(
            ServerPlayer player,
            long nowTick
    ) {
        return isActive(player, nowTick)
                ? DAMAGE_BONUS_MULTIPLIER
                : 1.0F;
    }

    public static float getLifestealRatio(
            ServerPlayer player,
            long nowTick
    ) {
        return isActive(player, nowTick) ? LIFESTEAL_RATIO : 0.0F;
    }

    public static void recordLifeSteal(
            ServerPlayer player,
            long nowTick,
            float healedAmount
    ) {
        if (player == null) {
            return;
        }
        activeState(player).ifPresent(state ->
                state.recordLifeSteal(
                        nowTick,
                        player.isAlive() && !player.isRemoved(),
                        player.level().dimension(),
                        healedAmount
                )
        );
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

    private static Optional<DarkSwordBloodMoonExecutionState> activeState(
            ServerPlayer player
    ) {
        if (player == null) {
            return Optional.empty();
        }
        return WeaponSkillRuntime.activeExecutionState(
                player.getUUID(),
                BuiltinWeaponSkillHandlers.DARK_SWORD_BLOOD_MOON,
                DarkSwordBloodMoonExecutionState.class
        );
    }
}
