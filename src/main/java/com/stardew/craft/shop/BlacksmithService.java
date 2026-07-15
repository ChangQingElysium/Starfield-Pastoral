package com.stardew.craft.shop;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.entity.npc.StardewNpcEntity;
import com.stardew.craft.item.tool.HoeItem;
import com.stardew.craft.item.tool.PanItem;
import com.stardew.craft.item.tool.StardewAxeItem;
import com.stardew.craft.item.tool.StardewPickaxeItem;
import com.stardew.craft.item.tool.WateringCanItem;
import com.stardew.craft.network.payload.OpenBlacksmithMenuPayload;
import com.stardew.craft.network.payload.OpenNpcDialogueScreenPayload;
import com.stardew.craft.network.payload.OpenShopScreenPayload;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.player.PlayerStardewDataAPI;
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
                ItemStack stack = new ItemStack(toolItem);
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
                PacketDistributor.sendToPlayer(player,
                    new OpenNpcDialogueScreenPayload("clint",
                        "stardewcraft.npc.clint.tool_pickup", 0, itemId, false));
            }
        } catch (Exception e) {
            StardewCraft.LOGGER.error("Failed to create upgraded tool: {}", upgradedToolId, e);
        }

        return InteractionResult.SUCCESS;
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
    private static List<ShopItemEntry> buildToolUpgradeItems(ServerPlayer player) {
        List<ShopItemEntry> items = new ArrayList<>();

        // Check each of the 4 tool types
        addUpgradeIfAvailable(items, player, "axe", StardewAxeItem.class);
        addUpgradeIfAvailable(items, player, "pickaxe", StardewPickaxeItem.class);
        addUpgradeIfAvailable(items, player, "hoe", HoeItem.class);
        addUpgradeIfAvailable(items, player, "watering_can", WateringCanItem.class);
        // Pan starts at Copper (tier 1) and goes to Iridium (tier 4) — uses
        // a separate upgrade path because there is no "starter" pan and
        // the id naming skips the "copper_" prefix on the base item.
        addPanUpgradeIfAvailable(items, player);

        return items;
    }

    /**
     * Pan upgrade variant — SDV: Copper (L1) → Steel (L2) → Gold (L3) → Iridium (L4).
     * Unlike other tools, pan has no "starter" tier; the copper_pan is itself
     * the base. Prices/bars match the standard SDV upgrade ladder.
     */
    private static void addPanUpgradeIfAvailable(List<ShopItemEntry> items, ServerPlayer player) {
        int currentTier = findCurrentPanTier(player);
        if (currentTier < 1 || currentTier >= 4) return; // No pan or already iridium

        int nextTier = currentTier + 1;
        String upgradedItemId = switch (nextTier) {
            case 2 -> "stardewcraft:steel_pan";
            case 3 -> "stardewcraft:gold_pan";
            case 4 -> "stardewcraft:iridium_pan";
            default -> null;
        };
        if (upgradedItemId == null) return;

        int price = getUpgradePrice(nextTier);
        String tradeItemId = getUpgradeBarId(nextTier);
        int tradeCount = 5;

        items.add(new ShopItemEntry(
            upgradedItemId, "", "",
            price, 1, tradeItemId, tradeCount,
            Set.of(), 1, 0, null, -1, 0, 1
        ));
    }

    private static int findCurrentPanTier(ServerPlayer player) {
        int maxTier = -1;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof PanItem pan) {
                int t = pan.getUpgradeLevel();
                if (t > maxTier) maxTier = t;
            }
        }
        return maxTier;
    }

    private static void addUpgradeIfAvailable(List<ShopItemEntry> items, ServerPlayer player,
                                               String toolBaseName, Class<?> toolClass) {
        int currentTier = findCurrentToolTier(player, toolClass);
        if (currentTier < 0 || currentTier >= 4) return; // No tool or already iridium

        int nextTier = currentTier + 1;
        String nextTierPrefix = switch (nextTier) {
            case 1 -> "copper_";
            case 2 -> "steel_";
            case 3 -> "gold_";
            case 4 -> "iridium_";
            default -> "";
        };

        String upgradedItemId = "stardewcraft:" + nextTierPrefix + toolBaseName;
        int price = getUpgradePrice(nextTier);
        String tradeItemId = getUpgradeBarId(nextTier);
        int tradeCount = 5; // SDV: always 5 bars

        items.add(new ShopItemEntry(
            upgradedItemId, "", "", // displayName and description resolved client-side
            price, 1, tradeItemId, tradeCount,
            Set.of(), 1, 0, null, -1, 0, 1
        ));
    }

    /**
     * Find the highest tier tool of a given type in the player's inventory.
     * Returns -1 if not found.
     */
    private static int findCurrentToolTier(ServerPlayer player, Class<?> toolClass) {
        int maxTier = -1;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            Item item = stack.getItem();
            if (!toolClass.isInstance(item)) continue;

            int tier = getToolTier(item);
            if (tier > maxTier) maxTier = tier;
        }
        return maxTier;
    }

    /**
     * Get the stardew tier level from a tool item.
     */
    private static int getToolTier(Item item) {
        if (item instanceof StardewAxeItem axe) return axe.getTierLevel();
        if (item instanceof StardewPickaxeItem pick) return pick.getStardewTier();
        if (item instanceof HoeItem hoe) return hoe.getTier().getMaxChargeLevel();
        if (item instanceof WateringCanItem can) return can.getTier().getMaxChargeLevel();
        return -1;
    }

    /**
     * SDV: ShopBuilder.GetToolUpgradeConventionalPrice
     */
    public static int getUpgradePrice(int level) {
        return switch (level) {
            case 1 -> 2000;
            case 2 -> 5000;
            case 3 -> 10000;
            case 4 -> 25000;
            default -> 2000;
        };
    }

    /**
     * SDV: ShopBuilder.GetToolUpgradeConventionalTradeItem → bar item IDs
     */
    public static String getUpgradeBarId(int level) {
        return switch (level) {
            case 1 -> "stardewcraft:copper_bar";
            case 2 -> "stardewcraft:iron_bar";
            case 3 -> "stardewcraft:gold_bar";
            case 4 -> "stardewcraft:iridium_bar";
            default -> "stardewcraft:copper_bar";
        };
    }

    /**
     * Called from ShopPurchasePayload when shopId == "ClintUpgrade".
     * Rebuilds the dynamic item list for this player and processes the purchase.
     */
    public static void handleToolUpgradePurchaseFromShop(ServerPlayer player, int itemIndex, int quantity) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);

        // Can't upgrade if already upgrading
        if (data.getToolBeingUpgraded() != null && !data.getToolBeingUpgraded().isEmpty()) {
            sendPurchaseResult(player, false);
            return;
        }

        // Rebuild the same dynamic list that was sent to the client
        List<ShopItemEntry> items = buildToolUpgradeItems(player);
        if (itemIndex < 0 || itemIndex >= items.size()) {
            sendPurchaseResult(player, false);
            return;
        }

        ShopItemEntry entry = items.get(itemIndex);
        int cost = entry.price();

        // Check money
        if (cost > 0 && com.stardew.craft.player.PlayerStardewDataAPI.getMoney(player) < cost) {
            sendPurchaseResult(player, false);
            return;
        }

        // Check trade items (bars)
        if (entry.requiresTrade()) {
            ResourceLocation tradeId = ResourceLocation.parse(entry.tradeItemId());
            Item tradeItem = BuiltInRegistries.ITEM.get(tradeId);
            if (tradeItem == null || tradeItem == Items.AIR) {
                sendPurchaseResult(player, false);
                return;
            }
            int tradeNeed = entry.tradeItemCount();
            if (player.getInventory().countItem(tradeItem) < tradeNeed) {
                sendPurchaseResult(player, false);
                return;
            }
        }

        // Deduct money
        if (cost > 0) {
            if (!com.stardew.craft.player.PlayerStardewDataAPI.removeMoney(player, cost)) {
                sendPurchaseResult(player, false);
                return;
            }
        }

        // Consume trade items
        if (entry.requiresTrade()) {
            ResourceLocation tradeId = ResourceLocation.parse(entry.tradeItemId());
            Item tradeItem = BuiltInRegistries.ITEM.get(tradeId);
            int tradeNeed = entry.tradeItemCount();
            int remaining = tradeNeed;
            for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
                ItemStack slot = player.getInventory().getItem(i);
                if (!slot.isEmpty() && slot.is(tradeItem)) {
                    int take = Math.min(remaining, slot.getCount());
                    slot.shrink(take);
                    remaining -= take;
                }
            }
            if (remaining > 0) {
                // Rollback money
                if (cost > 0) com.stardew.craft.player.PlayerStardewDataAPI.addMoney(player, cost);
                sendPurchaseResult(player, false);
                return;
            }
        }

        // Process the upgrade (remove old tool, set upgrade state)
        handleToolUpgradePurchase(player, entry);

        // Send purchase result first (updates client money display)
        int newMoney = com.stardew.craft.player.PlayerStardewDataAPI.getMoney(player);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
            new com.stardew.craft.network.payload.ShopPurchaseResultPayload(
                true, newMoney, "", 0, itemIndex));

        // SDV: Game1.exitActiveMenu() + Game1.DrawDialogue("Tool.cs.14317")
        // Send dialogue — this opens the dialogue screen, replacing the shop screen
        sendDialogue(player, "stardewcraft.npc.clint.upgrade_started", null);
    }

    private static void sendPurchaseResult(ServerPlayer player, boolean success) {
        int money = com.stardew.craft.player.PlayerStardewDataAPI.getMoney(player);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
            new com.stardew.craft.network.payload.ShopPurchaseResultPayload(
                success, money, "", 0, -1));
    }

    /**
     * Handle tool upgrade purchase from ClintUpgrade shop.
     * SDV parity: Tool.actionWhenPurchased("ClintUpgrade")
     * Called from ShopPurchasePayload when shopId == "ClintUpgrade".
     *
     * @return true if handled (caller should NOT do normal item delivery)
     */
    public static boolean handleToolUpgradePurchase(ServerPlayer player, ShopItemEntry entry) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);

        // SDV: must not already have a tool being upgraded
        if (data.getToolBeingUpgraded() != null && !data.getToolBeingUpgraded().isEmpty()) {
            return true; // Block purchase
        }

        String upgradedItemId = entry.itemId();

        // Determine which old tool to remove
        String oldToolId = getOldToolId(upgradedItemId);
        if (oldToolId == null) return true;

        // Find and remove the old tool from inventory
        ResourceLocation oldRl = ResourceLocation.parse(oldToolId);
        Item oldItem = BuiltInRegistries.ITEM.get(oldRl);
        if (oldItem == null || oldItem == Items.AIR) return true;

        boolean removed = false;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.is(oldItem)) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
                removed = true;
                break;
            }
        }
        if (!removed) return true; // Old tool not found

        // SDV: Game1.player.toolBeingUpgraded.Value = (Tool)getOne();
        // SDV: Game1.player.daysLeftForToolUpgrade.Value = 2;
        data.setToolBeingUpgraded(upgradedItemId);
        data.setDaysLeftForToolUpgrade(2);
        data.setToolUpgradeNotified(false);
        PlayerDataManager.get().setDirty();

        // SDV: Game1.playSound("parry")
        player.level().playSound(null, player.blockPosition(),
            net.minecraft.sounds.SoundEvents.ANVIL_USE,
            net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.0f);

        // Note: dialogue is NOT sent here; caller (handleToolUpgradePurchaseFromShop) 
        // handles closing the shop and sending the dialogue after the purchase result.

        return true; // Handled — don't deliver the item to inventory
    }

    /**
     * Get the old tool ID that should be removed when upgrading to the given new tool.
     */
    private static String getOldToolId(String newToolId) {
        // Map: upgraded tool → previous tier tool
        return switch (newToolId) {
            // Axe
            case "stardewcraft:copper_axe" -> "stardewcraft:axe";
            case "stardewcraft:steel_axe" -> "stardewcraft:copper_axe";
            case "stardewcraft:gold_axe" -> "stardewcraft:steel_axe";
            case "stardewcraft:iridium_axe" -> "stardewcraft:gold_axe";
            // Pickaxe
            case "stardewcraft:copper_pickaxe" -> "stardewcraft:pickaxe";
            case "stardewcraft:steel_pickaxe" -> "stardewcraft:copper_pickaxe";
            case "stardewcraft:gold_pickaxe" -> "stardewcraft:steel_pickaxe";
            case "stardewcraft:iridium_pickaxe" -> "stardewcraft:gold_pickaxe";
            // Hoe
            case "stardewcraft:copper_hoe" -> "stardewcraft:hoe";
            case "stardewcraft:steel_hoe" -> "stardewcraft:copper_hoe";
            case "stardewcraft:gold_hoe" -> "stardewcraft:steel_hoe";
            case "stardewcraft:iridium_hoe" -> "stardewcraft:gold_hoe";
            // Watering Can
            case "stardewcraft:copper_watering_can" -> "stardewcraft:watering_can";
            case "stardewcraft:steel_watering_can" -> "stardewcraft:copper_watering_can";
            case "stardewcraft:gold_watering_can" -> "stardewcraft:steel_watering_can";
            case "stardewcraft:iridium_watering_can" -> "stardewcraft:gold_watering_can";
            // Pan (Copper → Steel → Gold → Iridium; no starter tier)
            case "stardewcraft:steel_pan"    -> "stardewcraft:copper_pan";
            case "stardewcraft:gold_pan"     -> "stardewcraft:steel_pan";
            case "stardewcraft:iridium_pan"  -> "stardewcraft:gold_pan";
            default -> null;
        };
    }

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
            new com.stardew.craft.network.payload.OpenGeodeMenuPayload());
    }

    // ──── Helpers ────

    private static boolean playerHasGeode(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (id != null) {
                String path = id.getPath();
                if (path.contains("geode") || path.contains("omni_geode")
                    || path.contains("mystery_box") || path.contains("golden_coconut")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Check if the player has ANY stardew tool in inventory (even starter tier).
     */
    private static boolean playerHasAnyStardewTool(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            Item item = stack.getItem();
            if (item instanceof StardewAxeItem || item instanceof StardewPickaxeItem
                || item instanceof HoeItem || item instanceof WateringCanItem
                || item instanceof PanItem) {
                return true;
            }
        }
        return false;
    }

    private static void sendDialogue(ServerPlayer player, String langKey, PlayerStardewData data) {
        PacketDistributor.sendToPlayer(player,
            new OpenNpcDialogueScreenPayload("clint", langKey, data != null ? 0 : 0));
    }
}
