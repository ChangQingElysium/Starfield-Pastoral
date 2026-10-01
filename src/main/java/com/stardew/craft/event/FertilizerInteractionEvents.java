package com.stardew.craft.event;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.farming.FertilizerApplicationService;
import com.stardew.craft.item.FertilizerItem;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.common.util.TriState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

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
            event.setUseBlock(TriState.FALSE.toResult());
        }
    }
}
