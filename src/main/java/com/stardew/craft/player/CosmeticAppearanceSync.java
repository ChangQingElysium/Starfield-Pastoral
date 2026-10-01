package com.stardew.craft.player;

import com.stardew.craft.network.payload.CosmeticAppearanceSyncPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

public final class CosmeticAppearanceSync {
    private CosmeticAppearanceSync() {
    }

    public static void sendToPlayer(ServerPlayer recipient, ServerPlayer subject, PlayerStardewData data) {
        PacketDistributor.sendToPlayer(recipient, payload(subject, data));
    }

    public static void broadcast(ServerPlayer subject, PlayerStardewData data) {
        PacketDistributor.sendToAllPlayers(payload(subject, data));
    }

    public static void syncAllTo(ServerPlayer recipient) {
        for (ServerPlayer subject : recipient.server.getPlayerList().getPlayers()) {
            sendToPlayer(recipient, subject, PlayerDataManager.getPlayerData(subject));
        }
    }

    private static CosmeticAppearanceSyncPayload payload(ServerPlayer subject, PlayerStardewData data) {
        return new CosmeticAppearanceSyncPayload(
                subject.getUUID(),
                data.getEquippedHat(),
                data.getEquippedShirt(),
                data.getEquippedPants()
        );
    }
}
