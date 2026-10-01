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
 * Joja 超市商店 — 右键女收银员（joja_cashier）打开 SDV Shops.json "Joja" 商店界面。
 * 1.25x 非会员溢价在 {@link ShopRegistry#getFilteredItemsForPlayer} 内应用。
 *
 * <p>右键收银员任意位置都能打开（SDV 也是直接点柜台即开，不做额外柜台 AABB）。
 */
@SuppressWarnings("null")
public final class JojaMartService {

    private JojaMartService() {}

    public static InteractionResult handleJojaInteraction(ServerPlayer player, StardewNpcEntity cashier) {
        cashier.setYRot(0f);
        cashier.setYHeadRot(0f);

        ShopRegistry.ShopDefinition shop = ShopRegistry.get("JojaMart");
        if (shop == null) return InteractionResult.PASS;

        int money = PlayerStardewDataAPI.getMoney(player);
        List<ShopItemEntry> items = ShopRegistry.getFilteredItemsForPlayer("JojaMart", shop, player);

        // SDV parity: Joja 超市的 ShopMenu Owners = "AnyOrNone"（Portrait: ""、Dialogues: []），
        // 界面无 NPC 头像、无问候对话 —— 故意传空字符串。
        OpenShopScreenPayload payload = new OpenShopScreenPayload(
            "JojaMart", money, items,
            "", "",
            new ArrayList<>(shop.acceptedSellTypes())
        );
        PacketDistributor.sendToPlayer(player, payload);
        return InteractionResult.SUCCESS;
    }
}
