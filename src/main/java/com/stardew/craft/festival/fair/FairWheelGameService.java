package com.stardew.craft.festival.fair;

import com.stardew.craft.network.payload.OpenFairWheelGamePayload;
import com.stardew.craft.festival.FairFestivalService;
import com.stardew.craft.player.PlayerStardewDataAPI;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

public final class FairWheelGameService {
    private FairWheelGameService() {
    }

    public static void open(ServerPlayer player) {
        if (player == null) {
            return;
        }
        if (!FairFestivalService.canUseFairInteraction(player)) {
            player.displayClientMessage(Component.translatable("message.stardewcraft.fair.game.closed"), true);
            return;
        }
        PacketDistributor.sendToPlayer(player, new OpenFairWheelGamePayload(
            PlayerStardewDataAPI.getFairStarTokens(player),
            PlayerStardewDataAPI.getLuckLevel(player)
        ));
    }

    public static void complete(ServerPlayer player, int wager, boolean won) {
        if (player == null || !FairFestivalService.canUseFairInteraction(player)) {
            return;
        }
        int clampedWager = Math.max(1, wager);
        if (clampedWager > PlayerStardewDataAPI.getFairStarTokens(player)) {
            return;
        }
        if (won) {
            PlayerStardewDataAPI.addFairStarTokens(player, clampedWager);
            return;
        }
        PlayerStardewDataAPI.consumeFairStarTokens(player, clampedWager);
    }
}
