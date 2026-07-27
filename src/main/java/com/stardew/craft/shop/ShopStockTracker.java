package com.stardew.craft.shop;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.shop.StardewShopStockRule;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Persistent limited-shop-stock ledger.
 *
 * <p>Legacy rows remain per-player and daily. Datapacks can change a row to shared world stock
 * and select a day, week, season, year or never-reset period without changing the shop schema.
 */
@SuppressWarnings("null")
public final class ShopStockTracker extends SavedData {
    private static final String DATA_NAME =
            StardewCraft.MODID + "_shop_stock";
    private static final String ENTRIES = "Entries";
    private final Map<PurchaseKey, Integer> purchased =
            new HashMap<>();

    public ShopStockTracker() {
    }

    public static ShopStockTracker get(
            ServerPlayer player
    ) {
        return player.server.overworld().getDataStorage()
                .computeIfAbsent(
                        new SavedData.Factory<>(
                                ShopStockTracker::new,
                                ShopStockTracker::load),
                        DATA_NAME);
    }

    public static int getRemaining(
            ServerPlayer player,
            String shopId,
            String itemId,
            int originalStock
    ) {
        if (originalStock == Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        ShopStockTracker data = get(player);
        data.pruneExpired();
        PurchaseKey key = currentKey(
                player, shopId, itemId,
                ShopStockPolicyService.resolve(
                        player, shopId, itemId));
        int bought = data.purchased.getOrDefault(key, 0);
        return Math.max(0, originalStock - bought);
    }

    /**
     * Compatibility overload for existing internal dynamic stock builders.
     * Shop queries only run for online players, so the UUID is resolved back to its player.
     */
    public static int getRemaining(
            UUID playerId,
            String shopId,
            String itemId,
            int originalStock
    ) {
        ServerPlayer player = onlinePlayer(playerId);
        return player == null
                ? originalStock
                : getRemaining(
                        player, shopId, itemId, originalStock);
    }

    public static void recordPurchase(
            ServerPlayer player,
            String shopId,
            String itemId,
            int amount
    ) {
        if (amount <= 0) {
            return;
        }
        ShopStockTracker data = get(player);
        data.pruneExpired();
        PurchaseKey key = currentKey(
                player, shopId, itemId,
                ShopStockPolicyService.resolve(
                        player, shopId, itemId));
        data.purchased.merge(
                key, amount,
                (left, right) -> {
                    long sum = (long) left + right;
                    return (int) Math.min(
                            Integer.MAX_VALUE, sum);
                });
        data.setDirty();
    }

    /** Compatibility overload for existing purchase call sites. */
    public static void recordPurchase(
            UUID playerId,
            String shopId,
            String itemId,
            int amount
    ) {
        ServerPlayer player = onlinePlayer(playerId);
        if (player != null) {
            recordPurchase(
                    player, shopId, itemId, amount);
        }
    }

    /** Removes completed periods while preserving week/season/year/never ledgers. */
    public static void resetForNewDay() {
        var server =
                ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        ShopStockTracker data = server.overworld()
                .getDataStorage().computeIfAbsent(
                        new SavedData.Factory<>(
                                ShopStockTracker::new,
                                ShopStockTracker::load),
                        DATA_NAME);
        data.pruneExpired();
    }

    private void pruneExpired() {
        StardewTimeManager time = StardewTimeManager.get();
        boolean changed = false;
        Iterator<PurchaseKey> iterator =
                purchased.keySet().iterator();
        while (iterator.hasNext()) {
            PurchaseKey key = iterator.next();
            if (key.reset() != StardewShopStockRule.Reset.NEVER
                    && key.period() != period(
                            key.reset(), time)) {
                iterator.remove();
                changed = true;
            }
        }
        if (changed) {
            setDirty();
        }
    }

    private static PurchaseKey currentKey(
            ServerPlayer player,
            String shopId,
            String itemId,
            ShopStockPolicyService.Policy policy
    ) {
        String owner = policy.scope()
                == StardewShopStockRule.Scope.WORLD
                ? "world"
                : "player:" + player.getUUID();
        return new PurchaseKey(
                owner, shopId, itemId, policy.reset(),
                period(policy.reset(),
                        StardewTimeManager.get()));
    }

    private static int period(
            StardewShopStockRule.Reset reset,
            StardewTimeManager time
    ) {
        int absoluteDay = time.getAbsoluteDay();
        return switch (reset) {
            case DAY -> absoluteDay;
            case WEEK -> Math.floorDiv(
                    absoluteDay - 1, 7);
            case SEASON -> (time.getCurrentYear() - 1)
                    * 4 + time.getCurrentSeason();
            case YEAR -> time.getCurrentYear();
            case NEVER -> 0;
        };
    }

    private static ServerPlayer onlinePlayer(UUID playerId) {
        var server =
                ServerLifecycleHooks.getCurrentServer();
        return server == null
                ? null
                : server.getPlayerList().getPlayer(playerId);
    }

    public static ShopStockTracker load(
            CompoundTag tag,
            HolderLookup.Provider provider
    ) {
        ShopStockTracker data =
                new ShopStockTracker();
        ListTag entries = tag.getList(
                ENTRIES, Tag.TAG_COMPOUND);
        for (int index = 0;
             index < entries.size(); index++) {
            CompoundTag entry =
                    entries.getCompound(index);
            try {
                StardewShopStockRule.Reset reset =
                        StardewShopStockRule.Reset.valueOf(
                                entry.getString("Reset")
                                        .toUpperCase(Locale.ROOT));
                int count = entry.getInt("Count");
                if (count <= 0) {
                    continue;
                }
                PurchaseKey key = new PurchaseKey(
                        entry.getString("Owner"),
                        entry.getString("Shop"),
                        entry.getString("Item"),
                        reset,
                        entry.getInt("Period"));
                if (!key.owner().isBlank()
                        && !key.shopId().isBlank()
                        && !key.itemId().isBlank()) {
                    data.purchased.put(key, count);
                }
            } catch (IllegalArgumentException exception) {
                StardewCraft.LOGGER.warn(
                        "Skipped invalid persisted shop stock row",
                        exception);
            }
        }
        data.pruneExpired();
        return data;
    }

    @Override
    public @Nonnull CompoundTag save(
            @Nonnull CompoundTag tag,
            @Nonnull HolderLookup.Provider provider
    ) {
        ListTag entries = new ListTag();
        for (var row : purchased.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Owner", row.getKey().owner());
            entry.putString("Shop", row.getKey().shopId());
            entry.putString("Item", row.getKey().itemId());
            entry.putString(
                    "Reset",
                    row.getKey().reset().name());
            entry.putInt(
                    "Period", row.getKey().period());
            entry.putInt("Count", row.getValue());
            entries.add(entry);
        }
        tag.put(ENTRIES, entries);
        return tag;
    }

    private record PurchaseKey(
            String owner,
            String shopId,
            String itemId,
            StardewShopStockRule.Reset reset,
            int period
    ) {
    }
}
