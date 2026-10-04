package com.stardew.craft.event;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.museum.MuseumDonationItems;
import com.stardew.craft.museum.MuseumQuestService;
import com.stardew.craft.player.PlayerDataManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Catches direct-to-inventory artifact rewards that do not create an item entity. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class MuseumQuestEvents {

    /** 博物馆范围同 cutscene_events/museum_archaeology_intro.json 的 enter_area。 */
    private static final net.minecraft.world.phys.AABB MUSEUM_AREA =
            new net.minecraft.world.phys.AABB(108, 37, 29, 179, 46, 57);
    private static final java.util.Set<java.util.UUID> INSIDE_MUSEUM = new java.util.HashSet<>();

    private MuseumQuestEvents() {}

    /** 原版 LibraryMuseum.resetLocalState：每次进馆时写入 somethingToDonate / somethingWasDonated。 */
    private static void trackMuseumEntry(ServerPlayer player) {
        boolean inside = player.level().dimension() == com.stardew.craft.core.ModDimensions.STARDEW_VALLEY
                && MUSEUM_AREA.contains(player.position());
        if (!inside) {
            INSIDE_MUSEUM.remove(player.getUUID());
        } else if (INSIDE_MUSEUM.add(player.getUUID())) {
            MuseumQuestService.syncDonationMailFlags(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 5 != 0) return;
        trackMuseumEntry(player);
        if (PlayerDataManager.getPlayerData(player).hasMailFlag(MuseumQuestService.FIRST_ARTIFACT_FLAG)) return;

        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            var stack = player.getInventory().getItem(slot);
            if (MuseumDonationItems.isArtifact(stack)) {
                MuseumQuestService.onItemReceived(player, stack);
                return;
            }
        }
    }
}
