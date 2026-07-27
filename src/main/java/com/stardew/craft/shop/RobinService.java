package com.stardew.craft.shop;

import com.stardew.craft.entity.npc.StardewNpcEntity;
import com.stardew.craft.network.payload.OpenRobinMenuPayload;
import com.stardew.craft.network.payload.OpenShopScreenPayload;
import com.stardew.craft.api.v1.building.StardewBuildingBlueprints;
import com.stardew.craft.api.v1.building.StardewBuildingBuilders;
import com.stardew.craft.player.PlayerStardewDataAPI;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Robin's carpenter service. Handles counter detection, choice dialogue,
 * building purchase menu, and material shop.
 * Follows the same multi-option pattern as BlacksmithService.
 */
@SuppressWarnings("null")
public final class RobinService {

    // Counter AABB (world coordinates)
    private static final int COUNTER_MIN_X = 28, COUNTER_MAX_X = 33;
    private static final int COUNTER_MIN_Y = 51, COUNTER_MAX_Y = 53;
    private static final int COUNTER_MIN_Z = -120, COUNTER_MAX_Z = -117;

    private RobinService() {}

    /** @deprecated use {@link StardewBuildingBlueprints#forBuilder}. */
    @Deprecated(forRemoval = false)
    public static List<CarpenterBlueprint> getBlueprints() {
        return com.stardew.craft.building.BuildingBlueprintRegistry
                .forBuilder(StardewBuildingBuilders.ROBIN).stream()
                .map(CarpenterBlueprint::from)
                .toList();
    }

    public static boolean isPlayerAtCounter(ServerPlayer player) {
        int px = (int) Math.floor(player.getX());
        int py = (int) Math.floor(player.getY());
        int pz = (int) Math.floor(player.getZ());
        return px >= COUNTER_MIN_X && px <= COUNTER_MAX_X
            && py >= COUNTER_MIN_Y && py <= COUNTER_MAX_Y
            && pz >= COUNTER_MIN_Z && pz <= COUNTER_MAX_Z;
    }

    /**
     * Entry point: called from NpcInteractionService when player interacts
     * with Robin at her counter. Sends choice dialogue (Build / Shop / Leave).
     */
    public static InteractionResult handleCarpenterInteraction(ServerPlayer player, StardewNpcEntity robin) {
        robin.setYRot(0f);
        robin.setYHeadRot(0f);

        // Send choice dialogue to client (like BlacksmithService)
        PacketDistributor.sendToPlayer(player, new OpenRobinMenuPayload());
        return InteractionResult.SUCCESS;
    }

    /**
     * Dispatches the player's menu choice.
     * 0 = Build (open CarpenterMenu), 1 = Shop (open material shop), 2 = Leave
     */
    public static void handleMenuChoice(ServerPlayer player, int choice) {
        switch (choice) {
            case 0 -> openCarpenterMenu(player);
            case 1 -> openCarpenterShop(player);
            // 2 = Leave, do nothing
        }
    }

    // ──── Build (CarpenterMenu) ────

    private static void openCarpenterMenu(ServerPlayer player) {
        StardewBuildingBlueprints.open(
                player, StardewBuildingBuilders.ROBIN);
    }

    // ──── Shop (material purchase via ShopScreen) ────

    private static void openCarpenterShop(ServerPlayer player) {
        ShopRegistry.ShopDefinition shop = ShopRegistry.get("CarpenterShop");
        if (shop == null) return;

        int money = PlayerStardewDataAPI.getMoney(player);
        List<ShopItemEntry> items = ShopRegistry.getFilteredItemsForPlayer("CarpenterShop", shop, player);

        OpenShopScreenPayload payload = new OpenShopScreenPayload(
            "CarpenterShop", money, items,
            shop.ownerNpcId(), shop.ownerDialogue(),
            new ArrayList<>(shop.acceptedSellTypes())
        );
        PacketDistributor.sendToPlayer(player, payload);
    }
}
