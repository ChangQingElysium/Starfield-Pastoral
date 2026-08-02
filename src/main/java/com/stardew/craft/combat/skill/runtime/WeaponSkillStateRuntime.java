package com.stardew.craft.combat.skill.runtime;

import com.stardew.craft.combat.skill.BrokenTridentCatchTracker;
import com.stardew.craft.combat.skill.CrystalDaggerLayerTracker;
import com.stardew.craft.combat.skill.InsectDashChainState;
import com.stardew.craft.combat.skill.ObsidianResonanceTracker;
import com.stardew.craft.combat.skill.RiftPathDamageTracker;
import com.stardew.craft.combat.skill.SilverSaberSkillHelper;
import com.stardew.craft.combat.skill.handler.SteelFalchionDotTracker;
import com.stardew.craft.combat.skill.TemperedFireRingTracker;
import com.stardew.craft.combat.skill.WindSpireTracker;
import net.minecraft.server.level.ServerPlayer;

/**
 * Owns weapon state that intentionally outlives one active skill execution.
 *
 * <p>This includes shared timed statuses, passive charging and detached child
 * effects. Active line/trace movement remains with its handler; only the
 * residual Steel Falchion DOT is advanced here.</p>
 */
public final class WeaponSkillStateRuntime {
    private WeaponSkillStateRuntime() {
    }

    public static void tickPlayer(ServerPlayer player, long nowTick) {
        BrokenTridentCatchTracker.tick(player, nowTick);
        CrystalDaggerLayerTracker.tick(player, nowTick);
        InsectDashChainState.tick(player, nowTick);
        ObsidianResonanceTracker.tick(player, nowTick);
        SilverSaberSkillHelper.tickPersistedFoldback(player, nowTick);
        TemperedFireRingTracker.tick(player, nowTick);
        SteelFalchionDotTracker.tickDetachedEffects(player, nowTick);
        RiftPathDamageTracker.tick(player, nowTick);
        WindSpireTracker.tick(player, nowTick);
    }
}
