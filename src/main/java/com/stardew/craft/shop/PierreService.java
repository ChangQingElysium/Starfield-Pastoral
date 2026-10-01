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
 * Server-side handler for Pierre's seed shop interactions.
 * SDV parity: Data/Shops.json → "SeedShop" section.
 */
@SuppressWarnings("null")
public final class PierreService {

    // Counter area: player must be inside this AABB to trigger shop.
    // Coordinates from user: (18,38,-23) to (26,36,-20)
    private static final int COUNTER_MIN_X = 18;
    private static final int COUNTER_MAX_X = 26;
    private static final int COUNTER_MIN_Y = 36;
    private static final int COUNTER_MAX_Y = 38;
    private static final int COUNTER_MIN_Z = -23;
    private static final int COUNTER_MAX_Z = -20;

    private PierreService() {}

    public static boolean isPlayerAtCounter(ServerPlayer player) {
        int px = (int) Math.floor(player.getX());
        int py = (int) Math.floor(player.getY());
        int pz = (int) Math.floor(player.getZ());
        return px >= COUNTER_MIN_X && px <= COUNTER_MAX_X
            && py >= COUNTER_MIN_Y && py <= COUNTER_MAX_Y
            && pz >= COUNTER_MIN_Z && pz <= COUNTER_MAX_Z;
    }

    public static InteractionResult handlePierreInteraction(ServerPlayer player, StardewNpcEntity pierre) {
        pierre.setYRot(0f);
        pierre.setYHeadRot(0f);

        ShopRegistry.ShopDefinition shop = ShopRegistry.get("SeedShop");
        if (shop == null) return InteractionResult.PASS;

        int money = PlayerStardewDataAPI.getMoney(player);
        List<ShopItemEntry> items = ShopRegistry.getFilteredItemsForPlayer("SeedShop", shop, player);

        OpenShopScreenPayload payload = new OpenShopScreenPayload(
            "SeedShop", money, items,
            shop.ownerNpcId(), shop.ownerDialogue(),
            new ArrayList<>(shop.acceptedSellTypes())
        );
        PacketDistributor.sendToPlayer(player, payload);
        return InteractionResult.SUCCESS;
    }
}
