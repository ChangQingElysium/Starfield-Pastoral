package com.stardew.craft.museum;

import com.stardew.craft.StardewCraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Datapack reload and world interaction hooks for museum lost books. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class LostBookSystem {
    private LostBookSystem() {
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new LostBookRegistry.ReloadListener());
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        var bookId = LostBookRegistry.at(player.serverLevel().dimension().location(), event.getPos());
        if (bookId == null || !LostBookService.read(player, bookId)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }
}
