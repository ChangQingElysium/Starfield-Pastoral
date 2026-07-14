package com.stardew.craft.network.overnight;

import com.stardew.craft.api.v1.item.StardewItemDataApi;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistent per-player overnight settlement ledger.
 */
public final class OvernightSettlementTracker extends SavedData {
    private static final String DATA_NAME = "stardew_overnight_settlement";

    private final Map<UUID, PlayerLedger> ledgerByPlayer = new ConcurrentHashMap<>();

    public OvernightSettlementTracker() {
    }

    public static void recordShipping(ServerPlayer player, ItemStack stack, int pricePerItem) {
        if (player == null) {
            return;
        }
        recordShipping(player.server, player.getUUID(), stack, pricePerItem, currentAbsoluteDay() + 1);
    }

    public static void recordShipping(MinecraftServer server, UUID playerId, ItemStack stack, int pricePerItem, int availableDay) {
        if (server == null || playerId == null || stack.isEmpty() || pricePerItem <= 0) {
            return;
        }

        ItemStack copied = stack.copy();
        OvernightSettlementTracker tracker = get(server);
        PlayerLedger ledger = tracker.ledgerByPlayer.computeIfAbsent(playerId, key -> new PlayerLedger());
        ledger.shippedItems.add(new PendingShippedItem(
            new OvernightSettlementPayload.ShippedItem(copied, classifyCategory(copied), pricePerItem),
            Math.max(1, availableDay)
        ));
        tracker.setDirty();
    }

    public static OvernightSettlementPayload consumePayload(ServerPlayer player) {
        if (player == null) {
            return new OvernightSettlementPayload(List.of(), List.of());
        }
        return get(player.server).consumeAvailable(player.getUUID(), currentAbsoluteDay());
    }

    private OvernightSettlementPayload consumeAvailable(UUID playerId, int currentDay) {
        PlayerLedger ledger = ledgerByPlayer.get(playerId);
        if (ledger == null || ledger.shippedItems.isEmpty()) {
            return new OvernightSettlementPayload(List.of(), List.of());
        }

        List<OvernightSettlementPayload.ShippedItem> ready = new ArrayList<>();
        ledger.shippedItems.removeIf(entry -> {
            if (entry.availableDay() <= currentDay) {
                ready.add(entry.item());
                return true;
            }
            return false;
        });

        if (ledger.shippedItems.isEmpty()) {
            ledgerByPlayer.remove(playerId);
        }
        if (!ready.isEmpty()) {
            setDirty();
        }

        return new OvernightSettlementPayload(List.copyOf(ready), List.of());
    }

    public static OvernightSettlementTracker get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(OvernightSettlementTracker::new, OvernightSettlementTracker::load),
            DATA_NAME
        );
    }

    public static OvernightSettlementTracker load(CompoundTag tag, HolderLookup.Provider provider) {
        OvernightSettlementTracker tracker = new OvernightSettlementTracker();
        ListTag players = tag.getList("Players", Tag.TAG_COMPOUND);
        for (int i = 0; i < players.size(); i++) {
            CompoundTag playerTag = players.getCompound(i);
            if (!playerTag.hasUUID("Player")) {
                continue;
            }

            UUID playerId = playerTag.getUUID("Player");
            PlayerLedger ledger = new PlayerLedger();
            ListTag items = playerTag.getList("Items", Tag.TAG_COMPOUND);
            for (int j = 0; j < items.size(); j++) {
                CompoundTag itemTag = items.getCompound(j);
                ItemStack stack = ItemStack.parse(provider, itemTag.getCompound("Stack")).orElse(ItemStack.EMPTY);
                int pricePerItem = itemTag.getInt("PricePerItem");
                if (stack.isEmpty() || pricePerItem <= 0) {
                    continue;
                }
                int category = itemTag.contains("Category", Tag.TAG_INT) ? itemTag.getInt("Category") : classifyCategory(stack);
                int availableDay = itemTag.contains("AvailableDay", Tag.TAG_INT) ? itemTag.getInt("AvailableDay") : 1;
                ledger.shippedItems.add(new PendingShippedItem(
                    new OvernightSettlementPayload.ShippedItem(stack, category, pricePerItem),
                    Math.max(1, availableDay)
                ));
            }
            if (!ledger.shippedItems.isEmpty()) {
                tracker.ledgerByPlayer.put(playerId, ledger);
            }
        }
        return tracker;
    }

    @Override
    @SuppressWarnings("null")
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag players = new ListTag();
        for (Map.Entry<UUID, PlayerLedger> playerEntry : ledgerByPlayer.entrySet()) {
            ListTag items = new ListTag();
            for (PendingShippedItem pending : playerEntry.getValue().shippedItems) {
                OvernightSettlementPayload.ShippedItem item = pending.item();
                if (item.stack().isEmpty() || item.pricePerItem() <= 0) {
                    continue;
                }
                CompoundTag itemTag = new CompoundTag();
                itemTag.put("Stack", item.stack().save(provider));
                itemTag.putInt("Category", item.category());
                itemTag.putInt("PricePerItem", item.pricePerItem());
                itemTag.putInt("AvailableDay", pending.availableDay());
                items.add(itemTag);
            }
            if (!items.isEmpty()) {
                CompoundTag playerTag = new CompoundTag();
                playerTag.putUUID("Player", playerEntry.getKey());
                playerTag.put("Items", items);
                players.add(playerTag);
            }
        }
        tag.put("Players", players);
        return tag;
    }

    private static int currentAbsoluteDay() {
        StardewTimeManager time = StardewTimeManager.get();
        return time == null ? 1 : time.getAbsoluteDay();
    }

    private static int classifyCategory(ItemStack stack) {
        String typeKey = StardewItemDataApi.getTypeKey(stack);
        if (typeKey.isBlank()) {
            return 4;
        }

        String key = typeKey.toLowerCase(Locale.ROOT);
        if (key.contains("fish")) {
            return 2;
        }
        if (key.contains("mining") || key.contains("ore") || key.contains("gem") || key.contains("bar") || key.contains("mineral")) {
            return 3;
        }
        if (key.contains("forage") || key.contains("foraging")) {
            return 1;
        }
        if (key.contains("crop") || key.contains("animal_product") || key.contains("artisan") || key.contains("syrup")) {
            return 0;
        }
        return 4;
    }

    private record PendingShippedItem(OvernightSettlementPayload.ShippedItem item, int availableDay) {
    }

    private static final class PlayerLedger {
        private final List<PendingShippedItem> shippedItems = new ArrayList<>();
    }
}
