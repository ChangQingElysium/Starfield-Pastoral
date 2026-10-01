package com.stardew.craft.event;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.museum.MuseumDonationData;
import com.stardew.craft.network.MuseumDonationSyncPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.event.entity.player.PlayerEvent;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Sync museum donation data to clients.
 */
@EventBusSubscriber(modid = StardewCraft.MODID)
public class MuseumDonationSyncEvents {

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncToPlayer(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        // Museum is in a separate interior dimension; stand data is keyed by dimension so
        // re-sync whenever the player crosses dimensions, otherwise stands appear empty.
        if (event.getEntity() instanceof ServerPlayer player) {
            syncToPlayer(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncToPlayer(player);
        }
    }

    private static void syncToPlayer(ServerPlayer player) {
        MuseumDonationData data = MuseumDonationData.get(player.serverLevel());
        PacketDistributor.sendToPlayer(player,
                new MuseumDonationSyncPacket(List.copyOf(data.getDonatedItems(player.getUUID()))));
        com.stardew.craft.block.utility.MuseumExhibitStandBlock.ensureAndSyncStands(player.serverLevel(), data, player);
    }

    @EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
    public static class ClientEvents {
        @SubscribeEvent
        public static void onClientDisconnect(PlayerEvent.PlayerLoggedOutEvent event) {
            com.stardew.craft.client.ClientMuseumDonationCache.clear();
            com.stardew.craft.client.ClientMuseumStandCache.clear();
        }
    }
}
