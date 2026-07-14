package com.stardew.craft.client;

import java.util.List;
import java.util.Set;
import net.minecraft.world.item.ItemStack;

/** Client-safe display data supplied by the server for optional JEI categories. */
public final class ClientJeiCatalog {
    private static volatile boolean synced;
    private static volatile List<ShopEntry> shops = List.of();
    private static volatile List<GeodeEntry> geodes = List.of();

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

    public static void replace(List<ShopEntry> shopEntries, List<GeodeEntry> geodeEntries) {
        shops = shopEntries == null ? List.of() : List.copyOf(shopEntries);
        geodes = geodeEntries == null ? List.of() : List.copyOf(geodeEntries);
        synced = true;
    }

    public static void clear() {
        shops = List.of();
        geodes = List.of();
        synced = false;
    }

    public record ShopEntry(
            ItemStack item, String shopId, int price, int stock,
            Set<Integer> seasons, int minYear, boolean recipe) {
        public ShopEntry {
            item = item == null ? ItemStack.EMPTY : item.copy();
            shopId = shopId == null ? "" : shopId;
            seasons = seasons == null ? Set.of() : Set.copyOf(seasons);
        }

        @Override
        public ItemStack item() {
            return item.copy();
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
}
