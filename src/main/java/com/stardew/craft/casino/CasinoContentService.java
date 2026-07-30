package com.stardew.craft.casino;

import com.stardew.craft.entity.npc.StardewNpcEntity;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.network.ObjectDialogueService;
import com.stardew.craft.network.payload.HoldUpItemPayload;
import com.stardew.craft.network.payload.OpenDesertFestivalQuestionPayload;
import com.stardew.craft.network.payload.OpenNpcDialogueScreenPayload;
import com.stardew.craft.network.payload.OpenShopScreenPayload;
import com.stardew.craft.player.PlayerDataEventHandler;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.shop.ShopItemEntry;
import com.stardew.craft.shop.ShopRegistry;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/** Source-faithful non-minigame interactions inside the Calico Desert casino. */
public final class CasinoContentService {
    public static final String SHOP_ID = "Casino";
    public static final String MR_QI_NPC_ID = "mister_qi";
    public static final String STATUE_VENDOR_NPC_ID = "club_seller";
    /**
     * Source parity: accepting the Club Card gives Mister Qi one temporary
     * {@code MisterQi_PlayerClubMember} dialogue. It is consumed after the
     * player talks to him and must not become an endlessly repeatable line.
     */
    public static final String MR_QI_CASINO_DIALOGUE_SEEN_FLAG =
            "misterQiCasinoMemberDialogueSeen";
    public static final String BUY_QI_COINS_CONTEXT = "casino_buy_qi_coins";
    public static final String BUY_ENDLESS_FORTUNE_CONTEXT = "casino_buy_endless_fortune";
    private static final int QI_COIN_BUNDLE_PRICE = 1_000;
    private static final int QI_COIN_BUNDLE_SIZE = 100;
    private static final int ENDLESS_FORTUNE_PRICE = 1_000_000;

    private CasinoContentService() {
    }

    public static void openQiCoinMachine(ServerPlayer player) {
        sendQuestion(player, BUY_QI_COINS_CONTEXT,
                "stardewcraft.casino.buy_qi_coins.question",
                response(player, "yes", "gui.yes"),
                response(player, "no", "gui.no"));
    }

    public static void handleQuestionResponse(ServerPlayer player, String context, String choiceId) {
        if (!"yes".equals(choiceId)) {
            return;
        }
        if (BUY_QI_COINS_CONTEXT.equals(context)) {
            buyQiCoins(player);
        } else if (BUY_ENDLESS_FORTUNE_CONTEXT.equals(context)) {
            buyEndlessFortuneStatue(player);
        }
    }

    private static void buyQiCoins(ServerPlayer player) {
        if (!CasinoAccessService.isCasinoPosition(player.getX(), player.getY(), player.getZ())) {
            return;
        }
        if (!PlayerStardewDataAPI.removeMoney(player, QI_COIN_BUNDLE_PRICE)) {
            ObjectDialogueService.show(player, "stardewcraft.casino.not_enough_money");
            return;
        }
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        data.addClubCoins(QI_COIN_BUNDLE_SIZE);
        saveAndSync(player, data);
        player.playNotifySound(ModSounds.COIN.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    public static boolean openCasinoShop(ServerPlayer player) {
        if (!CasinoAccessService.isCasinoPosition(player.getX(), player.getY(), player.getZ())) {
            return false;
        }
        ShopRegistry.ShopDefinition shop = ShopRegistry.get(SHOP_ID);
        if (shop == null) {
            return false;
        }
        List<ShopItemEntry> items = ShopRegistry.getFilteredItemsForPlayer(SHOP_ID, shop, player);
        PacketDistributor.sendToPlayer(player, new OpenShopScreenPayload(
                SHOP_ID,
                PlayerDataManager.getPlayerData(player).getClubCoins(),
                items,
                "",
                "",
                new ArrayList<>()));
        return true;
    }

    public static InteractionResult interactMrQi(ServerPlayer player, StardewNpcEntity npc) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        if (!CasinoAccessService.hasBouncerMovedAside(data)
                || data.hasMailFlag(MR_QI_CASINO_DIALOGUE_SEEN_FLAG)) {
            return InteractionResult.PASS;
        }

        data.addMailFlag(MR_QI_CASINO_DIALOGUE_SEEN_FLAG);
        saveAndSync(player, data);
        npc.facePlayerTemporarily(player, 60, null);
        PacketDistributor.sendToPlayer(player, new OpenNpcDialogueScreenPayload(
                MR_QI_NPC_ID, "stardewcraft.npc.mister_qi.casino_member", 0));
        return InteractionResult.SUCCESS;
    }

    public static InteractionResult interactStatueVendor(ServerPlayer player, StardewNpcEntity npc) {
        npc.facePlayerTemporarily(player, 60, null);
        sendQuestion(player, BUY_ENDLESS_FORTUNE_CONTEXT,
                "stardewcraft.npc.club_seller.offer",
                response(player, "yes", "stardewcraft.npc.club_seller.yes"),
                response(player, "no", "stardewcraft.npc.club_seller.no"));
        return InteractionResult.SUCCESS;
    }

    private static void buyEndlessFortuneStatue(ServerPlayer player) {
        if (!CasinoAccessService.isCasinoPosition(player.getX(), player.getY(), player.getZ())) {
            return;
        }
        if (!PlayerStardewDataAPI.removeMoney(player, ENDLESS_FORTUNE_PRICE)) {
            ObjectDialogueService.show(player, "stardewcraft.npc.club_seller.not_enough_money");
            return;
        }
        ItemStack reward = new ItemStack(ModItems.STATUE_OF_ENDLESS_FORTUNE.get());
        ItemStack display = reward.copy();
        if (!player.getInventory().add(reward)) {
            player.drop(reward, false);
        }
        HoldUpItemPayload.sendTo(player, display);
    }

    private static void sendQuestion(ServerPlayer player, String context, String questionKey,
                                     OpenDesertFestivalQuestionPayload.ResponseOption... responses) {
        Component question = Component.translatable(questionKey);
        PacketDistributor.sendToPlayer(player, new OpenDesertFestivalQuestionPayload(
                context, 0, "",
                Component.Serializer.toJson(question, player.registryAccess()),
                List.of(responses)));
    }

    private static OpenDesertFestivalQuestionPayload.ResponseOption response(
            ServerPlayer player, String id, String translationKey) {
        return new OpenDesertFestivalQuestionPayload.ResponseOption(
                id,
                Component.Serializer.toJson(
                        Component.translatable(translationKey), player.registryAccess()));
    }

    private static void saveAndSync(ServerPlayer player, PlayerStardewData data) {
        PlayerDataManager.get().savePlayerData(player.getUUID(), data);
        PlayerDataEventHandler.syncPlayerData(player, data);
    }
}
