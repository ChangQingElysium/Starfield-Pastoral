package com.stardew.craft.client;

import java.util.List;
import java.util.Set;
import net.minecraft.world.item.ItemStack;

/** Client-safe display data supplied by the server for optional JEI categories. */
public final class ClientJeiCatalog {
    private static volatile boolean synced;
    private static volatile List<ShopEntry> shops = List.of();
    private static volatile List<GeodeEntry> geodes = List.of();
    private static volatile List<FishPondEntry> fishPonds = List.of();

    private ClientJeiCatalog() {
    }

    public static boolean isSynced() {
        return synced;
    }

    public static List<ShopEntry> shops() {
        return shops;
    }

    public static List<GeodeEntry> geodes() {
        return geodes;
    }

    public static List<FishPondEntry> fishPonds() {
        return fishPonds;
    }

    public static void replace(
            List<ShopEntry> shopEntries,
            List<GeodeEntry> geodeEntries,
            List<FishPondEntry> fishPondEntries
    ) {
        shops = shopEntries == null ? List.of() : List.copyOf(shopEntries);
        geodes = geodeEntries == null ? List.of() : List.copyOf(geodeEntries);
        fishPonds = fishPondEntries == null ? List.of() : List.copyOf(fishPondEntries);
        synced = true;
    }

    public static void replace(List<ShopEntry> shopEntries, List<GeodeEntry> geodeEntries) {
        replace(shopEntries, geodeEntries, List.of());
    }

    public static void clear() {
        shops = List.of();
        geodes = List.of();
        fishPonds = List.of();
        synced = false;
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
