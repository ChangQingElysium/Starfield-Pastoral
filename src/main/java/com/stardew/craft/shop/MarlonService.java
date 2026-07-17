package com.stardew.craft.shop;

import com.stardew.craft.book.BookPowerEffects;
import com.stardew.craft.api.v1.item.StardewItemDataApi;
import com.stardew.craft.entity.npc.StardewNpcEntity;
import com.stardew.craft.festival.desert.DesertFestivalMarlonChallengeService;
import com.stardew.craft.festival.desert.DesertFestivalMineService;
import com.stardew.craft.network.payload.OpenGilGoalsPayload;
import com.stardew.craft.network.payload.OpenMarlonMenuPayload;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-side handler for Marlon's adventurer's guild interactions.
 */
@SuppressWarnings("null")
public final class MarlonService {

    // Counter area: (107,59,-144) to (101,62,-147) — adventure guild
    private static final int COUNTER_MIN_X = 101;
    private static final int COUNTER_MAX_X = 107;
    private static final int COUNTER_MIN_Y = 59;
    private static final int COUNTER_MAX_Y = 62;
    private static final int COUNTER_MIN_Z = -147;
    private static final int COUNTER_MAX_Z = -144;

    private static final int DESERT_BOOTH_MIN_X = -233;
    private static final int DESERT_BOOTH_MAX_X = -226;
    private static final int DESERT_BOOTH_MIN_Y = 63;
    private static final int DESERT_BOOTH_MAX_Y = 66;
    private static final int DESERT_BOOTH_MIN_Z = -209;
    private static final int DESERT_BOOTH_MAX_Z = -205;

    private MarlonService() {}

    public static boolean isPlayerAtCounter(ServerPlayer player) {
        int px = (int) Math.floor(player.getX());
        int py = (int) Math.floor(player.getY());
        int pz = (int) Math.floor(player.getZ());
        return px >= COUNTER_MIN_X && px <= COUNTER_MAX_X
            && py >= COUNTER_MIN_Y && py <= COUNTER_MAX_Y
            && pz >= COUNTER_MIN_Z && pz <= COUNTER_MAX_Z;
    }

    public static boolean isPlayerAtDesertFestivalBooth(ServerPlayer player) {
        if (player == null || !DesertFestivalMineService.isActive()) {
            return false;
        }
        int px = (int) Math.floor(player.getX());
        int py = (int) Math.floor(player.getY());
        int pz = (int) Math.floor(player.getZ());
        return px >= DESERT_BOOTH_MIN_X && px <= DESERT_BOOTH_MAX_X
            && py >= DESERT_BOOTH_MIN_Y && py <= DESERT_BOOTH_MAX_Y
            && pz >= DESERT_BOOTH_MIN_Z && pz <= DESERT_BOOTH_MAX_Z;
    }

    public static InteractionResult handleMarlonInteraction(ServerPlayer player, StardewNpcEntity marlon) {
        boolean desertFestivalBooth = isPlayerAtDesertFestivalBooth(player);
        float yaw = desertFestivalBooth ? 0.0f : 90.0f;
        marlon.setYRot(yaw);
        marlon.setYHeadRot(yaw);

        boolean hasLost = hasLostItems(player);
        PacketDistributor.sendToPlayer(player, new OpenMarlonMenuPayload(hasLost, desertFestivalBooth, desertFestivalBooth, desertFestivalBooth));
        return InteractionResult.SUCCESS;
    }

    /**
     * Handle the player's choice from Marlon's question dialog.
    * 0 = Shop, 1 = Monster Slayer Goals, 2 = Recovery, 3 = Desert Festival rating, 4 = Desert Festival challenge
     */
    public static void handleChoice(ServerPlayer player, int choice) {
        if (choice == 0) {
            openAdventureShop(player);
        } else if (choice == 1) {
            openGilGoals(player);
        } else if (choice == 2) {
            openRecoveryShop(player);
        } else if (choice == 3) {
            DesertFestivalMineService.openMarlonRatingDialog(player);
        } else if (choice == 4) {
            DesertFestivalMarlonChallengeService.openChallengeDialog(player);
        }
    }

    private static void openAdventureShop(ServerPlayer player) {
        ShopRegistry.ShopDefinition shop = ShopRegistry.get("AdventureShop");
        if (shop == null) return;

        int money = com.stardew.craft.player.PlayerStardewDataAPI.getMoney(player);
        List<ShopItemEntry> items = ShopRegistry.getFilteredItemsForPlayer("AdventureShop", shop, player);

        com.stardew.craft.network.payload.OpenShopScreenPayload payload =
            new com.stardew.craft.network.payload.OpenShopScreenPayload(
                "AdventureShop", money, items,
                shop.ownerNpcId(), shop.ownerDialogue(),
                new ArrayList<>(shop.acceptedSellTypes())
            );
        PacketDistributor.sendToPlayer(player, payload);
    }

