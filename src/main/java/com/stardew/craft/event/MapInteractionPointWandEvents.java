package com.stardew.craft.event;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.item.tool.PointPlanWandItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Lets the map-point debug wand capture interactive blocks before their own
 * right-click behavior consumes the click.
 */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class MapInteractionPointWandEvents {
    private MapInteractionPointWandEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(
            PlayerInteractEvent.RightClickBlock event
    ) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (!PointPlanWandItem.isMapInteractionEditor(stack)
                || !(event.getEntity()
                        instanceof ServerPlayer player)) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        PointPlanWandItem.captureMapInteractionPoint(
                player,
                stack,
                event.getPos());
    }
}
