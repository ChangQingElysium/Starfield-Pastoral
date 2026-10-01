package com.stardew.craft.shop;

import com.stardew.craft.animal.runtime.LivestockShop;
import com.stardew.craft.entity.npc.StardewNpcEntity;
import com.stardew.craft.network.payload.OpenShopScreenPayload;
import com.stardew.craft.network.payload.OpenMarnieMenuPayload;
import com.stardew.craft.player.PlayerStardewDataAPI;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-side handler for Marnie's animal shop interactions.
 * SDV parity: Marnie shows a question dialog with "Supplies" / "Purchase Animals" / "Leave".
 * - Supplies → opens the "AnimalShop" item shop (hay, milk pail, shears, etc.)
 * - Purchase Animals → opens the animal purchase screen
 */
@SuppressWarnings("null")
public final class MarnieService {
    private static final java.util.Map<ServerPlayer, Long> OPEN_MENUS = new java.util.WeakHashMap<>();

    // Counter area: player must be inside this AABB to trigger shop.
    // Coordinates from user: (-90,36,22) to (-85,34,25)
    private static final int COUNTER_MIN_X = -90;
    private static final int COUNTER_MAX_X = -85;
    private static final int COUNTER_MIN_Y = 34;
    private static final int COUNTER_MAX_Y = 36;
    private static final int COUNTER_MIN_Z = 22;
    private static final int COUNTER_MAX_Z = 25;

    private MarnieService() {}

    public static boolean isPlayerAtCounter(ServerPlayer player) {
        int px = (int) Math.floor(player.getX());
        int py = (int) Math.floor(player.getY());
        int pz = (int) Math.floor(player.getZ());
        return px >= COUNTER_MIN_X && px <= COUNTER_MAX_X
            && py >= COUNTER_MIN_Y && py <= COUNTER_MAX_Y
            && pz >= COUNTER_MIN_Z && pz <= COUNTER_MAX_Z;
    }

    /**
     * Step 1: Send the question dialog to the client.
     */
    public static InteractionResult handleMarnieInteraction(ServerPlayer player, StardewNpcEntity marnie) {
        marnie.setYRot(0f);
        marnie.setYHeadRot(0f);

        openMenu(player);
        return InteractionResult.SUCCESS;
    }

    public static void openMenu(ServerPlayer player) {
        OPEN_MENUS.put(player, player.serverLevel().getServer().getTickCount() + 6000L);
        PacketDistributor.sendToPlayer(player, new OpenMarnieMenuPayload(com.stardew.craft.pet.PetManagement.unlocked(player)));
    }

    /**
     * Step 2: Handle the player's choice from the question dialog.
     * Called from MarnieMenuChoicePayload.
     */
    public static void handleChoice(ServerPlayer player, int choice) {
        Long expires = OPEN_MENUS.remove(player);
        if (expires == null || expires < player.serverLevel().getServer().getTickCount()) return;
        switch (choice) {
            case 0 -> openSupplies(player);
            case 1 -> openAnimalPurchase(player);
            case 3 -> {
                if (com.stardew.craft.pet.PetManagement.unlocked(player)) {
                    com.stardew.craft.pet.PetManagement.openShop(player);
                }
            }
            // 2 = Leave, do nothing
        }
    }

    private static void openSupplies(ServerPlayer player) {
        ShopRegistry.ShopDefinition shop = ShopRegistry.get("AnimalShop");
        if (shop == null) return;

        int money = PlayerStardewDataAPI.getMoney(player);
        List<ShopItemEntry> items = ShopRegistry.getFilteredItemsForPlayer("AnimalShop", shop, player);

        OpenShopScreenPayload payload = new OpenShopScreenPayload(
            "AnimalShop", money, items,
            shop.ownerNpcId(), shop.ownerDialogue(),
            new ArrayList<>(shop.acceptedSellTypes())
        );
        PacketDistributor.sendToPlayer(player, payload);
    }

    private static void openAnimalPurchase(ServerPlayer player) {
        LivestockShop.openForPlayer(player);
    }
}
