package com.stardew.craft.shop;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopPendingPickupDataTest {

    @Test
    void claimCannotExceedTheServerEscrow() {
        ShopPendingPickupData data = new ShopPendingPickupData();
        UUID playerId = UUID.randomUUID();
        data.add(playerId, new ItemStack(Items.APPLE, 3));

        assertEquals(2, totalCount(data.take(
            playerId, BuiltInRegistries.ITEM.getKey(Items.APPLE), 2)));
        assertEquals(1, totalCount(data.take(
            playerId, BuiltInRegistries.ITEM.getKey(Items.APPLE), 99)));
        assertTrue(data.take(
            playerId, BuiltInRegistries.ITEM.getKey(Items.APPLE), 1).isEmpty());
    }

    @Test
    void recoveryDrainsPendingItemsExactlyOnce() {
        ShopPendingPickupData data = new ShopPendingPickupData();
        UUID playerId = UUID.randomUUID();
        data.add(playerId, new ItemStack(Items.APPLE, 2));
        data.add(playerId, new ItemStack(Items.CARROT, 4));

        assertEquals(6, totalCount(data.takeAll(playerId)));
        assertTrue(data.takeAll(playerId).isEmpty());
    }

    private static int totalCount(List<ItemStack> stacks) {
        return stacks.stream().mapToInt(ItemStack::getCount).sum();
    }
}
