package com.stardew.craft.world.interaction;

import com.stardew.craft.StardewCraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Server-authoritative handler for authored map actions. HIGHEST-priority
 * cutscene/festival locks and debug tools run first; matching map definitions
 * consume the click before normal block behavior. Unmatched definitions leave
 * block behavior alone.
 */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class MapInteractionEvents {
    private MapInteractionEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(
            PlayerInteractEvent.RightClickBlock event
    ) {
        if (event.isCanceled()
                || event.getHand() != InteractionHand.MAIN_HAND
                || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        InteractionResult result = MapInteractionService.interact(
                player,
                event.getHand(),
                event.getHitVec());
        if (result == InteractionResult.PASS) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(result);
    }
}
