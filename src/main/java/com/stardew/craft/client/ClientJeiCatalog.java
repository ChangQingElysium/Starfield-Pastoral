package com.stardew.craft.client;

import java.util.List;
import java.util.Set;
import net.minecraft.world.item.ItemStack;

/** Client-safe display data supplied by the server for optional JEI categories. */
public final class ClientJeiCatalog {
    private static volatile Snapshot snapshot = Snapshot.empty();

    private ClientJeiCatalog() {
    }

    public static boolean isSynced() {
        return snapshot.synced();
    }

    public static List<ShopEntry> shops() {
        return snapshot.shops();
    }

    public static List<GeodeEntry> geodes() {
        return snapshot.geodes();
    }

    public static List<FishPondEntry> fishPonds() {
        return snapshot.fishPonds();
    }

    public static Snapshot snapshot() {
        return snapshot;
    }

    public static void replace(
            List<ShopEntry> shopEntries,
            List<GeodeEntry> geodeEntries,
            List<FishPondEntry> fishPondEntries
    ) {
        snapshot = new Snapshot(
                true,
                shopEntries == null ? List.of() : shopEntries,
                geodeEntries == null ? List.of() : geodeEntries,
                fishPondEntries == null ? List.of() : fishPondEntries);
    }

    public static void replace(List<ShopEntry> shopEntries, List<GeodeEntry> geodeEntries) {
        replace(shopEntries, geodeEntries, List.of());
    }

    public static void clear() {
        snapshot = Snapshot.empty();
    }

    public record Snapshot(
            boolean synced,
            List<ShopEntry> shops,
            List<GeodeEntry> geodes,
            List<FishPondEntry> fishPonds
    ) {
        public Snapshot {
            shops = List.copyOf(shops);
            geodes = List.copyOf(geodes);
            fishPonds = List.copyOf(fishPonds);
        }

        private static Snapshot empty() {
            return new Snapshot(
                    false, List.of(), List.of(), List.of());
        }
    }

    public record ShopEntry(
            ItemStack item, String shopId, String ownerNpcId, int price, int stock,
            ItemStack tradeItem, int tradeItemCount, int purchaseStack,
            Set<Integer> seasons, int minYear, int minMineLevel,
            boolean mailRequired, int dayOfWeek, int dayOfMonthParity,
            boolean conditional, List<String> conditionTokens, boolean recipe) {
        public ShopEntry {
            item = item == null ? ItemStack.EMPTY : item.copy();
            tradeItem = tradeItem == null ? ItemStack.EMPTY : tradeItem.copy();
            shopId = shopId == null ? "" : shopId;
            ownerNpcId = ownerNpcId == null ? "" : ownerNpcId;
            seasons = seasons == null ? Set.of() : Set.copyOf(seasons);
            tradeItemCount = Math.max(0, tradeItemCount);
            purchaseStack = Math.max(1, purchaseStack);
            conditionTokens = conditionTokens == null ? List.of() : List.copyOf(conditionTokens);
        }

        public ShopEntry(ItemStack item, String shopId, int price, int stock,
                         ItemStack tradeItem, int tradeItemCount, int purchaseStack,
                         Set<Integer> seasons, int minYear, int minMineLevel,
                         boolean mailRequired, int dayOfWeek, int dayOfMonthParity,
                         boolean conditional, List<String> conditionTokens, boolean recipe) {
            this(item, shopId, "", price, stock, tradeItem, tradeItemCount, purchaseStack,
                    seasons, minYear, minMineLevel, mailRequired, dayOfWeek,
                    dayOfMonthParity, conditional, conditionTokens, recipe);
        }

        public ShopEntry(ItemStack item, String shopId, int price, int stock,
                         ItemStack tradeItem, int tradeItemCount, int purchaseStack,
                         Set<Integer> seasons, int minYear, int minMineLevel,
                         boolean mailRequired, int dayOfWeek, int dayOfMonthParity,
                         boolean conditional, boolean recipe) {
            this(item, shopId, "", price, stock, tradeItem, tradeItemCount, purchaseStack,
                    seasons, minYear, minMineLevel, mailRequired, dayOfWeek,
                    dayOfMonthParity, conditional,
                    conditional ? List.of("unknown") : List.of(), recipe);
        }

        @Override
        public ItemStack item() {
            return item.copy();
        }

        @Override
        public ItemStack tradeItem() {
            return tradeItem.copy();
        }
    }

    public record GeodeEntry(ItemStack geode, ItemStack output) {
        public GeodeEntry {
            geode = geode == null ? ItemStack.EMPTY : geode.copy();
            output = output == null ? ItemStack.EMPTY : output.copy();
        }

        @Override
        public ItemStack geode() {
            return geode.copy();
        }

        @Override
        public ItemStack output() {
            return output.copy();
        }
    }

    public record FishPondEntry(
            ItemStack fish,
            ItemStack output,
            int requiredPopulation,
            double outputChance,
            double dailyMinChance,
            double dailyMaxChance,
            int minCount,
            int maxCount,
            boolean bonusCountPossible
    ) {
        public FishPondEntry {
            fish = fish == null ? ItemStack.EMPTY : fish.copy();
            output = output == null ? ItemStack.EMPTY : output.copy();
            requiredPopulation = Math.max(0, requiredPopulation);
            outputChance = clampChance(outputChance);
            dailyMinChance = clampChance(dailyMinChance);
            dailyMaxChance = clampChance(dailyMaxChance);
            minCount = Math.max(1, minCount);
            maxCount = Math.max(minCount, maxCount);
        }

        @Override
        public ItemStack fish() {
            return fish.copy();
        }

        @Override
        public ItemStack output() {
            return output.copy();
        }

        private static double clampChance(double value) {
            return Math.max(0.0D, Math.min(1.0D, value));
        }
    }
}
