package com.stardew.craft.event;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.farming.FertilizerApplicationService;
import com.stardew.craft.item.FertilizerItem;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Ensures crop blocks cannot consume a fertilizer click before the held item handles it. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class FertilizerInteractionEvents {
    private FertilizerInteractionEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getItemStack().getItem() instanceof FertilizerItem)) {
            return;
        }
        if (FertilizerApplicationService.resolveTarget(event.getLevel(), event.getPos()) != null) {
            event.setUseBlock(TriState.FALSE);
        }
    }
}
