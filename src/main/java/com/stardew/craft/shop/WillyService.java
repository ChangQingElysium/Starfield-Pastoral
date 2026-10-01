package com.stardew.craft.shop;

import com.stardew.craft.entity.npc.StardewNpcEntity;
import com.stardew.craft.network.payload.OpenShopScreenPayload;
import com.stardew.craft.player.PlayerStardewDataAPI;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-side handler for Willy's fish shop interactions.
 * SDV parity: Data/Shops.json → "FishShop" section.
 */
@SuppressWarnings("null")
public final class WillyService {

    // Counter area: (67,31,148) to (63,33,145)
    private static final int COUNTER_MIN_X = 63;
    private static final int COUNTER_MAX_X = 67;
    private static final int COUNTER_MIN_Y = 31;
    private static final int COUNTER_MAX_Y = 33;
    private static final int COUNTER_MIN_Z = 145;
    private static final int COUNTER_MAX_Z = 148;

    private WillyService() {}

    public static boolean isPlayerAtCounter(ServerPlayer player) {
        int px = (int) Math.floor(player.getX());
        int py = (int) Math.floor(player.getY());
        int pz = (int) Math.floor(player.getZ());
        return px >= COUNTER_MIN_X && px <= COUNTER_MAX_X
            && py >= COUNTER_MIN_Y && py <= COUNTER_MAX_Y
            && pz >= COUNTER_MIN_Z && pz <= COUNTER_MAX_Z;
    }

    public static InteractionResult handleWillyInteraction(ServerPlayer player, StardewNpcEntity willy) {
        willy.setYRot(0f);
        willy.setYHeadRot(0f);

        ShopRegistry.ShopDefinition shop = ShopRegistry.get("FishShop");
        if (shop == null) return InteractionResult.PASS;

        int money = PlayerStardewDataAPI.getMoney(player);
        List<ShopItemEntry> items = ShopRegistry.getFilteredItemsForPlayer("FishShop", shop, player);

        OpenShopScreenPayload payload = new OpenShopScreenPayload(
            "FishShop", money, items,
            shop.ownerNpcId(), shop.ownerDialogue(),
            new ArrayList<>(shop.acceptedSellTypes())
        );
        PacketDistributor.sendToPlayer(player, payload);
        return InteractionResult.SUCCESS;
    }
}
