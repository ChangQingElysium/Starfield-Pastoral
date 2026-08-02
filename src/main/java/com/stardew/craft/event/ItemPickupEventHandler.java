package com.stardew.craft.event;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.core.ModMiningDimensions;
import com.stardew.craft.api.v1.item.StardewItemDataApi;
import com.stardew.craft.network.ItemPickupHudPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;

/**
 * Sends an item-pickup HUD notification (SDV parity) when a player picks up
 * an item entity in a stardew dimension. Creative/command pickups are excluded
 * because they bypass the item entity system entirely.
 */
@EventBusSubscriber(modid = StardewCraft.MODID)
@SuppressWarnings("null")
public class ItemPickupEventHandler {

    private static final int EXPENSIVE_THRESHOLD = 500; // SDV: sellToStorePrice > 500

    @SubscribeEvent
    public static void onItemPickup(ItemEntityPickupEvent.Post event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;

        // Only in stardew dimensions
        var dim = player.level().dimension();
        if (!ModDimensions.STARDEW_VALLEY.equals(dim) && !ModMiningDimensions.STARDEW_MINING.equals(dim)) {
            return;
        }

        ItemStack original = event.getOriginalStack();
        if (original.isEmpty()) return;
        // Object 102 presents the original pickup/hold-up feedback when used
        // to advance the library, so the physical debug-friendly item doesn't
        // produce a duplicate generic pickup message.
        if (original.is(com.stardew.craft.item.ModItems.LOST_BOOK.get())) return;

        int count = original.getCount();
        boolean expensive = StardewItemDataApi.getSellPrice(original) > EXPENSIVE_THRESHOLD;

        ItemPickupHudPacket.sendTo(player, original, count, expensive);

        // Quest: item received
        String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(original.getItem()).toString();
        com.stardew.craft.quest.StardewQuestEvents.fireItemReceived(player, itemId, count);
        com.stardew.craft.specialorder.SpecialOrderManager.recordItemReceived(player, original, count);
        com.stardew.craft.festival.desert.DesertFestivalMarlonChallengeService.recordItemReceived(player, itemId, count);

        if ("stardewcraft:ectoplasm".equals(itemId)
            && com.stardew.craft.specialorder.SpecialOrderManager.hasActiveIncompleteOrder(player, "Wizard")) {
            com.stardew.craft.specialorder.SpecialOrderManager.markSpecialDropFlag(player, "ectoplasmDrop");
        } else if ("stardewcraft:prismatic_jelly".equals(itemId)
            && com.stardew.craft.specialorder.SpecialOrderManager.hasActiveIncompleteOrder(player, "Wizard2")) {
            com.stardew.craft.specialorder.SpecialOrderManager.markSpecialDropFlag(player, "prismaticJellyDrop");
        }

        // SDV parity: first time a player picks up an ore, set the corresponding
        // mail flag (e.g. "copperFound"). These flags gate early story events
        // such as Clint's furnace cutscene.
        setOreFoundFlag(player, itemId);
    }

    /** Sets {ore}Found flag once when the player first obtains that ore. */
    private static void setOreFoundFlag(ServerPlayer player, String itemId) {
        String flag = switch (itemId) {
            case "stardewcraft:copper_ore"   -> "copperFound";
            case "stardewcraft:iron_ore"     -> "ironFound";
            case "stardewcraft:gold_ore"     -> "goldFound";
            case "stardewcraft:iridium_ore"  -> "iridiumFound";
            default -> null;
        };
        if (flag == null) return;
        var data = com.stardew.craft.player.PlayerDataManager.getPlayerData(player);
        if (!data.hasMailFlag(flag)) {
            data.addMailFlag(flag);
        }
    }
}
