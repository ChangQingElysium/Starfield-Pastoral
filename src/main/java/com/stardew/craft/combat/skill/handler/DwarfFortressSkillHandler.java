package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.network.DwarfFortressPayload;
import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.runtime.RuntimeWeaponSkillHandler;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import com.stardew.craft.combat.skill.runtime.SkillValidation;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.player.PlayerStardewDataAPI;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server-authoritative lifecycle for Dwarf Sword's original Ley Fortress.
 */
public final class DwarfFortressSkillHandler
        implements RuntimeWeaponSkillHandler {
    public static final float ENERGY_COST = 10.0F;
    public static final int ACTIVE_DURATION_TICKS = 80;
    public static final int MAX_REACTIVE_SHOCKS = 4;
    public static final int SHELTER_AMPLIFIER = 1;
    public static final double KNOCKBACK_RESISTANCE_BONUS = 1.0D;
    public static final float INITIAL_SHOCK_RADIUS = 3.5F;
    public static final float REACTIVE_SHOCK_RADIUS = 3.0F;
    public static final float REACTIVE_DAMAGE_MULTIPLIER = 1.0F;
    public static final float ECHO_RADIUS = 4.0F;
    public static final float ECHO_DAMAGE_MULTIPLIER = 1.2F;
    public static final int HIT_CONTEXT_LIFETIME_TICKS = 5;
    public static final int RING_DURATION_TICKS = 12;

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
        return canPayEnergy(context)
                ? SkillValidation.accept()
                : SkillValidation.reject(
                        SkillValidation.RejectionReason.INVALID_STATE
                );
    }

    @Override
    public void begin(
            SkillExecutionContext context,
            SkillInstance instance
    ) {
        if (!canPayEnergy(context)) {
            throw new IllegalStateException(
                    "Validated Dwarf Fortress energy is unavailable"
            );
        }
        if (!WeaponSkillRuntime.consumeEnergyDuringBegin(
                context,
                instance,
                ENERGY_COST
        )) {
            throw new IllegalStateException(
                    "Validated Dwarf Fortress energy payment failed"
            );
        }

        DwarfFortressExecutionState executionState =
                new DwarfFortressExecutionState(
                        context.player().level().dimension(),
                        context.nowTick(),
                        ACTIVE_DURATION_TICKS,
                        context.weaponSnapshot()
        );
        instance.initializeExecutionState(executionState);
        WeaponSkillRuntime.commitCooldown(
                context,
                instance,
                context.skillData().getCooldown() * 20
        );
        instance.registerCommittedEffect(() -> {
            executionState.start(
                    context.player(),
                    ACTIVE_DURATION_TICKS,
                    context.nowTick(),
                    context.skillData().getDamagePercent() / 100.0F
            );
            PacketDistributor.sendToPlayer(
                    context.player(),
                    new DwarfFortressPayload(
                            true,
                            ACTIVE_DURATION_TICKS
                    )
            );
        });
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
                DwarfFortressExecutionState.class
        ).advance(context);
    }

    /** Triggers only this caster's exact active Ley Fortress execution. */
    public static void onDamageTaken(ServerPlayer player, long nowTick) {
        if (player == null) {
            return;
        }
        WeaponSkillRuntime.activeExecutionState(
                player.getUUID(),
                BuiltinWeaponSkillHandlers.DWARF_FORTRESS,
                DwarfFortressExecutionState.class
        ).ifPresent(state -> state.onDamageTaken(player, nowTick));
    }

    @Override
    public void finish(
            SkillExecutionContext context,
            SkillInstance instance,
            SkillInstance.EndReason reason
    ) {
        instance.executionState(DwarfFortressExecutionState.class)
                .ifPresent(state -> state.cancel(context.player()));
        if (reason != SkillInstance.EndReason.CASTER_UNAVAILABLE) {
            PacketDistributor.sendToPlayer(
                    context.player(),
                    new DwarfFortressPayload(false, 0)
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

    private static boolean canPayEnergy(SkillExecutionContext context) {
        return canPayEnergy(
                PlayerStardewDataAPI.getEnergy(context.player()),
                context.player().getAbilities().instabuild,
                context.player().hasEffect(
                        ModMobEffects.STATUE_OF_BLESSINGS_2
                )
        );
    }
}
