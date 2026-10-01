package com.stardew.craft.shop;

import com.stardew.craft.entity.npc.StardewNpcEntity;
import com.stardew.craft.network.payload.OpenShopScreenPayload;
import com.stardew.craft.player.PlayerStardewDataAPI;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-side handler for Sandy's Oasis shop interactions.
 * SDV parity: Data/Shops.json → "OasisShop" section.
 */
@SuppressWarnings("null")
public final class SandyService {

    // Counter area: player must be inside this AABB to trigger shop.
    // User coordinates: (-251,29,-147) to (-256,32,-148)
    private static final int COUNTER_MIN_X = -256;
    private static final int COUNTER_MAX_X = -251;
    private static final int COUNTER_MIN_Y = 29;
    private static final int COUNTER_MAX_Y = 32;
    private static final int COUNTER_MIN_Z = -148;
    private static final int COUNTER_MAX_Z = -147;

    private SandyService() {}

    public static boolean isPlayerAtCounter(ServerPlayer player) {
        int px = (int) Math.floor(player.getX());
        int py = (int) Math.floor(player.getY());
        int pz = (int) Math.floor(player.getZ());
        return px >= COUNTER_MIN_X && px <= COUNTER_MAX_X
            && py >= COUNTER_MIN_Y && py <= COUNTER_MAX_Y
            && pz >= COUNTER_MIN_Z && pz <= COUNTER_MAX_Z;
    }

    public static InteractionResult handleSandyInteraction(ServerPlayer player, StardewNpcEntity sandy) {
        // Face the player (west = -90 degrees = 270 degrees)
        sandy.setYRot(270f);
        sandy.setYHeadRot(270f);

        ShopRegistry.ShopDefinition shop = ShopRegistry.get("OasisShop");
        if (shop == null) return InteractionResult.PASS;

        int money = PlayerStardewDataAPI.getMoney(player);
        List<ShopItemEntry> items = ShopRegistry.getFilteredItemsForPlayer("OasisShop", shop, player);

        OpenShopScreenPayload payload = new OpenShopScreenPayload(
            "OasisShop", money, items,
            shop.ownerNpcId(), shop.ownerDialogue(),
            new ArrayList<>(shop.acceptedSellTypes())
        );
        PacketDistributor.sendToPlayer(player, payload);

        return InteractionResult.SUCCESS;
    }
}
