package com.stardew.craft.shop;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.economy.StardewCosts;
import com.stardew.craft.api.v1.economy.StardewCurrencies;
import com.stardew.craft.entity.npc.StardewNpcEntity;
import com.stardew.craft.inventory.TrashCanTier;
import com.stardew.craft.network.payload.OpenBlacksmithMenuPayload;
import com.stardew.craft.network.payload.OpenNpcDialogueScreenPayload;
import com.stardew.craft.network.payload.OpenShopScreenPayload;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.player.PlayerDataEventHandler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

/**
 * Server-side handler for Clint's blacksmith shop interactions.
 * Mirrors SDV GameLocation.blacksmith() + answerDialogueAction("Blacksmith_*") exactly.
 */
@SuppressWarnings("null")
public final class BlacksmithService {

    // Counter area: player must be inside this AABB to trigger shop (instead of dialogue).
    // Coordinates from user: (102,48,24) to (107,46,26)
    private static final int COUNTER_MIN_X = 102;
    private static final int COUNTER_MAX_X = 107;
    private static final int COUNTER_MIN_Y = 46;
    private static final int COUNTER_MAX_Y = 48;
    private static final int COUNTER_MIN_Z = 24;
    private static final int COUNTER_MAX_Z = 26;

    private BlacksmithService() {}

    /**
     * Check if player position is "in front of the counter".
     * SDV parity: only triggers shop when player is on the customer side.
     */
    public static boolean isPlayerAtCounter(ServerPlayer player) {
        int px = (int) Math.floor(player.getX());
        int py = (int) Math.floor(player.getY());
        int pz = (int) Math.floor(player.getZ());
        return px >= COUNTER_MIN_X && px <= COUNTER_MAX_X
            && py >= COUNTER_MIN_Y && py <= COUNTER_MAX_Y
            && pz >= COUNTER_MIN_Z && pz <= COUNTER_MAX_Z;
    }

