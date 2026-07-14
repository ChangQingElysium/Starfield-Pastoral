package com.stardew.craft.network;

import com.stardew.craft.StardewCraft;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/** Sends the client-safe datapack snapshot on both login and {@code /reload}. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class ClientContentSyncService {
    private ClientContentSyncService() {
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        DataRegistrySyncPayload registrySnapshot = DataRegistrySyncPayload.current();
        MailIndexSyncPayload mailSnapshot = MailIndexSyncPayload.current();
        FestivalAvailabilitySyncPayload festivalSnapshot = FestivalAvailabilitySyncPayload.current();
        List<ServerPlayer> recipients = event.getRelevantPlayers().toList();

        for (ServerPlayer player : recipients) {
            PacketDistributor.sendToPlayer(player, registrySnapshot);
            PacketDistributor.sendToPlayer(player, mailSnapshot);
            PacketDistributor.sendToPlayer(player, festivalSnapshot);
            PacketDistributor.sendToPlayer(player, JeiCatalogSyncPayload.current(player));
        }

        StardewCraft.LOGGER.info("[DATA-SYNC] Sent client content snapshot to {} player(s) ({} mail entries)",
                recipients.size(), mailSnapshot.entries().size());
    }
}
