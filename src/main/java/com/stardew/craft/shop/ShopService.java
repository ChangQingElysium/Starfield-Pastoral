package com.stardew.craft.shop;

import com.stardew.craft.network.payload.OpenShopScreenPayload;
import com.stardew.craft.player.PlayerStardewDataAPI;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;

/** Shared server entry point for opening a registered shop. */
public final class ShopService {
    private ShopService() {
    }

    public static boolean open(ServerPlayer player, String shopId) {
        ShopRegistry.ShopDefinition shop = ShopRegistry.get(shopId);
        if (shop == null) return false;
        PacketDistributor.sendToPlayer(player, new OpenShopScreenPayload(
                shop.shopId(),
                PlayerStardewDataAPI.getMoney(player),
                ShopRegistry.getFilteredItemsForPlayer(shop.shopId(), shop, player),
                shop.ownerNpcId(),
                shop.ownerDialogue(),
                new ArrayList<>(shop.acceptedSellTypes())
        ));
        return true;
    }
}