    private static void openGilGoals(ServerPlayer player) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        List<OpenGilGoalsPayload.GoalEntry> entries = new ArrayList<>();
        for (MonsterSlayerGoalRegistry.SlayerGoal goal : MonsterSlayerGoalRegistry.getAllGoals()) {
            entries.add(new OpenGilGoalsPayload.GoalEntry(
                goal.goalKey(),
                goal.translationKey(),
                data.getMonsterKills(goal.goalKey()),
                goal.requiredKills(),
                data.hasClaimedSlayerReward(goal.goalKey()),
                goal.hasReward()
            ));
        }
        PacketDistributor.sendToPlayer(player, new OpenGilGoalsPayload(entries));
    }

    /** Handle a monster slayer reward claim from the client. */
    public static void handleGilClaim(ServerPlayer player, String goalKey) {
        MonsterSlayerGoalRegistry.SlayerGoal goal = MonsterSlayerGoalRegistry.getGoal(goalKey);
        if (goal == null) return;

        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        if (data.hasClaimedSlayerReward(goalKey)) return;
        if (data.getMonsterKills(goalKey) < goal.requiredKills()) return;

        var actionContext = com.stardew.craft.api.v1.action.StardewActionContext.forPlayer(player);
        for (var reward : goal.rewards()) {
            var result = com.stardew.craft.api.v1.action.StardewActions.execute(reward, actionContext)
                    .resultOrPartial(message -> com.stardew.craft.StardewCraft.LOGGER.error(
                            "[Monster slayer] Reward {} failed for {}: {}",
                            goalKey, player.getName().getString(), message))
                    .orElse(null);
            if (result == null || !result.success()) {
                return;
            }
        }
        data.claimSlayerReward(goalKey);

        // Refresh the Gil screen
        openGilGoals(player);
    }

    // ──────────────────────────────────────
    //  物品找回
    // ──────────────────────────────────────

    /**
     * 打开物品找回商店。
     * SDV parity: 每件物品售价 = getSellToStorePrice × 1（有 Book_Marlon 则 ×0.5），
     * 实际我们用 sellPrice × 5（最低 250g），只能买回 1 件。
     * stock = 1，每件物品只显示 1 个。
     */
    private static void openRecoveryShop(ServerPlayer player) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        List<ItemStack> lostItems = data.getItemsLostLastDeath();

        if (lostItems.isEmpty()) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("stardewcraft.marlon.no_lost_items"));
            return;
        }

        int money = com.stardew.craft.player.PlayerStardewDataAPI.getMoney(player);
        List<ShopItemEntry> items = new ArrayList<>();
        for (int i = 0; i < lostItems.size(); i++) {
            ItemStack lost = lostItems.get(i);
            int price = getRecoveryPrice(data, lost);
            var rl = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(lost.getItem());
            // 用真实物品ID显示图标，购买逻辑通过 itemIndex 定位原始 ItemStack
            String realItemId = rl.toString();
            String displayName = lost.getHoverName().getString();
            items.add(new ShopItemEntry(
                realItemId, displayName, "",
                price, lost.getCount(),
                null, 0,
                java.util.Set.of(), 1, 0, null, -1, 0, 1
            ));
        }

        com.stardew.craft.network.payload.OpenShopScreenPayload payload =
            new com.stardew.craft.network.payload.OpenShopScreenPayload(
                "MarlonRecovery", money, items,
                "marlon", "stardewcraft.marlon.recovery_desc",
                new ArrayList<>()
            );
        PacketDistributor.sendToPlayer(player, payload);
    }

    /**
     * 服务端处理物品找回购买（从 ShopPurchasePayload 调用）。
     * SDV parity: 买回 1 件 → 原始 ItemStack（含 NBT/附魔/数量）还给玩家 → 清空整个 lostItems 列表。
     */
    public static void handleRecoveryPurchaseFromShop(ServerPlayer player, int itemIndex) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        List<ItemStack> lostItems = data.getItemsLostLastDeath();

        if (itemIndex < 0 || itemIndex >= lostItems.size()) {
            sendRecoveryResult(player, false);
            return;
        }

        // 计算价格
        ItemStack chosen = lostItems.get(itemIndex);
        int price = getRecoveryPrice(data, chosen);

        // 扣钱
        int currentMoney = com.stardew.craft.player.PlayerStardewDataAPI.getMoney(player);
        if (currentMoney < price) {
            sendRecoveryResult(player, false);
            return;
        }
        com.stardew.craft.player.PlayerStardewDataAPI.removeMoney(player, price);

        // 把原始 ItemStack 还给玩家（保留所有 NBT）
        ItemStack recovered = chosen.copy();
        if (!player.getInventory().add(recovered)) {
            player.drop(recovered, false);
        }
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();

        // SDV parity: 买回 1 件后清空全部
        data.clearItemsLostLastDeath();

        sendRecoveryResult(player, true);

        com.stardew.craft.StardewCraft.LOGGER.info("[MarlonRecovery] {} recovered '{}' for {}g",
                player.getName().getString(), recovered.getHoverName().getString(), price);
    }

    private static void sendRecoveryResult(ServerPlayer player, boolean success) {
        int newMoney = com.stardew.craft.player.PlayerStardewDataAPI.getMoney(player);
        // Reuse ShopPurchaseResultPayload: success, new money, empty item (already delivered), qty 0, idx 0
        PacketDistributor.sendToPlayer(player,
            new com.stardew.craft.network.payload.ShopPurchaseResultPayload(
                success, newMoney, "", success ? 1 : 0, 0));
    }

    /**
     * 检查玩家是否有可找回的物品。
     */
    public static boolean hasLostItems(ServerPlayer player) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        return !data.getItemsLostLastDeath().isEmpty();
    }

    private static int getItemSellPrice(ItemStack stack) {
        int price = StardewItemDataApi.getSellPrice(stack);
        if (price > 0) return price;
        return 50;
    }

    private static int getRecoveryPrice(PlayerStardewData data, ItemStack stack) {
        int price = Math.max(250, getItemSellPrice(stack) * 5);
        return BookPowerEffects.applyMarlonRecoveryPrice(data, price);
    }
}
