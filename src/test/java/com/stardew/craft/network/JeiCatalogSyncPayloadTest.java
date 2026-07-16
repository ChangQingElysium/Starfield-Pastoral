package com.stardew.craft.network;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JeiCatalogSyncPayloadTest {
    @Test
    void entriesDefensivelyCopyMutableStacksAndSeasons() {
        ItemStack item = new ItemStack(Items.APPLE, 4);
        ItemStack geode = new ItemStack(Items.AMETHYST_CLUSTER, 2);
        ItemStack output = new ItemStack(Items.DIAMOND, 3);
        Set<Integer> seasons = new HashSet<>(Set.of(0, 2));
        ItemStack tradeItem = new ItemStack(Items.EMERALD, 5);

        JeiCatalogSyncPayload.ShopEntry shop = new JeiCatalogSyncPayload.ShopEntry(
                item, "example:shop", "Pierre", 50, 1, tradeItem, 5, 2,
                seasons, 2, 40, true, 4, 1, true, List.of("unknown"), false);
        JeiCatalogSyncPayload.GeodeEntry drop = new JeiCatalogSyncPayload.GeodeEntry(geode, output);
        item.setCount(1);
        geode.setCount(1);
        output.setCount(1);
        seasons.clear();
        tradeItem.setCount(1);

        assertEquals(4, shop.item().getCount());
        assertEquals("Pierre", shop.ownerNpcId());
        assertEquals(Set.of(0, 2), shop.seasons());
        assertEquals(5, shop.tradeItem().getCount());
        assertEquals(5, shop.tradeItemCount());
        assertEquals(2, shop.purchaseStack());
        assertEquals(40, shop.minMineLevel());
        assertEquals(4, shop.dayOfWeek());
        assertEquals(2, drop.geode().getCount());
        assertEquals(3, drop.output().getCount());

        shop.item().setCount(1);
        shop.tradeItem().setCount(1);
        drop.geode().setCount(1);
        drop.output().setCount(1);
        assertEquals(4, shop.item().getCount());
        assertEquals(5, shop.tradeItem().getCount());
        assertEquals(2, drop.geode().getCount());
        assertEquals(3, drop.output().getCount());
    }

    @Test
    void constructorRejectsCatalogsAboveTheEntryLimit() {
        JeiCatalogSyncPayload.ShopEntry entry = new JeiCatalogSyncPayload.ShopEntry(
                ItemStack.EMPTY, "", 0, 0, ItemStack.EMPTY, 0, 1,
                Set.of(), 1, 0, false, -1, 0, false, false);
        List<JeiCatalogSyncPayload.ShopEntry> oversized = Collections.nCopies(100_001, entry);

        assertThrows(IllegalArgumentException.class, () -> new JeiCatalogSyncPayload(oversized, List.of()));
    }
}