    /**
     * Main interaction entry point when player right-clicks Clint at the counter.
     * SDV parity: GameLocation.blacksmith()
     */
    public static InteractionResult handleBlacksmithInteraction(ServerPlayer player, StardewNpcEntity clint) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);

        // SDV: n.faceDirection(2) — make Clint face south (toward counter/player)
        clint.setYRot(0f); // 0 = south in MC
        clint.setYHeadRot(0f);

        // ====== Branch 1: Tool upgrade ready for pickup ======
        // SDV: if (Game1.player.toolBeingUpgraded.Value != null && Game1.player.daysLeftForToolUpgrade.Value <= 0)
        String upgradedToolId = data.getToolBeingUpgraded();
        if (upgradedToolId != null && !upgradedToolId.isEmpty() && data.getDaysLeftForToolUpgrade() <= 0) {
            return handleToolPickup(player, clint, data, upgradedToolId);
        }

        // ====== Branch 2: Show blacksmith menu ======
        // SDV: check if player has geodes in inventory
        boolean hasGeode = playerHasGeode(player);

        // Send payload to client to open the question dialog
        PacketDistributor.sendToPlayer(player, new OpenBlacksmithMenuPayload(hasGeode));
        return InteractionResult.SUCCESS;
    }

    /**
     * Handle tool pickup when upgrade is complete.
     * SDV parity: adds tool to inventory, displays holdUpItemThenMessage.
     */
    private static InteractionResult handleToolPickup(ServerPlayer player, StardewNpcEntity clint,
                                                       PlayerStardewData data, String upgradedToolId) {
        Optional<TrashCanTier> trashCanTier = TrashCanTier.fromUpgradeItemId(upgradedToolId);
        if (trashCanTier.isPresent()) {
            return handleTrashCanPickup(player, data, trashCanTier.get());
        }

        // Check inventory space
        if (player.getInventory().getFreeSlot() == -1) {
            // SDV: Game1.DrawDialogue(n, "Data\\ExtraDialogue:Clint_NoInventorySpace")
            sendDialogue(player, "stardewcraft.npc.clint.no_inventory_space", data);
            return InteractionResult.SUCCESS;
        }

        // Create the upgraded tool and give to player
        try {
            ResourceLocation rl = ResourceLocation.parse(upgradedToolId);
            Item toolItem = BuiltInRegistries.ITEM.get(rl);
            if (toolItem != null && toolItem != Items.AIR) {
                ItemStack stack = data.getToolUpgradeStack(player.registryAccess());
                if (stack.isEmpty()) stack = new ItemStack(toolItem);
                player.getInventory().add(stack);

                // Clear upgrade state
                data.setToolBeingUpgraded("");
                data.setDaysLeftForToolUpgrade(0);
                data.setToolUpgradeNotified(false);
                PlayerDataManager.get().setDirty();

                // SDV parity: dialogue first, then holdUpItemThenMessage on close
                // Sends dialogue with afterCloseItemId → client shows dialogue,
                // on close triggers totem animation + HUD pickup notification
                String itemId = BuiltInRegistries.ITEM.getKey(toolItem).toString();
                com.stardew.craft.npc.runtime.NpcInteractionService.sendDialogue(player,
                    new OpenNpcDialogueScreenPayload("clint",
                        "stardewcraft.npc.clint.tool_pickup", 0, itemId, false));
            }
        } catch (Exception e) {
            StardewCraft.LOGGER.error("Failed to create upgraded tool: {}", upgradedToolId, e);
        }

        return InteractionResult.SUCCESS;
    }

    private static InteractionResult handleTrashCanPickup(ServerPlayer player, PlayerStardewData data,
                                                           TrashCanTier targetTier) {
        if (!completeTrashCanUpgrade(player, data, targetTier)) {
            sendDialogue(player, "stardewcraft.npc.clint.still_working", data);
            return InteractionResult.SUCCESS;
        }

        com.stardew.craft.npc.runtime.NpcInteractionService.sendDialogue(player,
                new OpenNpcDialogueScreenPayload("clint", "stardewcraft.npc.clint.tool_pickup", 0,
                        targetTier.upgradeItemId(), false));
        return InteractionResult.SUCCESS;
    }

    /** Completes a ready trash-can upgrade without requiring or granting an inventory item. */
    public static boolean completeTrashCanUpgrade(ServerPlayer player, PlayerStardewData data,
                                                   TrashCanTier targetTier) {
        int currentLevel = data.getTrashCanLevel();
        if (!completeTrashCanUpgradeState(data, targetTier)) {
            StardewCraft.LOGGER.error("Refusing incomplete or out-of-order trash can upgrade {} for player {} at level {}",
                    targetTier.upgradeItemId(), player.getGameProfile().getName(), currentLevel);
            return false;
        }
        PlayerDataManager.get().setDirty();
        PlayerDataEventHandler.syncPlayerData(player, data);
        return true;
    }

    /** Pure persisted-state transition used by the live pickup path and headless regression tests. */
    public static boolean completeTrashCanUpgradeState(PlayerStardewData data, TrashCanTier targetTier) {
        int currentLevel = data.getTrashCanLevel();
        if (data.getDaysLeftForToolUpgrade() > 0 || currentLevel < targetTier.level() - 1
                || !targetTier.upgradeItemId().equals(data.getToolBeingUpgraded())) {
            return false;
        }
        if (currentLevel < targetTier.level()) {
            data.setTrashCanLevel(targetTier.level());
        }
        data.setToolBeingUpgraded("");
        data.setDaysLeftForToolUpgrade(0);
        data.setToolUpgradeNotified(false);
        return true;
    }

    /**
     * Handle the player's menu choice.
     * SDV parity: answerDialogueAction("Blacksmith_Shop"/"Blacksmith_Upgrade"/"Blacksmith_Process")
     */
    public static void handleMenuChoice(ServerPlayer player, int choice) {
        switch (choice) {
            case 0 -> openBlacksmithShop(player);       // Shop
            case 1 -> openToolUpgrade(player);           // Upgrade
            case 2 -> openGeodeProcessing(player);       // Process (geodes)
            // 3 = Leave, do nothing
        }
    }

    // ──── Shop (material purchase) ────

    private static void openBlacksmithShop(ServerPlayer player) {
        // SDV: Utility.TryOpenShopMenu("Blacksmith", "Clint")
        ShopRegistry.ShopDefinition shop = ShopRegistry.get("Blacksmith");
        if (shop == null) return;

        int money = PlayerStardewDataAPI.getMoney(player);
        List<ShopItemEntry> items = ShopRegistry.getFilteredItemsForPlayer("Blacksmith", shop, player);

        OpenShopScreenPayload payload = new OpenShopScreenPayload(
            "Blacksmith", money, items,
            shop.ownerNpcId(), shop.ownerDialogue(),
            new ArrayList<>(shop.acceptedSellTypes())
        );
        PacketDistributor.sendToPlayer(player, payload);
    }

    // ──── Tool Upgrade ────

    private static void openToolUpgrade(ServerPlayer player) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);

        // SDV: if daysLeftForToolUpgrade > 0, show "still working" dialogue
        if (data.getDaysLeftForToolUpgrade() > 0) {
            if (data.getDaysLeftForToolUpgrade() == 1) {
                sendDialogue(player, "stardewcraft.npc.clint.still_working_tomorrow", data);
            } else {
                sendDialogue(player, "stardewcraft.npc.clint.still_working", data);
            }
            return;
        }

        // Build dynamic tool upgrade shop based on player's current tools
        // SDV: Utility.TryOpenShopMenu("ClintUpgrade", "Clint")
        List<ShopItemEntry> upgradeItems = buildToolUpgradeItems(player);
        if (upgradeItems.isEmpty()) {
            // Check if player has NO stardew tools at all vs all tools are iridium
            boolean hasAnyTool = playerHasAnyStardewTool(player);
            if (hasAnyTool) {
                sendDialogue(player, "stardewcraft.npc.clint.no_upgrades", data);
            } else {
                sendDialogue(player, "stardewcraft.npc.clint.no_tools", data);
            }
            return;
        }

        int money = PlayerStardewDataAPI.getMoney(player);
        OpenShopScreenPayload payload = new OpenShopScreenPayload(
            "ClintUpgrade", money, upgradeItems,
            "Clint",
            "stardewcraft.npc.clint.upgrade_dialogue",
            List.of() // Can't sell items here
        );
        PacketDistributor.sendToPlayer(player, payload);
    }

    /**
     * Build list of available tool upgrades for the player.
     * SDV parity: TOOL_UPGRADES ItemQuery — one entry per tool, showing next tier.
     */
    private static final Map<UUID, List<Map.Entry<ResourceLocation, ToolUpgradeData.Definition>>> OPEN_UPGRADES = new HashMap<>();

    private static List<ShopItemEntry> buildToolUpgradeItems(ServerPlayer player) {
        var offers = ToolUpgradeData.offers(player);
        OPEN_UPGRADES.put(player.getUUID(), offers);
        return offers.stream().map(e -> e.getValue().shopEntry()).toList();
    }

    /** Legacy helpers now read the standard axe path from the same definitions. */
    public static int getUpgradePrice(int level) {
        return standardUpgrade(level).map(ToolUpgradeData.Definition::price).orElse(0);
    }
    public static String getUpgradeBarId(int level) {
        return standardUpgrade(level).map(d -> d.material().toString()).orElse("");
    }
    private static Optional<ToolUpgradeData.Definition> standardUpgrade(int level) {
        return ToolUpgradeData.snapshot().values().stream().filter(d -> d.family().toString().equals("stardewcraft:axe")
                && d.tier() == level).findFirst();
    }

    public static void handleToolUpgradePurchaseFromShop(ServerPlayer player, int itemIndex, int quantity) {
        var data = PlayerDataManager.getPlayerData(player);
        var offers = OPEN_UPGRADES.getOrDefault(player.getUUID(), List.of());
        if (quantity != 1 || !data.getToolBeingUpgraded().isEmpty() || itemIndex < 0 || itemIndex >= offers.size()) {
            sendPurchaseResult(player, false); return;
        }
        var selected = offers.get(itemIndex);
        // Never reinterpret a stale index after reload or after inventory changes.
        if (!selected.getValue().equals(ToolUpgradeData.snapshot().get(selected.getKey()))
                || ToolUpgradeData.offers(player).stream().noneMatch(e -> e.equals(selected))) {
            sendPurchaseResult(player, false); return;
        }
        var definition = selected.getValue();
        int slot = definition.inputSlot(player);
        var result = definition.result(slot < 0 ? ItemStack.EMPTY : player.getInventory().getItem(slot));
        if (result.isEmpty()) { sendPurchaseResult(player, false); return; }
        var cost = ShopCostService.resolve(player, "ClintUpgrade", definition.shopEntry(), 1, StardewCurrencies.MONEY);
        var reserved = slot < 0 ? ItemStack.EMPTY : player.getInventory().getItem(slot).copy();
        if (slot >= 0) player.getInventory().getItem(slot).shrink(1);
        if (cost.isEmpty() || !StardewCosts.pay(player, cost.get().cost()).success()) {
            if (slot >= 0) player.getInventory().setItem(slot, reserved);
            sendPurchaseResult(player, false); return;
        }
        startOrder(player, definition, -1, result);
        OPEN_UPGRADES.remove(player.getUUID());
        PacketDistributor.sendToPlayer(player, new com.stardew.craft.network.payload.ShopPurchaseResultPayload(
                true, "ClintUpgrade", PlayerStardewDataAPI.getMoney(player), "", 0, itemIndex));
        sendDialogue(player, "stardewcraft.npc.clint.upgrade_started", null);
    }
    private static void sendPurchaseResult(ServerPlayer player, boolean success) {
        PacketDistributor.sendToPlayer(player, new com.stardew.craft.network.payload.ShopPurchaseResultPayload(
                success, "ClintUpgrade", PlayerStardewDataAPI.getMoney(player), "", 0, -1));
    }
    /** Compatibility hook for callers which already paid the displayed cost. */
    public static boolean handleToolUpgradePurchase(ServerPlayer player, ShopItemEntry entry) {
        if (!PlayerDataManager.getPlayerData(player).getToolBeingUpgraded().isEmpty()) return true;
        ToolUpgradeData.offers(player).stream().map(Map.Entry::getValue)
                .filter(d -> d.output().toString().equals(entry.itemId())).findFirst().ifPresent(d -> {
                    int slot = d.inputSlot(player);
                    var result = d.result(slot < 0 ? ItemStack.EMPTY : player.getInventory().getItem(slot));
                    if (!result.isEmpty()) startOrder(player, d, slot, result);
                });
        return true;
    }
    private static void startOrder(ServerPlayer player, ToolUpgradeData.Definition definition, int slot, ItemStack result) {
        if (slot >= 0) player.getInventory().getItem(slot).shrink(1);
        var data = PlayerDataManager.getPlayerData(player);
        data.setToolBeingUpgraded(definition.output().toString());
        data.setToolUpgradeStack(result, player.registryAccess());
        data.setDaysLeftForToolUpgrade(definition.days());
        data.setToolUpgradeNotified(false);
        PlayerDataManager.get().setDirty();
        player.inventoryMenu.broadcastChanges();
        player.level().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.ANVIL_USE,
                net.minecraft.sounds.SoundSource.BLOCKS, 1, 1);
    }
    public static void onLogout(ServerPlayer player) { OPEN_UPGRADES.remove(player.getUUID()); }

    /**
     * Called each new day for every player. Decrements daysLeftForToolUpgrade.
     * SDV parity: Farmer.dayupdate() → daysLeftForToolUpgrade--
     */
    public static void onNewDay(ServerPlayer player) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        if (data.getDaysLeftForToolUpgrade() > 0) {
            data.setDaysLeftForToolUpgrade(data.getDaysLeftForToolUpgrade() - 1);
            PlayerDataManager.get().setDirty();
        }
    }

    /**
     * Show tool upgrade notification at day start if tool is ready.
     * SDV parity: Farmer.showToolUpgradeAvailability()
     */
    public static void showToolUpgradeNotification(ServerPlayer player) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        String toolId = data.getToolBeingUpgraded();
        if (toolId == null || toolId.isEmpty()) return;
        if (data.getDaysLeftForToolUpgrade() > 0) return;
        if (data.isToolUpgradeNotified()) return;

        // SDV: skip notification on festival days
        // SDV: skip on Friday when CC completed and not raining (Clint goes to saloon)
        // For now, just show the message
        data.setToolUpgradeNotified(true);
        PlayerDataManager.get().setDirty();

        // Get tool display name for the message
        try {
            ResourceLocation rl = ResourceLocation.parse(toolId);
            Item toolItem = BuiltInRegistries.ITEM.get(rl);
            if (toolItem != null && toolItem != Items.AIR) {
                String toolName = new ItemStack(toolItem).getHoverName().getString();
				com.stardew.craft.network.GlobalHudMessagePayload.sendTo(player,
					net.minecraft.network.chat.Component.translatable("stardewcraft.blacksmith.tool_ready", toolName));
            }
        } catch (Exception ignored) {}
    }

    // ──── Geode Processing ────

    private static void openGeodeProcessing(ServerPlayer player) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
            new com.stardew.craft.network.payload.OpenGeodeMenuPayload(GeodeLootService.clintInputs(player)));
    }

    // ──── Helpers ────

    private static boolean playerHasGeode(ServerPlayer player) {
        var inputs = GeodeLootService.clintInputs(player);
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && inputs.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()))) return true;
        }
        return false;
    }

    /**
     * Check if the player has ANY stardew tool in inventory (even starter tier).
     */
    private static boolean playerHasAnyStardewTool(ServerPlayer player) {
        return ToolUpgradeData.hasTool(player);
    }

    private static void sendDialogue(ServerPlayer player, String langKey, PlayerStardewData data) {
        com.stardew.craft.npc.runtime.NpcInteractionService.sendDialogue(player,
            new OpenNpcDialogueScreenPayload("clint", langKey, data != null ? 0 : 0));
    }
}
