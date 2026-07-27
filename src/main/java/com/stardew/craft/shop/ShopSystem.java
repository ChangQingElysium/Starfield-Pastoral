package com.stardew.craft.shop;

import com.stardew.craft.StardewCraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Reload hooks and generic world interaction binding for datapack shops. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class ShopSystem {
    private ShopSystem() {
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new ShopDataLoader.ShopReloadListener());
        event.addListener(new ShopDataLoader.BindingReloadListener());
        event.addListener(new ShopDataLoader.CostRuleReloadListener());
        event.addListener(new ShopDataLoader.StockRuleReloadListener());
        event.addListener(new ShopDataLoader.ProductRuleReloadListener());
        event.addListener(new com.stardew.craft.building
                .BuildingBlueprintRegistry.ReloadListener());
        event.addListener(new MonsterSlayerGoalRegistry.ReloadListener());
        event.addListener(new com.stardew.craft.museum.MuseumRewardRegistry.ReloadListener());
        event.addListener(new GeodeDropData.ReloadListener());
        event.addListener(new PrizeTicketRewardData.ReloadListener());
        event.addListener(new com.stardew.craft.mining.MineChestRewardData.ReloadListener());
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (ShopInteractionBindings.tryOpenBlock(player, event.getPos())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }
}
