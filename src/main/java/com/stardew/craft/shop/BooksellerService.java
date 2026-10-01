package com.stardew.craft.shop;

import com.stardew.craft.network.payload.OpenBooksellerMenuPayload;
import com.stardew.craft.network.payload.OpenShopScreenPayload;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.player.PlayerStardewDataAPI;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public final class BooksellerService {
    private static final String READ_A_BOOK_FLAG = "read_a_book";

    private BooksellerService() {
    }

    public static void handleInteraction(ServerPlayer player) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        if (!data.hasMailFlag(READ_A_BOOK_FLAG)) {
            openShop(player, "Bookseller");
            return;
        }
        PacketDistributor.sendToPlayer(player, new OpenBooksellerMenuPayload());
    }

    public static void handleMenuChoice(ServerPlayer player, int choice) {
        switch (choice) {
            case 0 -> openShop(player, "Bookseller");
            case 1 -> openShop(player, "BooksellerTrade");
            default -> {
            }
        }
    }

    private static void openShop(ServerPlayer player, String shopId) {
        ShopRegistry.ShopDefinition shop = ShopRegistry.get(shopId);
        if (shop == null) {
            return;
        }

        int money = PlayerStardewDataAPI.getMoney(player);
        List<ShopItemEntry> items = ShopRegistry.getFilteredItemsForPlayer(shopId, shop, player);
        OpenShopScreenPayload payload = new OpenShopScreenPayload(
                shopId,
                money,
                items,
                shop.ownerNpcId(),
                shop.ownerDialogue(),
                new ArrayList<>(shop.acceptedSellTypes())
        );
        PacketDistributor.sendToPlayer(player, payload);
    }
}
