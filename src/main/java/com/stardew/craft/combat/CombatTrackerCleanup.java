package com.stardew.craft.combat;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.skill.BrokenTridentCatchTracker;
import com.stardew.craft.combat.skill.CrystalDaggerLayerTracker;
import com.stardew.craft.combat.skill.DashMovementTracker;
import com.stardew.craft.combat.skill.DragonBreathTracker;
import com.stardew.craft.combat.skill.HolyBladeDodgeTracker;
import com.stardew.craft.combat.skill.InsectDashChainState;
import com.stardew.craft.combat.skill.IridiumNeedleCritTracker;
import com.stardew.craft.combat.skill.LavaKatanaMarkTracker;
import com.stardew.craft.combat.skill.ObsidianResonanceTracker;
import com.stardew.craft.combat.skill.RiftPathDamageTracker;
import com.stardew.craft.combat.skill.SingularityTracker;
import com.stardew.craft.combat.skill.SilverSaberFoldbackState;
import com.stardew.craft.combat.skill.SilverSaberSkillHelper;
import com.stardew.craft.combat.skill.StartrailTracker;
import com.stardew.craft.combat.skill.handler.SteelFalchionDotTracker;
import com.stardew.craft.combat.skill.TemperedFireRingTracker;
import com.stardew.craft.combat.skill.TideAnchorRootTracker;
import com.stardew.craft.combat.skill.WickedKrisPoisonTracker;
import com.stardew.craft.combat.skill.WindSpireTracker;
import com.stardew.craft.combat.skill.WeaponSkillAnimationLock;
import com.stardew.craft.combat.skill.WeaponSkillContextStore;
import com.stardew.craft.combat.skill.YetiFreezeTracker;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import com.stardew.craft.combat.skill.runtime.WeaponSkillMovementArbiter;
import com.stardew.craft.combat.equipment.YobaProtectionState;

import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/**
 * Centralized cleanup for all player-backed transient combat state.
 */
public final class CombatTrackerCleanup {

    private CombatTrackerCleanup() {}

    /** Releases short-lived damage frames that never reached their Pre event. */
    public static void tickTransientFrames(long nowTick) {
        WeaponIncomingHitStore.discardExpired(nowTick);
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
        runAll(
                () -> InsectDashChainState.cancel(
                        player,
                        player.level().getGameTime()
                ),
                () -> WeaponSkillRuntime.removePlayer(playerId),
                () -> WeaponSkillContextStore.clear(player),
                () -> WeaponSkillAnimationLock.clear(player),
                () -> YetiFreezeTracker.clear(player),
                () -> TideAnchorRootTracker.clear(player),
                () -> DamageNumberContextStore.clear(player),
                () -> WeaponIncomingHitStore.clear(player),
                () -> com.stardew.craft.combat.equipment
                        .CrossDimensionCombatHandler.clear(player),
                () -> AuthoredDirectDamageContextStore.clear(player),
                () -> YobaProtectionState.clear(player),
                () -> SilverSaberSkillHelper.cancelFoldback(
                        player,
                        player.level().getGameTime()
                ),
                () -> SilverSaberFoldbackState.clear(player),
                () -> DashMovementTracker.clear(player),
                () -> DragonBreathTracker.clear(player),
                () -> clearTrackers(playerId),
                () -> WeaponSkillMovementArbiter.removePlayer(playerId)
        );
    }

    private static void clearTrackers(UUID playerId) {
        runAll(
                () -> BrokenTridentCatchTracker.removePlayer(playerId),
                () -> CrystalDaggerLayerTracker.removePlayer(playerId),
                () -> HolyBladeDodgeTracker.removePlayer(playerId),
                () -> InsectDashChainState.removePlayer(playerId),
                () -> IridiumNeedleCritTracker.removePlayer(playerId),
                () -> LavaKatanaMarkTracker.removePlayer(playerId),
                () -> ObsidianResonanceTracker.removePlayer(playerId),
                () -> RiftPathDamageTracker.removePlayer(playerId),
                () -> SingularityTracker.removePlayer(playerId),
                () -> StartrailTracker.removePlayer(playerId),
                () -> SteelFalchionDotTracker.removePlayer(playerId),
                () -> TemperedFireRingTracker.removePlayer(playerId),
                () -> WickedKrisPoisonTracker.removePlayer(playerId),
                () -> WindSpireTracker.removePlayer(playerId),
                () -> OrdinaryWeaponAttackFrameStore.clear(playerId),
                () -> StardewWeaponAttackRecovery.clear(playerId),
                () -> CombatDamageHistory.remove(playerId),
                () -> com.stardew.craft.combat.equipment
                        .EquipmentFireProtection.clear(playerId),
                () -> com.stardew.craft.combat.equipment
                        .CrossDimensionNativeAttackHandler.clear(playerId)
        );
    }

    private static void runAll(Runnable... cleanupSteps) {
        for (Runnable cleanupStep : cleanupSteps) {
            try {
                cleanupStep.run();
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error(
                        "Transient combat cleanup step failed",
                        exception
                );
            }
        }
    }
}
