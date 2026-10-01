package com.stardew.craft.festival.fair;

import com.stardew.craft.network.payload.OpenFairStrengthGamePayload;
import com.stardew.craft.festival.FairFestivalService;
import com.stardew.craft.player.PlayerStardewDataAPI;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

public final class FairStrengthGameService {
    private FairStrengthGameService() {
    }

    public static void open(ServerPlayer player) {
        if (player == null) {
            return;
        }
        if (!FairFestivalService.canUseFairInteraction(player)) {
            player.displayClientMessage(Component.translatable("message.stardewcraft.fair.game.closed"), true);
            return;
        }
        int changeSpeed = 3 + player.getRandom().nextInt(2);
        PacketDistributor.sendToPlayer(player, new OpenFairStrengthGamePayload(changeSpeed));
    }

    public static void complete(ServerPlayer player, int power) {
        if (player == null || !FairFestivalService.canUseFairInteraction(player)) {
            return;
        }
        int clamped = Math.max(0, Math.min(100, power));
        if (clamped >= 99 || clamped < 2) {
            PlayerStardewDataAPI.addFairStarTokens(player, 1);
        }
    }
}
