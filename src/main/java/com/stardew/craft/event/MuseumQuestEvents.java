package com.stardew.craft.event;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.museum.MuseumDonationItems;
import com.stardew.craft.museum.MuseumQuestService;
import com.stardew.craft.player.PlayerDataManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Catches direct-to-inventory artifact rewards that do not create an item entity. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class MuseumQuestEvents {

    private MuseumQuestEvents() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 5 != 0) return;
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
