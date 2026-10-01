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
 * Server-side handler for Harvey's clinic shop interactions.
 * SDV parity: Data/Shops.json → "Hospital" section.
 */
@SuppressWarnings("null")
public final class ClinicService {

    // Counter area: player must be inside this AABB to trigger shop.
    // Coordinates from user: (0,45,-13) to (7,43,-11)
    private static final int COUNTER_MIN_X = 0;
    private static final int COUNTER_MAX_X = 7;
    private static final int COUNTER_MIN_Y = 43;
    private static final int COUNTER_MAX_Y = 45;
    private static final int COUNTER_MIN_Z = -13;
    private static final int COUNTER_MAX_Z = -11;

    private ClinicService() {}

    public static boolean isPlayerAtCounter(ServerPlayer player) {
        int px = (int) Math.floor(player.getX());
        int py = (int) Math.floor(player.getY());
        int pz = (int) Math.floor(player.getZ());
        return px >= COUNTER_MIN_X && px <= COUNTER_MAX_X
            && py >= COUNTER_MIN_Y && py <= COUNTER_MAX_Y
            && pz >= COUNTER_MIN_Z && pz <= COUNTER_MAX_Z;
    }

    public static InteractionResult handleClinicInteraction(ServerPlayer player, StardewNpcEntity harvey) {
        harvey.setYRot(0f);
        harvey.setYHeadRot(0f);

        ShopRegistry.ShopDefinition shop = ShopRegistry.get("Hospital");
        if (shop == null) return InteractionResult.PASS;

        int money = PlayerStardewDataAPI.getMoney(player);
        List<ShopItemEntry> items = ShopRegistry.getFilteredItemsForPlayer("Hospital", shop, player);

        OpenShopScreenPayload payload = new OpenShopScreenPayload(
            "Hospital", money, items,
            shop.ownerNpcId(), shop.ownerDialogue(),
            new ArrayList<>(shop.acceptedSellTypes())
        );
        PacketDistributor.sendToPlayer(player, payload);
        return InteractionResult.SUCCESS;
    }
}
