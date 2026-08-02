package com.stardew.craft.combat.skill.runtime;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.skill.DashMovementTracker;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Single event owner for weapon-skill work that requires ServerTick.Post. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class WeaponSkillPostServerRuntime {
    private WeaponSkillPostServerRuntime() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerPlayer player : event.getServer()
                .getPlayerList().getPlayers()) {
            long nowTick = player.serverLevel().getGameTime();
            if (WeaponSkillMovementControl.isLocked(player, nowTick)) {
                WeaponSkillMovementArbiter.revokeCurrent(player);
                player.setDeltaMovement(0.0, 0.0, 0.0);
            }
        }
        DashMovementTracker.tickServer(event.getServer());
        WeaponSkillRuntime.tickPostServer(event.getServer());
    }
}
