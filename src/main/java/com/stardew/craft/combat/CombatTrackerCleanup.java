package com.stardew.craft.combat;

import com.stardew.craft.combat.skill.BrokenTridentCatchTracker;
import com.stardew.craft.combat.skill.BrokenTridentThrustTracker;
import com.stardew.craft.combat.skill.CarvingKnifeThrustTracker;
import com.stardew.craft.combat.skill.ClaymoreFoldbackTracker;
import com.stardew.craft.combat.skill.CrystalDaggerLayerTracker;
import com.stardew.craft.combat.skill.DarkSwordBloodDebtTracker;
import com.stardew.craft.combat.skill.DarkSwordBloodMoonTracker;
import com.stardew.craft.combat.skill.DashMovementTracker;
import com.stardew.craft.combat.skill.DragonBreathTracker;
import com.stardew.craft.combat.skill.DragontoothShivBreathTracker;
import com.stardew.craft.combat.skill.DwarfDaggerRushTracker;
import com.stardew.craft.combat.skill.DwarfDaggerThrustTracker;
import com.stardew.craft.combat.skill.DwarfFortressTracker;
import com.stardew.craft.combat.skill.ElfBladeTracker;
import com.stardew.craft.combat.skill.EternalCollapseTracker;
import com.stardew.craft.combat.skill.FemurSlamTracker;
import com.stardew.craft.combat.skill.GalaxyDaggerThrustTracker;
import com.stardew.craft.combat.skill.HolyBladeDodgeTracker;
import com.stardew.craft.combat.skill.HolyBladeSanctuaryTracker;
import com.stardew.craft.combat.skill.InfinityDaggerThrustTracker;
import com.stardew.craft.combat.skill.InsectDashChainState;
import com.stardew.craft.combat.skill.InsectEyeStanceTracker;
import com.stardew.craft.combat.skill.IridiumNeedleCritTracker;
import com.stardew.craft.combat.skill.IridiumNeedleFrenzyTracker;
import com.stardew.craft.combat.skill.IridiumNeedleThrustTracker;
import com.stardew.craft.combat.skill.LavaKatanaMarkTracker;
import com.stardew.craft.combat.skill.LavaKatanaReverbTracker;
import com.stardew.craft.combat.skill.ObsidianCrackTracker;
import com.stardew.craft.combat.skill.ObsidianResonanceTracker;
import com.stardew.craft.combat.skill.OssifiedExecutionTracker;
import com.stardew.craft.combat.skill.RiftPathDamageTracker;
import com.stardew.craft.combat.skill.SingularityEvolveTracker;
import com.stardew.craft.combat.skill.SingularityTracker;
import com.stardew.craft.combat.skill.SilverSaberFoldbackState;
import com.stardew.craft.combat.skill.SilverSaberSkillHelper;
import com.stardew.craft.combat.skill.StarfallTracker;
import com.stardew.craft.combat.skill.StartrailTracker;
import com.stardew.craft.combat.skill.SteelFalchionLineTracker;
import com.stardew.craft.combat.skill.SteelSpineFuryState;
import com.stardew.craft.combat.skill.TemperedFireRingTracker;
import com.stardew.craft.combat.skill.TemperedQuenchTracker;
import com.stardew.craft.combat.skill.TemplarJudgementTracker;
import com.stardew.craft.combat.skill.TemplarVowTracker;
import com.stardew.craft.combat.skill.WickedKrisPoisonTracker;
import com.stardew.craft.combat.skill.WindSpireTracker;
import com.stardew.craft.combat.skill.YetiToothSpineTracker;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;

import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/**
 * Centralized cleanup for all combat tracker static maps on player logout.
 * Prevents memory leaks from UUID-keyed maps accumulating entries for disconnected players.
 */
public final class CombatTrackerCleanup {

    private CombatTrackerCleanup() {}

    /**
     * Called from PlayerDataEventHandler.onPlayerLogout().
     * Removes entries for the given player from every combat tracker.
     */
    public static void onPlayerLogout(UUID playerId) {
        WeaponSkillRuntime.removePlayer(playerId);
        clearTrackers(playerId);
    }

    /**
     * Ends runtime executions and player-backed transient state while the
     * concrete server player is still available.
     */
    public static void onPlayerUnavailable(ServerPlayer player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUUID();
        WeaponSkillRuntime.removePlayer(playerId);
        SilverSaberSkillHelper.cancelFoldback(
                player,
                player.level().getGameTime()
        );
        SilverSaberFoldbackState.clear(player);
        clearTrackers(playerId);
    }

    private static void clearTrackers(UUID playerId) {
        BrokenTridentCatchTracker.removePlayer(playerId);
        BrokenTridentThrustTracker.removePlayer(playerId);
        CarvingKnifeThrustTracker.removePlayer(playerId);
        ClaymoreFoldbackTracker.removePlayer(playerId);
        CrystalDaggerLayerTracker.removePlayer(playerId);
        DarkSwordBloodDebtTracker.removePlayer(playerId);
        DarkSwordBloodMoonTracker.removePlayer(playerId);
        DashMovementTracker.removePlayer(playerId);
        DragonBreathTracker.removePlayer(playerId);
        DragontoothShivBreathTracker.removePlayer(playerId);
        DwarfDaggerRushTracker.removePlayer(playerId);
        DwarfDaggerThrustTracker.removePlayer(playerId);
        DwarfFortressTracker.removePlayer(playerId);
        EternalCollapseTracker.removePlayer(playerId);
        FemurSlamTracker.removePlayer(playerId);
        ElfBladeTracker.removePlayer(playerId);
        GalaxyDaggerThrustTracker.removePlayer(playerId);
        HolyBladeDodgeTracker.removePlayer(playerId);
        HolyBladeSanctuaryTracker.removePlayer(playerId);
        InfinityDaggerThrustTracker.removePlayer(playerId);
        InsectDashChainState.removePlayer(playerId);
        InsectEyeStanceTracker.removePlayer(playerId);
        IridiumNeedleCritTracker.removePlayer(playerId);
        IridiumNeedleFrenzyTracker.removePlayer(playerId);
        IridiumNeedleThrustTracker.removePlayer(playerId);
        LavaKatanaMarkTracker.removePlayer(playerId);
        LavaKatanaReverbTracker.removePlayer(playerId);
        ObsidianCrackTracker.removePlayer(playerId);
        ObsidianResonanceTracker.removePlayer(playerId);
        OssifiedExecutionTracker.removePlayer(playerId);
        RiftPathDamageTracker.removePlayer(playerId);
        SingularityEvolveTracker.removePlayer(playerId);
        SingularityTracker.removePlayer(playerId);
        StarfallTracker.removePlayer(playerId);
        StartrailTracker.removePlayer(playerId);
        SteelFalchionLineTracker.removePlayer(playerId);
        SteelSpineFuryState.removePlayer(playerId);
        TemperedFireRingTracker.removePlayer(playerId);
        TemperedQuenchTracker.removePlayer(playerId);
        TemplarJudgementTracker.removePlayer(playerId);
        TemplarVowTracker.removePlayer(playerId);
        WickedKrisPoisonTracker.removePlayer(playerId);
        WindSpireTracker.removePlayer(playerId);
        YetiToothSpineTracker.removePlayer(playerId);
        AttackTargetTracker.removePlayer(playerId);
        DamageNumberContextStore.removePlayer(playerId);
        WeaponCombatEvents.removePlayer(playerId);
        com.stardew.craft.combat.equipment.EquipmentFireProtection.clear(playerId);
    }
}
