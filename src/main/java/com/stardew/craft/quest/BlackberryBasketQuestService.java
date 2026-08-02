package com.stardew.craft.quest;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.network.ItemPickupHudPacket;
import com.stardew.craft.player.PlayerDataEventHandler;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** World pickup for vanilla story quest #107, Linus' Blackberry Basket. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class BlackberryBasketQuestService {
    public static final String QUEST_ID = "107";
    public static final String PICKED_UP_FLAG = "foundLinusBasket";
    public static final BlockPos BASKET_POS = new BlockPos(-96, 64, -71);

    private BlackberryBasketQuestService() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || event.getHand() != InteractionHand.MAIN_HAND
                || !ModDimensions.STARDEW_VALLEY.equals(player.level().dimension())
                || !isBasketSurface(event.getPos())
                || !canPickUp(player)) {
            return;
        }

        ItemStack basket = new ItemStack(ModItems.BLACKBERRY_BASKET.get());
        if (!player.getInventory().add(basket)) {
            player.drop(basket, false);
        }

        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        data.addMailFlag(PICKED_UP_FLAG);
        PlayerDataEventHandler.syncPlayerData(player, data);
        ItemPickupHudPacket.sendTo(player, basket, 1, false);
        StardewQuestEvents.fireItemReceived(player, "stardewcraft:blackberry_basket", 1);

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    public static boolean canPickUp(ServerPlayer player) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        QuestManager quests = data.getQuestManager();
        return quests.hasQuest(QUEST_ID)
                && !quests.isQuestCompleted(QUEST_ID)
                && !data.hasMailFlag(PICKED_UP_FLAG)
                && player.getInventory().countItem(ModItems.BLACKBERRY_BASKET.get()) == 0;
    }

    private static boolean isBasketSurface(BlockPos clicked) {
        return BASKET_POS.equals(clicked) || BASKET_POS.below().equals(clicked);
    }
}
