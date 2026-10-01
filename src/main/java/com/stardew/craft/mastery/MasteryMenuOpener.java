package com.stardew.craft.mastery;

import com.stardew.craft.network.payload.OpenMasteryMenuPayload;
import com.stardew.craft.player.PlayerDataEventHandler;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.player.SkillType;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;

public final class MasteryMenuOpener {
    private MasteryMenuOpener() {}

    /** skill == null → 总览页 (which = -1)。 */
    public static void open(ServerPlayer player, SkillType skill) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        if (data != null) {
            PlayerDataEventHandler.syncPlayerData(player, data);
        }
        int which = skill == null ? -1 : skill.getId();
        PacketDistributor.sendToPlayer(player, new OpenMasteryMenuPayload(which));
    }
}
