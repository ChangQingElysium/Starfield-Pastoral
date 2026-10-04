package com.stardew.craft.event;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.cooking.CookingPlacedFoodBlock;
import com.stardew.craft.block.decor.PlacedFishBlock;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

/** Shift-use retrieves the serving even when vanilla would bypass the block for a held item. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class PlacedFoodInteractionEvents {
    private PlacedFoodInteractionEvents() {}

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || event.getUseBlock() == Event.Result.DENY
                || !event.getEntity().isShiftKeyDown() || !event.getEntity().mayBuild()) return;
        var block = event.getLevel().getBlockState(event.getPos()).getBlock();
        if (!(block instanceof CookingPlacedFoodBlock || block instanceof PlacedFishBlock)) return;
        // Keep client/server native dispatch and protection events; suppress held-item placement or use.
        event.setUseBlock(Event.Result.ALLOW);
        event.setUseItem(Event.Result.DENY);
    }
}
