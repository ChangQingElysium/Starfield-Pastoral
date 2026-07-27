package com.stardew.craft.api.v1.internal.shop;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.requirement.StardewRequirement;
import com.stardew.craft.api.v1.requirement.StardewRequirementReport;
import com.stardew.craft.api.v1.requirement.StardewRequirementTypes;
import com.stardew.craft.api.v1.shop.StardewShopInventorySnapshot;
import com.stardew.craft.api.v1.shop.StardewShopProductContext;
import com.stardew.craft.api.v1.shop.StardewShopRowKey;
import com.stardew.craft.shop.ShopCostService;
import com.stardew.craft.shop.ShopEntryAvailabilityService;
import com.stardew.craft.shop.ShopItemEntry;
import com.stardew.craft.shop.ShopRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Runtime shop row projection with duplicate identity diagnostics. */
public final class StardewShopInventoryRegistry {
    private StardewShopInventoryRegistry() {
    }

    public static List<StardewShopInventorySnapshot> list(
            ServerPlayer player,
            String shopId
    ) {
        return listedRows(player, shopId).entrySet().stream()
                .map(row -> snapshot(player, row, 1))
                .toList();
    }

    public static List<StardewShopInventorySnapshot> candidates(
            ServerPlayer player,
            String shopId
    ) {
        return candidateRows(player, shopId).entrySet().stream()
                .map(row -> snapshot(player, row, 1))
                .toList();
    }

    public static Optional<StardewShopInventorySnapshot> inspect(
            ServerPlayer player,
            StardewShopRowKey key
    ) {
        ShopItemEntry row = candidateRows(
                player, key.shopId()).get(key);
        return Optional.ofNullable(row)
                .map(entry -> snapshot(
                        player, Map.entry(key, entry), 1));
    }

    public static StardewRequirementReport requirements(
            ServerPlayer player,
            StardewShopRowKey key,
            int quantity
    ) {
        ShopItemEntry row = candidateRows(
                player, key.shopId()).get(key);
        if (row == null) {
            return new StardewRequirementReport(List.of(
                    requirement(
                            StardewRequirementTypes
                                    .SHOP_ENTRY_LISTED,
                            false,
                            Component.translatable(
                                    "stardewcraft.requirement.shop.entry_listed",
                                    key.entryId()))));
        }
        return report(player, key, row, quantity);
    }

    private static Map<StardewShopRowKey, ShopItemEntry> listedRows(
            ServerPlayer player,
            String shopId
    ) {
        ShopRegistry.ShopDefinition definition =
                ShopRegistry.get(shopId);
        if (definition == null) {
            return Map.of();
        }
        return index(
                shopId,
                ShopRegistry.getFilteredItemsForPlayer(
                        shopId, definition, player, true));
    }

    private static Map<StardewShopRowKey, ShopItemEntry> candidateRows(
            ServerPlayer player,
            String shopId
    ) {
        ShopRegistry.ShopDefinition definition =
                ShopRegistry.get(shopId);
        if (definition == null) {
            return Map.of();
        }
        return index(
                shopId,
                ShopRegistry.getCandidateItemsForPlayer(
                        shopId, definition, player));
    }

    private static Map<StardewShopRowKey, ShopItemEntry> index(
            String shopId,
            List<ShopItemEntry> entries
    ) {
        LinkedHashMap<StardewShopRowKey, ShopItemEntry> rows =
                new LinkedHashMap<>();
        for (ShopItemEntry entry : entries) {
            StardewShopRowKey key = new StardewShopRowKey(
                    shopId, entry.itemId());
            ShopItemEntry previous = rows.putIfAbsent(
                    key, entry);
            if (previous != null) {
                StardewCraft.LOGGER.error(
                        "Shop {} published duplicate runtime entry ID {}; "
                                + "the first row remains authoritative",
                        shopId, entry.itemId());
            }
        }
        return rows;
    }

    private static StardewShopInventorySnapshot snapshot(
            ServerPlayer player,
            Map.Entry<StardewShopRowKey, ShopItemEntry> row,
            int quantity
    ) {
        ShopItemEntry entry = row.getValue();
        return new StardewShopInventorySnapshot(
                row.getKey(),
                ShopCostService.toApiEntry(entry),
                entry.stock(),
                entry.stock() == Integer.MAX_VALUE,
                report(player, row.getKey(), entry, quantity));
    }

    private static StardewRequirementReport report(
            ServerPlayer player,
            StardewShopRowKey key,
            ShopItemEntry entry,
            int quantity
    ) {
        StardewRequirementReport listing =
                ShopEntryAvailabilityService.evaluate(
                        player, key.shopId(), entry);
        boolean enough = entry.stock() == Integer.MAX_VALUE
                || entry.stock() >= quantity;
        java.util.ArrayList<StardewRequirement> requirements =
                new java.util.ArrayList<>();
        requirements.add(requirement(
                StardewRequirementTypes.SHOP_ENTRY_LISTED,
                listing.satisfied(),
                Component.translatable(
                        "stardewcraft.requirement.shop.entry_listed",
                        key.entryId())));
        requirements.addAll(listing.requirements());
        requirements.addAll(
                StardewShopProductRegistry.requirements(
                        new StardewShopProductContext(
                                player,
                                key.shopId(),
                                ShopCostService.toApiEntry(entry),
                                quantity))
                        .requirements());
        requirements.add(requirement(
                StardewRequirementTypes
                        .SHOP_STOCK_AVAILABLE,
                enough,
                Component.translatable(
                        "stardewcraft.requirement.shop.stock",
                        quantity,
                        entry.stock()
                                == Integer.MAX_VALUE
                                ? "\u221e"
                                : entry.stock())));
        return new StardewRequirementReport(requirements);
    }

    private static StardewRequirement requirement(
            net.minecraft.resources.ResourceLocation type,
            boolean satisfied,
            Component description
    ) {
        return new StardewRequirement(
                type,
                satisfied
                        ? StardewRequirement.State.SATISFIED
                        : StardewRequirement.State.UNSATISFIED,
                description,
                true);
    }
}
