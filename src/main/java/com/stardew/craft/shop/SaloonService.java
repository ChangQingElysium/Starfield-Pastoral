package com.stardew.craft.shop;

import com.stardew.craft.entity.npc.StardewNpcEntity;
import com.stardew.craft.network.payload.OpenShopScreenPayload;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.player.RecipeIdNormalizer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-side handler for Gus's saloon shop interactions.
 * SDV parity: Data/Shops.json → "Saloon" section.
 * Sells food items and cooking recipes (recipe: prefix items).
 */
@SuppressWarnings("null")
public final class SaloonService {

    // Counter area: player must be inside this AABB to trigger shop.
    // Coordinates from user: (22,38,13) to (32,36,15)
    private static final int COUNTER_MIN_X = 22;
    private static final int COUNTER_MAX_X = 32;
    private static final int COUNTER_MIN_Y = 36;
    private static final int COUNTER_MAX_Y = 38;
    private static final int COUNTER_MIN_Z = 13;
    private static final int COUNTER_MAX_Z = 15;

    private SaloonService() {}

    public static boolean isPlayerAtCounter(ServerPlayer player) {
        int px = (int) Math.floor(player.getX());
        int py = (int) Math.floor(player.getY());
        int pz = (int) Math.floor(player.getZ());
        return px >= COUNTER_MIN_X && px <= COUNTER_MAX_X
            && py >= COUNTER_MIN_Y && py <= COUNTER_MAX_Y
            && pz >= COUNTER_MIN_Z && pz <= COUNTER_MAX_Z;
    }

    public static InteractionResult handleSaloonInteraction(ServerPlayer player, StardewNpcEntity gus) {
        gus.setYRot(0f);
        gus.setYHeadRot(0f);

        ShopRegistry.ShopDefinition shop = ShopRegistry.get("Saloon");
        if (shop == null) return InteractionResult.PASS;

        int money = PlayerStardewDataAPI.getMoney(player);
        List<ShopItemEntry> items = ShopRegistry.getFilteredItemsForPlayer("Saloon", shop, player);

        OpenShopScreenPayload payload = new OpenShopScreenPayload(
            "Saloon", money, items,
            shop.ownerNpcId(), shop.ownerDialogue(),
            new ArrayList<>(shop.acceptedSellTypes())
        );
        PacketDistributor.sendToPlayer(player, payload);
        return InteractionResult.SUCCESS;
    }

    /** Converts a shop recipe pseudo ID to the player-storage representation. */
    public static String extractRecipeId(String itemId) {
        return RecipeIdNormalizer.storageId(itemId);
    }
}
