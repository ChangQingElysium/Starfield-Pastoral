package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.player.PlayerStardewDataAPI;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/** One Rune Guard cast's mutually exclusive hit-or-miss settlement. */
final class DwarfRuneGuardExecutionState
        implements SkillInstance.ExecutionState {
    private final UUID targetId;
    private boolean outcomeSettled;

    DwarfRuneGuardExecutionState(UUID targetId) {
        this.targetId = targetId;
    }

    synchronized boolean onAppliedHit(
            ServerPlayer player,
            LivingEntity target
    ) {
        if (outcomeSettled
                || targetId == null
                || !targetId.equals(target.getUUID())) {
            return false;
        }
        outcomeSettled = true;
        target.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN,
                DwarfRuneGuardSkillHandler.SLOW_DURATION_TICKS,
                DwarfRuneGuardSkillHandler.SLOW_AMPLIFIER,
                false,
                true,
                true
        ));
        PlayerStardewDataAPI.restoreEnergy(
                player,
                DwarfRuneGuardSkillHandler.HIT_ENERGY_RESTORE
        );
        return true;
    }

    synchronized boolean settleMiss(ServerPlayer player) {
        if (outcomeSettled) {
            return false;
        }
        outcomeSettled = true;
        PlayerStardewDataAPI.restoreEnergy(
                player,
                DwarfRuneGuardSkillHandler.MISS_ENERGY_RESTORE
        );
        return true;
    }

    synchronized boolean outcomeSettled() {
        return outcomeSettled;
    }
}
