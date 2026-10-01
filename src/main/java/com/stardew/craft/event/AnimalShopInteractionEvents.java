package com.stardew.craft.event;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.animal.runtime.LivestockShop;
import com.stardew.craft.core.ModDimensions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = StardewCraft.MODID)
public class AnimalShopInteractionEvents {

    /**
     * Legacy handler for Villager-type Marnie entity.
     * Marnie is now a StardewNpcEntity and handled via NpcInteractionService + MarnieService.
     * This handler is retained only for vanilla Villager fallback (should not trigger in practice).
     */
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!ModDimensions.STARDEW_VALLEY.equals(player.serverLevel().dimension())) {
            return;
        }
        // Only handle vanilla Villager entities, not StardewNpcEntity
        // (StardewNpcEntity Marnie is handled by NpcInteractionService → MarnieService)
        if (!(event.getTarget() instanceof Villager villager)) {
            return;
        }
        if (event.getTarget() instanceof com.stardew.craft.entity.npc.StardewNpcEntity) {
            return;
        }

        String name = villager.getName().getString();
        if (!"Marnie".equalsIgnoreCase(name)) {
            return;
        }

        LivestockShop.openForPlayer(player);
        event.setCanceled(true);
    }

}
