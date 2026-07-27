package com.stardew.craft.api.v1.shop;

import com.stardew.craft.api.v1.internal.shop.StardewShopInventoryRegistry;
import com.stardew.craft.api.v1.requirement.StardewRequirementReport;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Read-only runtime inventory catalog for standard shops. */
public final class StardewShopInventories {
    private StardewShopInventories() {
    }

    public static List<StardewShopInventorySnapshot> list(
            ServerPlayer player,
            String shopId
    ) {
        return StardewShopInventoryRegistry.list(
                Objects.requireNonNull(player, "player"),
                Objects.requireNonNull(shopId, "shopId"));
    }

    /**
     * Lists identifiable declared/provider rows even when their current date,
     * condition, progress or ownership requirements prevent them from being
     * listed. Today's generated runtime rows are included too.
     */
    public static List<StardewShopInventorySnapshot> candidates(
            ServerPlayer player,
            String shopId
    ) {
        return StardewShopInventoryRegistry.candidates(
                Objects.requireNonNull(player, "player"),
                Objects.requireNonNull(shopId, "shopId"));
    }

    public static Optional<StardewShopInventorySnapshot> inspect(
            ServerPlayer player,
            StardewShopRowKey key
    ) {
        return StardewShopInventoryRegistry.inspect(
                Objects.requireNonNull(player, "player"),
                Objects.requireNonNull(key, "key"));
    }

    /**
     * Explains whether an identifiable candidate is currently listed and has
     * enough remaining stock. This does not reserve stock or authorize a
     * purchase. A key absent from the candidate directory reports only the
     * entry-listed blocker.
     */
    public static StardewRequirementReport requirements(
            ServerPlayer player,
            StardewShopRowKey key,
            int quantity
    ) {
        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "quantity must be positive");
        }
        return StardewShopInventoryRegistry.requirements(
                Objects.requireNonNull(player, "player"),
                Objects.requireNonNull(key, "key"),
                quantity);
    }
}
