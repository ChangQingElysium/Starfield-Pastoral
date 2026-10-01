package com.stardew.craft.festival.fair;

import com.stardew.craft.festival.FairFestivalService;
import com.stardew.craft.network.ObjectDialogueService;
import com.stardew.craft.network.payload.OpenFairSlingshotGamePayload;
import com.stardew.craft.player.PlayerStardewDataAPI;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

public final class FairSlingshotGameService {
    public static final String TARGET_ID = "fair_slingshot_game";
    public static final String MARKER_TAG = "fair_slingshot_game";
    private static final int ENTRY_COST = 50;

    private FairSlingshotGameService() {
    }

    public static void open(ServerPlayer player) {
        if (player == null) {
            return;
        }
        if (!FairFestivalService.canUseFairInteraction(player)) {
            player.displayClientMessage(Component.translatable("message.stardewcraft.fair.game.closed"), true);
            return;
        }
        PacketDistributor.sendToPlayer(player, new OpenFairSlingshotGamePayload(
            PlayerStardewDataAPI.getFairStarTokens(player),
            false
        ));
    }

    public static void start(ServerPlayer player) {
        if (player == null || !FairFestivalService.canUseFairInteraction(player)) {
            return;
        }
        if (PlayerStardewDataAPI.getMoney(player) < ENTRY_COST || !PlayerStardewDataAPI.removeMoney(player, ENTRY_COST)) {
            ObjectDialogueService.show(player, "stardewcraft.fair.slingshot.no_money");
            return;
        }
        PacketDistributor.sendToPlayer(player, new OpenFairSlingshotGamePayload(
            PlayerStardewDataAPI.getFairStarTokens(player),
            true
        ));
    }

    public static void complete(ServerPlayer player, int score) {
        if (player == null || !FairFestivalService.canUseFairInteraction(player)) {
            return;
        }
        int tokens = starTokensForScore(Math.max(0, Math.min(9999, score)));
        if (tokens > 0) {
            PlayerStardewDataAPI.addFairStarTokens(player, tokens);
        }
    }

    private static int starTokensForScore(int score) {
        if (score < 40) {
            return 0;
        }
        int tokens = (int) (((score * 2 - 30) / 10) * 2.5F);
        tokens *= 2;
        return tokens > 280 ? 500 : tokens;
    }
}
