package com.stardew.craft.shop;

import com.stardew.craft.entity.npc.StardewNpcEntity;
import com.stardew.craft.network.payload.OpenNpcDialogueScreenPayload;
import com.stardew.craft.network.payload.OpenShopScreenPayload;
import com.stardew.craft.player.PlayerDataEventHandler;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.quest.QuestManager;
import com.stardew.craft.sewer.SewerStoryFlags;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("null")
public final class ShadowShopService {
    private static final String SHOP_ID = "ShadowShop";
    private static final String DARK_TALISMAN_QUEST_ID = "28";
    private static final String DARK_TALISMAN_DIALOGUE = "stardewcraft.npc.krobus.dark_talisman";

    private ShadowShopService() {
    }

    public static InteractionResult handleKrobusInteraction(ServerPlayer player, StardewNpcEntity krobus) {
        if (tryHandleDarkTalisman(player, krobus)) {
            return InteractionResult.SUCCESS;
        }

        krobus.facePlayerTemporarily(player, 60, () -> openShadowShop(player));
        return InteractionResult.SUCCESS;
    }

    private static boolean tryHandleDarkTalisman(ServerPlayer player, StardewNpcEntity krobus) {
        QuestManager questManager = QuestManager.of(player);
        if (questManager == null || !questManager.hasQuest(DARK_TALISMAN_QUEST_ID)) {
            return false;
        }

        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        if (data == null || data.hasMailFlag(SewerStoryFlags.KROBUS_UNSEAL)) {
            return false;
        }

        questManager.removeQuest(DARK_TALISMAN_QUEST_ID, player);
        data.addMailFlag(SewerStoryFlags.KROBUS_UNSEAL);
        PlayerDataEventHandler.syncPlayerData(player, data);
        com.stardew.craft.sewer.KrobusUnsealEffectService.armAfterDialogueEffect(player);

        krobus.facePlayerTemporarily(player, 60, () -> PacketDistributor.sendToPlayer(player,
            new OpenNpcDialogueScreenPayload("krobus", DARK_TALISMAN_DIALOGUE, 0)));
        return true;
    }

    public static void openShadowShop(ServerPlayer player) {
        ShopRegistry.ShopDefinition shop = ShopRegistry.get(SHOP_ID);
        if (shop == null) return;

        int money = PlayerStardewDataAPI.getMoney(player);
        List<ShopItemEntry> items = ShopRegistry.getFilteredItemsForPlayer(SHOP_ID, shop, player);

        OpenShopScreenPayload payload = new OpenShopScreenPayload(
            SHOP_ID, money, items,
            shop.ownerNpcId(), shop.ownerDialogue(),
            new ArrayList<>(shop.acceptedSellTypes())
        );
        PacketDistributor.sendToPlayer(player, payload);
    }
}
