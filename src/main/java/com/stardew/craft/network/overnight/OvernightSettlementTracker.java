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

    /** Persist the final menu payload before it is sent to the client. */
    public static void storePendingSettlement(
            ServerPlayer player,
            OvernightSettlementPayload payload
    ) {
        if (player == null || payload == null) {
            return;
        }
        OvernightSettlementTracker tracker = get(player.server);
        PlayerLedger ledger = tracker.ledgerByPlayer.computeIfAbsent(
                player.getUUID(), key -> new PlayerLedger());
        ledger.pendingSettlement = payload;
        tracker.setDirty();
    }

    public static OvernightSettlementPayload peekPendingSettlement(ServerPlayer player) {
        if (player == null) {
            return null;
        }
        PlayerLedger ledger = get(player.server).ledgerByPlayer.get(player.getUUID());
        return ledger == null ? null : ledger.pendingSettlement;
    }

    public static void acknowledgePendingSettlement(ServerPlayer player) {
        if (player == null) {
            return;
        }
        OvernightSettlementTracker tracker = get(player.server);
        PlayerLedger ledger = tracker.ledgerByPlayer.get(player.getUUID());
        if (ledger == null || ledger.pendingSettlement == null) {
            return;
        }
        ledger.pendingSettlement = null;
        if (ledger.shippedItems.isEmpty()) {
            tracker.ledgerByPlayer.remove(player.getUUID(), ledger);
        }
        tracker.setDirty();
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

        if (ledger.shippedItems.isEmpty() && ledger.pendingSettlement == null) {
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
            if (playerTag.contains("PendingSettlement", Tag.TAG_COMPOUND)) {
                ledger.pendingSettlement = loadSettlement(
                        playerTag.getCompound("PendingSettlement"), provider);
            }
            if (!ledger.shippedItems.isEmpty() || ledger.pendingSettlement != null) {
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
            if (!items.isEmpty() || playerEntry.getValue().pendingSettlement != null) {
                CompoundTag playerTag = new CompoundTag();
                playerTag.putUUID("Player", playerEntry.getKey());
                playerTag.put("Items", items);
                if (playerEntry.getValue().pendingSettlement != null) {
                    playerTag.put(
                            "PendingSettlement",
                            saveSettlement(playerEntry.getValue().pendingSettlement, provider));
                }
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
        private OvernightSettlementPayload pendingSettlement;
    }

    private static CompoundTag saveSettlement(
            OvernightSettlementPayload payload,
            HolderLookup.Provider provider
    ) {
        CompoundTag tag = new CompoundTag();
        ListTag shipped = new ListTag();
        for (OvernightSettlementPayload.ShippedItem item : payload.shippedItems()) {
            CompoundTag raw = new CompoundTag();
            raw.put("Stack", item.stack().save(provider));
            raw.putInt("Category", item.category());
            raw.putInt("PricePerItem", item.pricePerItem());
            shipped.add(raw);
        }
        tag.put("Shipped", shipped);

        ListTag levels = new ListTag();
        for (OvernightSettlementPayload.LevelUpData level : payload.levelUps()) {
            CompoundTag raw = new CompoundTag();
            raw.putInt("Skill", level.skillIndex());
            raw.putInt("Level", level.newLevel());
            levels.add(raw);
        }
        tag.put("LevelUps", levels);
        tag.putInt("PassOutType", payload.passOutType());
        tag.putInt("PassOutMoney", payload.passOutMoneyLost());

        ListTag lost = new ListTag();
        for (ItemStack stack : payload.passOutLostItems()) {
            if (!stack.isEmpty()) {
                lost.add(stack.save(provider));
            }
        }
        tag.put("PassOutItems", lost);

        OvernightSettlementPayload.OvernightContext context = payload.context();
        CompoundTag contextTag = new CompoundTag();
        contextTag.putInt("PreviousDay", context.previousDay());
        contextTag.putInt("PreviousSeason", context.previousSeason());
        contextTag.putInt("PreviousYear", context.previousYear());
        contextTag.putInt("NewDay", context.newDay());
        contextTag.putInt("NewSeason", context.newSeason());
        contextTag.putInt("NewYear", context.newYear());
        contextTag.putString("PreviousWeather", context.previousWeather());
        tag.put("Context", contextTag);
        return tag;
    }

    private static OvernightSettlementPayload loadSettlement(
            CompoundTag tag,
            HolderLookup.Provider provider
    ) {
        List<OvernightSettlementPayload.ShippedItem> shipped = new ArrayList<>();
        ListTag shippedTag = tag.getList("Shipped", Tag.TAG_COMPOUND);
        for (int i = 0; i < shippedTag.size(); i++) {
            CompoundTag raw = shippedTag.getCompound(i);
            ItemStack stack = ItemStack.parse(provider, raw.getCompound("Stack"))
                    .orElse(ItemStack.EMPTY);
            if (!stack.isEmpty()) {
                shipped.add(new OvernightSettlementPayload.ShippedItem(
                        stack, raw.getInt("Category"), raw.getInt("PricePerItem")));
            }
        }

        List<OvernightSettlementPayload.LevelUpData> levels = new ArrayList<>();
        ListTag levelTag = tag.getList("LevelUps", Tag.TAG_COMPOUND);
        for (int i = 0; i < levelTag.size(); i++) {
            CompoundTag raw = levelTag.getCompound(i);
            levels.add(new OvernightSettlementPayload.LevelUpData(
                    raw.getInt("Skill"), raw.getInt("Level")));
        }

        List<ItemStack> lost = new ArrayList<>();
        ListTag lostTag = tag.getList("PassOutItems", Tag.TAG_COMPOUND);
        for (int i = 0; i < lostTag.size(); i++) {
            ItemStack stack = ItemStack.parse(provider, lostTag.getCompound(i))
                    .orElse(ItemStack.EMPTY);
            if (!stack.isEmpty()) {
                lost.add(stack);
            }
        }

        CompoundTag rawContext = tag.getCompound("Context");
        OvernightSettlementPayload.OvernightContext context =
                new OvernightSettlementPayload.OvernightContext(
                        rawContext.getInt("PreviousDay"),
                        rawContext.getInt("PreviousSeason"),
                        rawContext.getInt("PreviousYear"),
                        rawContext.getInt("NewDay"),
                        rawContext.getInt("NewSeason"),
                        rawContext.getInt("NewYear"),
                        rawContext.getString("PreviousWeather")
                );
        return new OvernightSettlementPayload(
                List.copyOf(shipped),
                List.copyOf(levels),
                tag.contains("PassOutType", Tag.TAG_INT) ? tag.getInt("PassOutType") : -1,
                tag.getInt("PassOutMoney"),
                List.copyOf(lost),
                context
        );
    }
}
